"""Qwen3-VL 图片级建筑表观病害语义门控。"""
from __future__ import annotations

import gc
import json
import os
from pathlib import Path
from typing import Any

from PIL import Image, ImageOps

from ..accuracy import ALLOWED_CLASS_CODES, SemanticClassDecision, SemanticGateResult
from ..errors import ModelUnavailableError

PRESENCE_ABSENT_THRESHOLD = 0.45
PRESENCE_POSITIVE_THRESHOLD = 0.70
DEFAULT_QWEN_MAX_NEW_TOKENS = 128
MIN_QWEN_MAX_NEW_TOKENS = 32
MAX_QWEN_MAX_NEW_TOKENS = 256

_GATE_PROMPT = """你是建筑表观病害图片分类器。请独立判断下列六类病害在图片中的可见程度：
CRACK, SPALLING, EXPOSED_REBAR, CORROSION, WATER_STAIN, SURFACE_DAMAGE。
CRACK=裂缝；SPALLING=混凝土/墙面剥落；EXPOSED_REBAR=露筋；CORROSION=锈蚀；WATER_STAIN=水渍或受潮痕迹；SURFACE_DAMAGE=其他明显表面损伤。

对每个类别只输出一个 presenceScore，范围 0 到 1：
- 0 表示没有任何可见证据；
- 1 表示有非常清晰、直接的可见证据；
- 中间值表示证据强弱，不要把不确定性强行判成存在。

判断要求：
- 不要默认 CRACK 或任何其他类别存在；
- 正常墙面、没有病害的图片允许六类全部低分；
- 一张图片允许多类同时存在；
- 墙体接缝、瓷砖缝、电线、阴影、树枝投影、装饰纹理不能仅因呈线状就判为 CRACK；
- WATER_STAIN 需要受潮、水迹、渗漏、明显水渍等视觉证据；
- SPALLING/EXPOSED_REBAR/CORROSION 需要与各自病害含义相符的直接视觉证据；
- 不要根据本提示词里的类别顺序猜答案，只依据当前图片。

只返回一个标准 JSON 对象，不要解释，不要 Markdown。对象只能包含 scores 字段；scores 必须完整包含上述六个固定类别，每个值必须是 0 到 1 的数字。"""

_GATE_RETRY_PROMPT = _GATE_PROMPT + """

上一轮输出格式或字段不符合要求。必须只输出一个 JSON 对象：
- 第一个字符必须是 {
- 最后一个字符必须是 }
- 顶层只能有 scores
- scores 必须完整包含六个固定类别，一个不能省略
- 每个类别的值只能是 0 到 1 的 JSON 数字
- 不要添加 present、confidence、uncertain 或任何解释字段
- 不要使用 ```json 代码围栏
- 不要添加解释、前缀、后缀或思考过程
"""


class _JsonExtractionError(ModelUnavailableError):
    pass


def _raw_preview(raw: str, limit: int = 320) -> str:
    compact = " ".join(str(raw).replace("\x00", "").split())
    if len(compact) > limit:
        compact = compact[:limit] + "..."
    return compact


def _extract_json_payload(raw: str) -> dict[str, Any]:
    """提取首个包含 scores 的合法 JSON 对象。"""
    text = str(raw or "").strip()
    decoder = json.JSONDecoder()
    for index, char in enumerate(text):
        if char != "{":
            continue
        try:
            payload, _end = decoder.raw_decode(text[index:])
        except json.JSONDecodeError:
            continue
        if isinstance(payload, dict) and isinstance(payload.get("scores"), dict):
            return payload
    raise _JsonExtractionError(
        f"Qwen3-VL 输出中未找到合法 JSON scores 对象；raw={_raw_preview(text)!r}"
    )


def _decision_from_presence_score(code: str, score: float) -> SemanticClassDecision:
    if score >= PRESENCE_POSITIVE_THRESHOLD:
        present: bool | None = True
    elif score < PRESENCE_ABSENT_THRESHOLD:
        present = False
    else:
        present = None
    return SemanticClassDecision(code, present, score)


def _parse_payload(payload: dict[str, Any]) -> SemanticGateResult:
    if not isinstance(payload, dict) or not isinstance(payload.get("scores"), dict):
        raise ModelUnavailableError("Qwen3-VL JSON 缺少 scores 对象")
    if set(payload) != {"scores"}:
        raise ModelUnavailableError("Qwen3-VL JSON 顶层只能包含 scores")

    raw_scores: dict[str, Any] = payload["scores"]
    normalized_codes = {str(code).upper() for code in raw_scores}
    unknown = normalized_codes - ALLOWED_CLASS_CODES
    missing = ALLOWED_CLASS_CODES - normalized_codes
    if unknown:
        raise ModelUnavailableError(f"Qwen3-VL 返回未知病害类别：{sorted(unknown)}")
    if missing:
        raise ModelUnavailableError(f"Qwen3-VL JSON 缺少病害类别：{sorted(missing)}")

    normalized_items = {str(key).upper(): value for key, value in raw_scores.items()}
    decisions: dict[str, SemanticClassDecision] = {}
    for code in sorted(ALLOWED_CLASS_CODES):
        raw_score = normalized_items[code]
        if isinstance(raw_score, bool):
            raise ModelUnavailableError(f"Qwen3-VL 类别 {code} presenceScore 非法")
        try:
            score = float(raw_score)
        except (TypeError, ValueError) as ex:
            raise ModelUnavailableError(
                f"Qwen3-VL 类别 {code} presenceScore 非法"
            ) from ex
        if not 0.0 <= score <= 1.0:
            raise ModelUnavailableError(
                f"Qwen3-VL 类别 {code} presenceScore 超出范围"
            )
        decisions[code] = _decision_from_presence_score(code, score)
    return SemanticGateResult(decisions)


def _validate_max_new_tokens(value: int) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError("Qwen3-VL max_new_tokens 必须是整数")
    if not MIN_QWEN_MAX_NEW_TOKENS <= value <= MAX_QWEN_MAX_NEW_TOKENS:
        raise ValueError(
            "Qwen3-VL max_new_tokens 必须位于 "
            f"[{MIN_QWEN_MAX_NEW_TOKENS}, {MAX_QWEN_MAX_NEW_TOKENS}]"
        )
    return value


def _resolve_max_new_tokens(value: int | None) -> int:
    if value is not None:
        return _validate_max_new_tokens(value)
    raw = os.getenv(
        "AI_QWEN_MAX_NEW_TOKENS",
        os.getenv("URBAN_SAFE_AI_QWEN_MAX_NEW_TOKENS", str(DEFAULT_QWEN_MAX_NEW_TOKENS)),
    ).strip()
    try:
        parsed = int(raw)
    except ValueError as ex:
        raise ValueError("环境变量 AI_QWEN_MAX_NEW_TOKENS 必须是整数") from ex
    return _validate_max_new_tokens(parsed)


class _TransformersQwenBackend:
    def __init__(
        self,
        model_path: Path,
        device: str = "cuda",
        max_side: int = 1024,
        max_new_tokens: int = DEFAULT_QWEN_MAX_NEW_TOKENS,
    ) -> None:
        self._max_new_tokens = _validate_max_new_tokens(max_new_tokens)
        try:
            import torch
            from transformers import AutoProcessor, Qwen3VLForConditionalGeneration
        except ImportError as ex:
            raise ModelUnavailableError("未安装 Qwen3-VL 所需 transformers/torch 依赖") from ex
        if device.startswith("cuda") and not torch.cuda.is_available():
            raise ModelUnavailableError("Qwen3-VL ACCURACY 实验需要 CUDA")
        try:
            self._torch = torch
            self._device = device
            self._max_side = max_side
            self._processor = AutoProcessor.from_pretrained(
                str(model_path), local_files_only=True
            )
            self._model = Qwen3VLForConditionalGeneration.from_pretrained(
                str(model_path),
                local_files_only=True,
                dtype=torch.float16 if device.startswith("cuda") else torch.float32,
            ).to(device).eval()
        except Exception as ex:
            raise ModelUnavailableError(f"Qwen3-VL 本地权重加载失败：{model_path}") from ex

    def _prepare_image(self, image: Image.Image) -> Image.Image:
        prepared = ImageOps.exif_transpose(image).convert("RGB")
        if max(prepared.size) <= self._max_side:
            return prepared
        scale = self._max_side / float(max(prepared.size))
        return prepared.resize(
            (max(1, round(prepared.width * scale)), max(1, round(prepared.height * scale))),
            Image.Resampling.LANCZOS,
        )

    def generate_json(self, image: Image.Image, prompt: str) -> str:
        image = self._prepare_image(image)
        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image},
                    {"type": "text", "text": prompt},
                ],
            }
        ]
        try:
            inputs = self._processor.apply_chat_template(
                messages,
                tokenize=True,
                add_generation_prompt=True,
                return_dict=True,
                return_tensors="pt",
            ).to(self._device)
            with self._torch.inference_mode():
                generated = self._model.generate(
                    **inputs,
                    max_new_tokens=self._max_new_tokens,
                    do_sample=False,
                )
            trimmed = [
                out_ids[len(in_ids):]
                for in_ids, out_ids in zip(inputs.input_ids, generated, strict=True)
            ]
            return self._processor.batch_decode(
                trimmed,
                skip_special_tokens=True,
                clean_up_tokenization_spaces=False,
            )[0].strip()
        except Exception as ex:
            raise ModelUnavailableError("Qwen3-VL 语义门控推理失败") from ex

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


class Qwen3VlClassifier:
    """只输出固定六类图片级 presenceScore；定位由下游模型负责。"""

    def __init__(
        self,
        model_path: Path,
        device: str = "cuda",
        backend=None,
        max_side: int = 1024,
        max_new_tokens: int | None = None,
    ) -> None:
        self.model_path = Path(model_path)
        self.max_new_tokens = _resolve_max_new_tokens(max_new_tokens)
        self._backend = backend or _TransformersQwenBackend(
            self.model_path,
            device=device,
            max_side=max_side,
            max_new_tokens=self.max_new_tokens,
        )

    def classify(self, image: Image.Image) -> SemanticGateResult:
        first_raw = self._backend.generate_json(image, _GATE_PROMPT)
        try:
            return _parse_payload(_extract_json_payload(first_raw))
        except ModelUnavailableError as first_error:
            retry_raw = self._backend.generate_json(image, _GATE_RETRY_PROMPT)
            try:
                return _parse_payload(_extract_json_payload(retry_raw))
            except ModelUnavailableError as retry_error:
                raise ModelUnavailableError(
                    "Qwen3-VL 两次输出都不是可用的完整六类 presenceScore JSON；"
                    f"first_error={first_error}; retry_error={retry_error}; "
                    f"first_raw={_raw_preview(first_raw)!r}; "
                    f"retry_raw={_raw_preview(retry_raw)!r}"
                ) from retry_error

    @staticmethod
    def _parse(raw: str) -> SemanticGateResult:
        return _parse_payload(_extract_json_payload(raw))

    def close(self) -> None:
        backend = self._backend
        self._backend = None
        if backend is not None and hasattr(backend, "close"):
            backend.close()
