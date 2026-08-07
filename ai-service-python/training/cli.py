"""裂缝语义分割数据准备、训练和 ONNX 导出命令。"""
from __future__ import annotations
import argparse, json, os, random, time
from dataclasses import asdict
from pathlib import Path
from typing import Any
import numpy as np
import torch
from torch.utils.data import DataLoader
from modeling import ImprovedUNet, UNetConfig, normalize_state_dict
from training.data import CrackSegmentationDataset, discover_pairs, read_split_manifest, split_pairs, write_split_manifest
from training.metrics import SegmentationTotals, dice_bce_loss

def build_parser()->argparse.ArgumentParser:
    parser=argparse.ArgumentParser(description="UrbanSafe 裂缝分割训练工具"); subs=parser.add_subparsers(dest="command",required=True)
    prepare=subs.add_parser("prepare",help="配对并按 group 划分数据")
    for name in ("images","masks","output"): prepare.add_argument(f"--{name}",type=Path,required=True)
    prepare.add_argument("--dataset-name",required=True); prepare.add_argument("--source",required=True); prepare.add_argument("--license",dest="license_name",required=True)
    prepare.add_argument("--group-regex"); prepare.add_argument("--seed",type=int,default=20260726)
    prepare.add_argument("--train-ratio",type=float,default=0.70); prepare.add_argument("--val-ratio",type=float,default=0.15)
    train=subs.add_parser("train",help="训练 U-Net 裂缝分割模型")
    train.add_argument("--splits",type=Path,required=True); train.add_argument("--output",type=Path,required=True)
    train.add_argument("--image-size",type=int,default=640); train.add_argument("--epochs",type=int,default=80); train.add_argument("--batch-size",type=int,default=4)
    train.add_argument("--workers",type=int,default=min(8,os.cpu_count() or 1)); train.add_argument("--learning-rate",type=float,default=1e-4)
    train.add_argument("--weight-decay",type=float,default=1e-4); train.add_argument("--start-filters",type=int,default=32); train.add_argument("--depth",type=int,default=4)
    train.add_argument("--patience",type=int,default=12); train.add_argument("--threshold",type=float,default=0.5)
    train.add_argument("--mask-polarity",choices=["auto","white-crack","black-crack"],default="auto")
    train.add_argument("--seed",type=int,default=20260726); train.add_argument("--device",choices=["auto","cpu","cuda"],default="auto"); train.add_argument("--no-amp",action="store_true")
    export=subs.add_parser("export",help="把训练检查点导出为固定契约 ONNX")
    export.add_argument("--checkpoint",type=Path,required=True); export.add_argument("--output",type=Path,required=True); export.add_argument("--opset",type=int,default=17)
    return parser

def main()->None:
    args=build_parser().parse_args(); {"prepare":_prepare,"train":_train,"export":_export}[args.command](args)

def _prepare(args)->None:
    pairs=discover_pairs(args.images,args.masks,group_regex=args.group_regex)
    splits=split_pairs(pairs,seed=args.seed,train_ratio=args.train_ratio,val_ratio=args.val_ratio)
    path=write_split_manifest(args.output,splits,dataset_name=args.dataset_name,source=args.source,license_name=args.license_name,seed=args.seed)
    print(f"已生成数据清单：{path}"); print({name:len(items) for name,items in splits.items()})

def _train(args)->None:
    _seed_everything(args.seed); device=_select_device(args.device); use_amp=device.type=="cuda" and not args.no_amp
    output_dir=args.output.expanduser().resolve(); output_dir.mkdir(parents=True,exist_ok=True)
    train_pairs=read_split_manifest(args.splits/"train.tsv"); val_pairs=read_split_manifest(args.splits/"val.tsv"); test_pairs=read_split_manifest(args.splits/"test.tsv")
    train_data=CrackSegmentationDataset(train_pairs,image_size=args.image_size,training=True,mask_polarity=args.mask_polarity)
    val_data=CrackSegmentationDataset(val_pairs,image_size=args.image_size,training=False,mask_polarity=args.mask_polarity)
    test_data=CrackSegmentationDataset(test_pairs,image_size=args.image_size,training=False,mask_polarity=args.mask_polarity)
    kwargs={"batch_size":args.batch_size,"num_workers":args.workers,"pin_memory":device.type=="cuda","persistent_workers":args.workers>0}
    train_loader=DataLoader(train_data,shuffle=True,drop_last=False,**kwargs); val_loader=DataLoader(val_data,shuffle=False,**kwargs); test_loader=DataLoader(test_data,shuffle=False,**kwargs)
    config=UNetConfig(depth=args.depth,start_filters=args.start_filters); model=ImprovedUNet(config).to(device)
    optimizer=torch.optim.AdamW(model.parameters(),lr=args.learning_rate,weight_decay=args.weight_decay)
    scheduler=torch.optim.lr_scheduler.CosineAnnealingLR(optimizer,T_max=max(1,args.epochs),eta_min=args.learning_rate*0.01)
    scaler=torch.amp.GradScaler("cuda",enabled=use_amp); best_score=-1.0; best_epoch=0; stale=0; history=[]; started=time.time()
    for epoch in range(1,args.epochs+1):
        model.train(); train_loss=0.0; sample_count=0
        for images,masks in train_loader:
            images=images.to(device,non_blocking=True); masks=masks.to(device,non_blocking=True); optimizer.zero_grad(set_to_none=True)
            with torch.amp.autocast(device_type=device.type,enabled=use_amp): logits=model(images); loss=dice_bce_loss(logits,masks)
            scaler.scale(loss).backward(); scaler.unscale_(optimizer); torch.nn.utils.clip_grad_norm_(model.parameters(),5.0)
            scaler.step(optimizer); scaler.update(); train_loss+=float(loss.item())*images.shape[0]; sample_count+=images.shape[0]
        scheduler.step(); val_metrics=_evaluate(model,val_loader,device=device,threshold=args.threshold,use_amp=use_amp)
        score=(val_metrics["pixelF1"]+val_metrics["iou"])/2.0
        record={"epoch":epoch,"trainLoss":train_loss/max(1,sample_count),"learningRate":optimizer.param_groups[0]["lr"],**val_metrics}
        history.append(record); print(json.dumps(record,ensure_ascii=False))
        if score>best_score:
            best_score=score; best_epoch=epoch; stale=0
            torch.save({"schemaVersion":1,"modelStateDict":model.state_dict(),"modelConfig":asdict(config),"imageSize":args.image_size,
                "threshold":args.threshold,"normalization":{"mean":[0.485,0.456,0.406],"std":[0.229,0.224,0.225]},
                "validationMetrics":val_metrics,"epoch":epoch},output_dir/"best.pt")
        else: stale+=1
        (output_dir/"history.json").write_text(json.dumps(history,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
        if stale>=args.patience: print(f"连续 {stale} 轮无提升，提前停止。"); break
    checkpoint=torch.load(output_dir/"best.pt",map_location=device,weights_only=False); model.load_state_dict(normalize_state_dict(checkpoint))
    test_metrics=_evaluate(model,test_loader,device=device,threshold=args.threshold,use_amp=use_amp)
    result={"bestEpoch":best_epoch,"durationSeconds":round(time.time()-started,2),"device":str(device),
        "validationMetrics":checkpoint["validationMetrics"],"testMetrics":test_metrics,"splitMetadata":_read_json_if_exists(args.splits/"dataset.json")}
    (output_dir/"evaluation.json").write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(result,ensure_ascii=False,indent=2))

@torch.no_grad()
def _evaluate(model,loader,*,device,threshold,use_amp)->dict[str,float]:
    model.eval(); totals=SegmentationTotals(); losses=[]
    for images,masks in loader:
        images=images.to(device,non_blocking=True); masks=masks.to(device,non_blocking=True)
        with torch.amp.autocast(device_type=device.type,enabled=use_amp): logits=model(images); loss=dice_bce_loss(logits,masks)
        totals.update(torch.sigmoid(logits),masks,threshold=threshold); losses.append(float(loss.item()))
    return {"loss":sum(losses)/max(1,len(losses)),**totals.compute()}

def _export(args)->None:
    checkpoint=torch.load(args.checkpoint.expanduser().resolve(),map_location="cpu",weights_only=False)
    config_payload=checkpoint.get("modelConfig")
    if not isinstance(config_payload,dict): raise ValueError("检查点缺少 modelConfig")
    model=ImprovedUNet(UNetConfig(**config_payload)); model.load_state_dict(normalize_state_dict(checkpoint),strict=True); model.eval()
    image_size=int(checkpoint.get("imageSize",640)); output=args.output.expanduser().resolve(); output.parent.mkdir(parents=True,exist_ok=True)
    dummy=torch.zeros((1,3,image_size,image_size),dtype=torch.float32)
    with torch.no_grad(): torch.onnx.export(model,dummy,output,input_names=["images"],output_names=["mask_logits"],opset_version=args.opset,do_constant_folding=True,dynamic_axes=None,dynamo=False)
    _verify_export(model,dummy,output); print(f"ONNX 已导出并验证：{output}")

def _verify_export(model,dummy,output)->None:
    try: import onnxruntime as ort
    except ImportError as ex: raise RuntimeError("导出验证需要安装 onnxruntime") from ex
    session=ort.InferenceSession(str(output),providers=["CPUExecutionProvider"])
    if [i.name for i in session.get_inputs()] != ["images"] or [o.name for o in session.get_outputs()] != ["mask_logits"]: raise RuntimeError("ONNX 契约不匹配")
    with torch.no_grad(): torch_output=model(dummy).cpu().numpy()
    ort_output=session.run(["mask_logits"],{"images":dummy.numpy()})[0]
    if torch_output.shape!=ort_output.shape: raise RuntimeError("PyTorch 与 ONNX 输出形状不一致")
    maximum_error=float(np.max(np.abs(torch_output-ort_output)))
    if maximum_error>1e-3: raise RuntimeError(f"PyTorch 与 ONNX 最大误差过大：{maximum_error}")

def _select_device(value)->torch.device:
    if value=="cpu": return torch.device("cpu")
    if value=="cuda":
        if not torch.cuda.is_available(): raise RuntimeError("请求使用 CUDA，但当前环境不可用")
        return torch.device("cuda")
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")

def _seed_everything(seed)->None:
    random.seed(seed); np.random.seed(seed); torch.manual_seed(seed)
    if torch.cuda.is_available(): torch.cuda.manual_seed_all(seed)
    torch.backends.cudnn.benchmark=True

def _read_json_if_exists(path:Path)->dict[str,Any]|None:
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else None

if __name__=="__main__": main()
