"""Florence-2 Large 独立文本到区域定位适配器。"""
from __future__ import annotations

import gc
from pathlib import Path
from typing import Callable

from PIL import Image, ImageOps

from ..accuracy import ALLOWED_CLASS_CODES, LocatorCandidate
from ..errors import ModelUnavailableError

TASK_PROMPT = "<CAPTION_TO_PHRASE_GROUNDING>"
DEFAULT_FLORENCE_SCORE = 0.5
CLASS_PHRASES = {
    "CRACK": "crack on concrete or wall",
    "SPALLING": "concrete spalling",
    "EXPOSED_REBAR": "exposed reinforcing steel bar",
    "CORROSION": "corrosion or rust on building surface",
    "WATER_STAIN": "water stain or damp stain on wall",
    "SURFACE_DAMAGE": "damaged concrete or wall surface",
}


def _load_with_fallback(loaders: list[tuple[str, Callable[[], object]]]) -> tuple[object, str]:
    """按官方/兼容模型类顺序加载，并保留每个失败原因。"""

    errors: list[str] = []
    for name, loader in loaders:
        try:
            return loader(), name
        except Exception as ex:
            errors.append(f"{name}: {type(ex).__name__}: {ex}")
    raise ModelUnavailableError("Florence-2 模型类加载全部失败：" + " | ".join(errors))


def _load_processor_with_fallback(model_path: Path):
    """现代 Transformers 优先使用内置 Florence2Processor，避免执行旧 remote config。"""

    try:
        from transformers import Florence2Processor
    except ImportError:
        Florence2Processor = None

    errors: list[str] = []
    if Florence2Processor is not None:
        try:
            return (
                Florence2Processor.from_pretrained(
                    str(model_path),
                    local_files_only=True,
                ),
                "Florence2Processor(native)",
            )
        except Exception as ex:
            errors.append(
                f"Florence2Processor(native): {type(ex).__name__}: {ex}"
            )

    # 仅用于旧 Transformers：如果没有原生 Florence-2，再尝试仓库 custom code。
    try:
        from transformers import AutoProcessor

        return (
            AutoProcessor.from_pretrained(
                str(model_path),
                trust_remote_code=True,
                local_files_only=True,
            ),
            "AutoProcessor(remote-code)",
        )
    except Exception as ex:
        errors.append(f"AutoProcessor(remote-code): {type(ex).__name__}: {ex}")

    raise ModelUnavailableError(
        "Florence-2 Processor 加载全部失败：" + " | ".join(errors)
    )


class _TransformersFlorenceBackend:
    def __init__(self, model_path: Path, device: str = "cuda") -> None:
        try:
            import torch
            from transformers import AutoModelForCausalLM, AutoModelForMultimodalLM
            try:
                from transformers import Florence2ForConditionalGeneration
            except ImportError:
                Florence2ForConditionalGeneration = None
        except ImportError as ex:
            raise ModelUnavailableError("未安装 Florence-2 所需 transformers/torch 依赖") from ex
        if device.startswith("cuda") and not torch.cuda.is_available():
            raise ModelUnavailableError("Florence-2 ACCURACY 实验需要 CUDA")

        self._torch = torch
        self._device = device
        self._dtype = torch.float16 if device.startswith("cuda") else torch.float32

        try:
            self._processor, self._processor_loader_name = _load_processor_with_fallback(
                model_path
            )
        except ModelUnavailableError as ex:
            raise ModelUnavailableError(
                f"Florence-2 Processor 加载失败：{model_path}；{ex}"
            ) from ex

        loaders: list[tuple[str, Callable[[], object]]] = []

        # 新版 Transformers 已原生集成 Florence-2。优先使用内置模型类，避免
        # microsoft/Florence-2-large 仓库中的旧 configuration_florence2.py 与
        # Transformers v5+ 的 PretrainedConfig 行为冲突。
        if Florence2ForConditionalGeneration is not None:
            loaders.append(
                (
                    "Florence2ForConditionalGeneration(native)",
                    lambda: Florence2ForConditionalGeneration.from_pretrained(
                        str(model_path),
                        local_files_only=True,
                        torch_dtype=self._dtype,
                    ),
                )
            )

        remote_common_kwargs = {
            "trust_remote_code": True,
            "local_files_only": True,
            "torch_dtype": self._dtype,
        }
        loaders.extend(
            [
                (
                    "AutoModelForMultimodalLM(remote-code)",
                    lambda: AutoModelForMultimodalLM.from_pretrained(
                        str(model_path), **remote_common_kwargs
                    ),
                ),
                (
                    "AutoModelForCausalLM(remote-code)",
                    lambda: AutoModelForCausalLM.from_pretrained(
                        str(model_path), **remote_common_kwargs
                    ),
                ),
            ]
        )

        try:
            model, loader_name = _load_with_fallback(loaders)
            self._loader_name = loader_name
        except ModelUnavailableError as ex:
            raise ModelUnavailableError(
                f"Florence-2 本地权重加载失败：{model_path}；{ex}"
            ) from ex

        try:
            self._model = model.to(device).eval()
        except Exception as ex:
            self._model = None
            raise ModelUnavailableError(
                f"Florence-2 权重已读取但迁移到 {device} 失败；"
                f"processor={self._processor_loader_name}；"
                f"loader={self._loader_name}；{type(ex).__name__}: {ex}"
            ) from ex

    def locate_phrase(self, image: Image.Image, phrase: str):
        prepared = ImageOps.exif_transpose(image).convert("RGB")
        prompt = TASK_PROMPT + phrase
        try:
            inputs = self._processor(
                text=prompt,
                images=prepared,
                return_tensors="pt",
            )
            # 不对 input_ids 施加 float16；只把浮点张量转换为模型 dtype。
            inputs = {
                key: (
                    value.to(self._device, dtype=self._dtype)
                    if getattr(value, "is_floating_point", lambda: False)()
                    else value.to(self._device)
                )
                for key, value in inputs.items()
            }
            with self._torch.inference_mode():
                generated_ids = self._model.generate(
                    input_ids=inputs["input_ids"],
                    pixel_values=inputs["pixel_values"],
                    max_new_tokens=1024,
                    num_beams=3,
                    do_sample=False,
                )
            generated_text = self._processor.batch_decode(
                generated_ids,
                skip_special_tokens=False,
            )[0]
            parsed = self._processor.post_process_generation(
                generated_text,
                task=TASK_PROMPT,
                image_size=(prepared.width, prepared.height),
            )
            body = parsed.get(TASK_PROMPT, {}) if isinstance(parsed, dict) else {}
            boxes = body.get("bboxes", []) if isinstance(body, dict) else []
            if not isinstance(boxes, list):
                raise ValueError("bboxes is not a list")
            return [{"bbox": box} for box in boxes]
        except Exception as ex:
            raise ModelUnavailableError(
                f"Florence-2 phrase grounding 推理失败；"
                f"processor={getattr(self, '_processor_loader_name', 'unknown')}；"
                f"loader={getattr(self, '_loader_name', 'unknown')}；"
                f"{type(ex).__name__}: {ex}"
            ) from ex

    def close(self) -> None:
        model = getattr(self, "_model", None)
        processor = getattr(self, "_processor", None)
        self._model = None
        self._processor = None
        del model, processor
        gc.collect()
        torch = getattr(self, "_torch", None)
        if torch is not None and torch.cuda.is_available():
            torch.cuda.empty_cache()


class Florence2Locator:
    """把 Florence-2 phrase grounding 转换为统一 LocatorCandidate。"""

    def __init__(self, model_path: Path, device: str = "cuda", backend=None) -> None:
        self.model_path = Path(model_path)
        self._backend = backend or _TransformersFlorenceBackend(self.model_path, device=device)

    def locate(self, image: Image.Image, class_codes: set[str]) -> list[LocatorCandidate]:
        requested = {str(code).upper() for code in class_codes}
        unknown = requested - ALLOWED_CLASS_CODES
        if unknown:
            raise ModelUnavailableError(f"Florence-2 不支持的类别：{sorted(unknown)}")
        width, height = image.size
        output: list[LocatorCandidate] = []
        for code in sorted(requested):
            raw = self._backend.locate_phrase(image, CLASS_PHRASES[code])
            if raw is None:
                continue
            if not isinstance(raw, list):
                raise ModelUnavailableError("Florence-2 结果格式非法：应为候选数组")
            for item in raw:
                if not isinstance(item, dict) or "bbox" not in item:
                    raise ModelUnavailableError("Florence-2 结果格式非法：缺少 bbox")
                box = item["bbox"]
                if not isinstance(box, (list, tuple)) or len(box) != 4:
                    raise ModelUnavailableError("Florence-2 结果格式非法：bbox 必须为四坐标")
                try:
                    x1, y1, x2, y2 = [float(v) for v in box]
                except (TypeError, ValueError) as ex:
                    raise ModelUnavailableError("Florence-2 结果格式非法：bbox 非数值") from ex
                x1 = max(0.0, min(float(width), x1))
                y1 = max(0.0, min(float(height), y1))
                x2 = max(0.0, min(float(width), x2))
                y2 = max(0.0, min(float(height), y2))
                if x2 <= x1 or y2 <= y1:
                    continue
                output.append(
                    LocatorCandidate(
                        [x1, y1, x2, y2],
                        DEFAULT_FLORENCE_SCORE,
                        code,
                        "FLORENCE2",
                    )
                )
        return output

    def close(self) -> None:
        backend = self._backend
        self._backend = None
        if backend is not None and hasattr(backend, "close"):
            backend.close()
