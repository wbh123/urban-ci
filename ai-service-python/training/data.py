"""裂缝语义分割数据集、配对、分组划分和同步增强。"""
from __future__ import annotations
import csv, hashlib, json, random, re
from dataclasses import dataclass
from pathlib import Path
import numpy as np
import torch
from PIL import Image, ImageEnhance, ImageOps
from torch.utils.data import Dataset
from torchvision.transforms import functional as TF
from torchvision.transforms.functional import InterpolationMode
from training.split import split_group_names

IMAGE_SUFFIXES={".jpg",".jpeg",".png",".bmp",".tif",".tiff",".webp"}
@dataclass(frozen=True)
class SamplePair:
    image:Path; mask:Path; group:str

def discover_pairs(images_dir:Path,masks_dir:Path,*,group_regex:str|None=None)->list[SamplePair]:
    images_dir=images_dir.expanduser().resolve(); masks_dir=masks_dir.expanduser().resolve()
    if not images_dir.is_dir() or not masks_dir.is_dir(): raise FileNotFoundError("images_dir 和 masks_dir 必须是存在的目录")
    mask_index={}
    for path in sorted(masks_dir.rglob("*")):
        if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES:
            key=_canonical_stem(path.stem)
            if key in mask_index: raise ValueError(f"掩膜主干重复，无法确定配对：{key}")
            mask_index[key]=path
    pattern=re.compile(group_regex) if group_regex else None; pairs=[]; missing=[]
    for image in sorted(images_dir.rglob("*")):
        if not image.is_file() or image.suffix.lower() not in IMAGE_SUFFIXES: continue
        mask=mask_index.get(_canonical_stem(image.stem))
        if mask is None: missing.append(str(image)); continue
        pairs.append(SamplePair(image=image,mask=mask,group=_group_name(image.stem,pattern)))
    if not pairs: raise ValueError("没有找到任何图片—掩膜配对")
    if missing: raise ValueError(f"有 {len(missing)} 张图片缺少掩膜，前 10 项：\n"+"\n".join(missing[:10]))
    return pairs

def split_pairs(pairs:list[SamplePair],*,seed:int,train_ratio:float=0.70,val_ratio:float=0.15)->dict[str,list[SamplePair]]:
    groups={}
    for pair in pairs: groups.setdefault(pair.group,[]).append(pair)
    train_groups,val_groups,test_groups=split_group_names(
        list(groups),seed=seed,train_ratio=train_ratio,val_ratio=val_ratio
    )
    return {
        "train":[p for p in pairs if p.group in train_groups],
        "val":[p for p in pairs if p.group in val_groups],
        "test":[p for p in pairs if p.group in test_groups],
    }

def write_split_manifest(output_dir:Path,splits:dict[str,list[SamplePair]],*,dataset_name:str,source:str,license_name:str,seed:int)->Path:
    output_dir=output_dir.expanduser().resolve(); output_dir.mkdir(parents=True,exist_ok=True); counts={}
    for name,pairs in splits.items():
        if not pairs: raise ValueError(f"{name} 划分不能为空")
        with (output_dir/f"{name}.tsv").open("w",encoding="utf-8",newline="") as file:
            writer=csv.writer(file,delimiter="\t"); writer.writerow(["image","mask","group"])
            for pair in pairs: writer.writerow([str(pair.image),str(pair.mask),pair.group])
        counts[name]=len(pairs)
    metadata={"schemaVersion":1,"datasetName":dataset_name,"source":source,"license":license_name,"seed":seed,
              "counts":counts,"pairDigestSha256":_pairs_digest(splits)}
    path=output_dir/"dataset.json"; path.write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+"\n",encoding="utf-8"); return path

def read_split_manifest(path:Path)->list[SamplePair]:
    pairs=[]
    with path.expanduser().resolve().open("r",encoding="utf-8",newline="") as file:
        reader=csv.DictReader(file,delimiter="\t")
        if reader.fieldnames is None or not {"image","mask","group"}.issubset(reader.fieldnames): raise ValueError("划分清单必须包含 image、mask、group 三列")
        for row in reader:
            image=Path(row["image"]).expanduser().resolve(); mask=Path(row["mask"]).expanduser().resolve()
            if not image.is_file() or not mask.is_file(): raise FileNotFoundError(f"样本文件不存在：{image} / {mask}")
            pairs.append(SamplePair(image=image,mask=mask,group=row["group"]))
    if not pairs: raise ValueError("划分清单为空")
    return pairs

class CrackSegmentationDataset(Dataset[tuple[torch.Tensor,torch.Tensor]]):
    def __init__(self,pairs:list[SamplePair],*,image_size:int,training:bool,mask_polarity:str="auto"):
        if image_size<64: raise ValueError("image_size 必须大于等于 64")
        if mask_polarity not in {"auto","white-crack","black-crack"}: raise ValueError("mask_polarity 不合法")
        self.pairs=list(pairs); self.image_size=image_size; self.training=training; self.mask_polarity=mask_polarity
    def __len__(self): return len(self.pairs)
    def __getitem__(self,index):
        pair=self.pairs[index]; image=Image.open(pair.image).convert("RGB"); mask=self._normalize_mask(Image.open(pair.mask).convert("L"))
        if self.training: image,mask=self._augment(image,mask)
        else:
            image=image.resize((self.image_size,self.image_size),Image.Resampling.BILINEAR)
            mask=mask.resize((self.image_size,self.image_size),Image.Resampling.NEAREST)
        image_tensor=TF.normalize(TF.pil_to_tensor(image).float()/255.0,mean=[0.485,0.456,0.406],std=[0.229,0.224,0.225])
        return image_tensor,(TF.pil_to_tensor(mask).float()>=127.5).float()
    def _normalize_mask(self,mask):
        binary=np.asarray(mask,dtype=np.uint8)>=127
        if self.mask_polarity=="black-crack" or (self.mask_polarity=="auto" and float(binary.mean())>0.5): binary=~binary
        return Image.fromarray(binary.astype(np.uint8)*255)
    def _augment(self,image,mask):
        if random.random()<0.5: image=ImageOps.mirror(image); mask=ImageOps.mirror(mask)
        if random.random()<0.2: image=ImageOps.flip(image); mask=ImageOps.flip(mask)
        angle=random.uniform(-12.0,12.0)
        image=TF.rotate(image,angle,interpolation=InterpolationMode.BILINEAR,fill=0)
        mask=TF.rotate(mask,angle,interpolation=InterpolationMode.NEAREST,fill=0)
        scale=random.uniform(0.85,1.25); width=max(self.image_size,round(image.width*scale)); height=max(self.image_size,round(image.height*scale))
        image=image.resize((width,height),Image.Resampling.BILINEAR); mask=mask.resize((width,height),Image.Resampling.NEAREST)
        left=random.randint(0,width-self.image_size) if width>self.image_size else 0; top=random.randint(0,height-self.image_size) if height>self.image_size else 0
        box=(left,top,left+self.image_size,top+self.image_size); image=image.crop(box); mask=mask.crop(box)
        if random.random()<0.5:
            image=ImageEnhance.Brightness(image).enhance(random.uniform(0.85,1.15)); image=ImageEnhance.Contrast(image).enhance(random.uniform(0.85,1.15))
        return image,mask

def _canonical_stem(stem:str)->str:
    normalized=stem.lower()
    for suffix in ("_mask","-mask","_label","-label","_gt","-gt"):
        if normalized.endswith(suffix): return normalized[:-len(suffix)]
    return normalized

def _group_name(stem:str,pattern):
    if pattern is not None:
        match=pattern.search(stem)
        if match is None: raise ValueError(f"文件名不匹配 group_regex：{stem}")
        return match.group(1) if match.groups() else match.group(0)
    return stem.split("__",maxsplit=1)[0]

def _pairs_digest(splits)->str:
    digest=hashlib.sha256()
    for name in sorted(splits):
        for pair in sorted(splits[name],key=lambda item:str(item.image)):
            for value in (name,str(pair.image),str(pair.mask),pair.group): digest.update(value.encode())
    return digest.hexdigest()
