"""CUDA-only ONNX 裂缝二值分割适配器。"""

from __future__ import annotations

import io
from collections import deque
from dataclasses import dataclass
from typing import Any, Iterable

import numpy as np
from PIL import Image

from ..errors import ModelUnavailableError
from ..image import DecodedImage
from ..model_manifest import ModelManifest, ModelManifestError, ModelThresholds
from ..schemas import Applicability, BoundingBox, CoordinateType, DetectionItem, ModelBrief


CUDA_EXECUTION_PROVIDER = "CUDAExecutionProvider"
CPU_EXECUTION_PROVIDER = "CPUExecutionProvider"
DISABLE_CPU_FALLBACK_KEY = "session.disable_cpu_ep_fallback"
RECORD_EP_GRAPH_ASSIGNMENT_KEY = "session.record_ep_graph_assignment_info"
PIL_RESAMPLING = {
    "BILINEAR": Image.Resampling.BILINEAR,
    "BICUBIC": Image.Resampling.BICUBIC,
    "LANCZOS": Image.Resampling.LANCZOS,
}


@dataclass(frozen=True)
class ComponentCandidate:
    """通过安全门禁后的连通区域摘要。"""

    y_min: int
    y_max: int
    x_min: int
    x_max: int
    confidence: float
    component_area_ratio: float
    bounding_box_area_ratio: float


class OnnxCrackSegmentationAdapter:
    """把 CUDA 上的二值裂缝掩膜转换为统一检测框。"""

    INPUT_NAME = "images"
    OUTPUT_NAME = "mask_logits"

    def __init__(
        self,
        manifest: ModelManifest,
        session: Any | None = None,
        cuda_device_id: int = 0,
    ) -> None:
        if manifest.adapter != "onnx-crack-segmentation-v1":
            raise ModelManifestError("清单适配器与 ONNX 裂缝分割不匹配")
        self._manifest = manifest
        self._session = session or _create_session(manifest, cuda_device_id)
        self._crack_class = next(
            item for item in manifest.classes if item.code == "CRACK"
        )

    def model_info(self) -> ModelBrief:
        return ModelBrief(
            modelId=self._manifest.model_id,
            modelName=self._manifest.model_name,
            version=self._manifest.version,
        )

    def execution_provider(self) -> str | None:
        get_providers = getattr(self._session, "get_providers", None)
        if get_providers is None:
            return None
        providers = list(get_providers())
        return providers[0] if providers else None

    def predict(
        self, image: DecodedImage
    ) -> tuple[Applicability, list[DetectionItem]]:
        if image.applicability == Applicability.LOW_QUALITY:
            return Applicability.LOW_QUALITY, []

        raw_output, model_probabilities, foreground_scores = self.raw_output(image)
        del raw_output, model_probabilities
        mask = foreground_scores >= self._manifest.thresholds.mask
        components = _connected_components(
            mask, self._manifest.thresholds.min_component_pixels
        )
        if not components:
            return Applicability.NO_DEFECT_FOUND, []

        candidates, oversized_activation = _filter_component_candidates(
            foreground_scores,
            components,
            self._manifest.thresholds,
        )
        if oversized_activation:
            return Applicability.NOT_APPLICABLE, []
        if not candidates:
            return Applicability.NO_DEFECT_FOUND, []

        height, width = mask.shape
        detections: list[DetectionItem] = []
        for sequence, candidate in enumerate(candidates, start=1):
            detections.append(
                DetectionItem(
                    sequence=sequence,
                    classCode=self._crack_class.code,
                    className=self._crack_class.name,
                    confidence=max(0.0, min(1.0, candidate.confidence)),
                    boundingBox=BoundingBox(
                        x=candidate.x_min / width,
                        y=candidate.y_min / height,
                        width=(candidate.x_max - candidate.x_min + 1) / width,
                        height=(candidate.y_max - candidate.y_min + 1) / height,
                        coordinateType=CoordinateType.NORMALIZED_XYWH,
                    ),
                )
            )
        return Applicability.APPLICABLE, detections

    def raw_output(
        self, image: DecodedImage
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        """返回原始输出、模型概率和统一为“值越高越像裂缝”的前景分数。"""

        batch = self.preprocess(image)
        try:
            outputs = self._session.run(
                [self.OUTPUT_NAME], {self.INPUT_NAME: batch}
            )
        except Exception as ex:
            raise ModelUnavailableError("CUDA 模型推理失败") from ex
        output = _single_output(outputs, self._expected_output_shape())[0, 0]
        model_probabilities = _output_probabilities(
            output, self._manifest.output_activation
        )
        foreground_scores = _foreground_probabilities(
            model_probabilities, self._manifest.foreground_polarity
        )
        return output, model_probabilities, foreground_scores

    def preprocess(self, image: DecodedImage) -> np.ndarray:
        """按模型清单执行固定尺寸、插值和归一化预处理。"""

        try:
            pil = Image.open(io.BytesIO(image.bytes_)).convert("RGB")
            pil = pil.resize(
                (self._manifest.input.width, self._manifest.input.height),
                PIL_RESAMPLING[self._manifest.input.interpolation],
            )
        except (KeyError, OSError, ValueError) as ex:
            raise ModelUnavailableError("模型图片预处理失败") from ex

        array = np.asarray(pil, dtype=np.float32) / 255.0
        mean = np.asarray(self._manifest.input.mean, dtype=np.float32)
        std = np.asarray(self._manifest.input.std, dtype=np.float32)
        array = (array - mean) / std
        return np.transpose(array, (2, 0, 1))[None, ...].astype(np.float32)

    def _expected_output_shape(self) -> tuple[int, int, int, int]:
        return (
            1,
            1,
            self._manifest.input.height,
            self._manifest.input.width,
        )


def _filter_component_candidates(
    foreground_scores: np.ndarray,
    components: list[list[tuple[int, int]]],
    thresholds: ModelThresholds,
) -> tuple[list[ComponentCandidate], bool]:
    """应用面积、外接框、平均前景置信度和数量门禁。"""

    if foreground_scores.ndim != 2:
        raise ModelUnavailableError("模型前景分数图维度不合法")
    height, width = foreground_scores.shape
    image_pixels = height * width
    candidates: list[ComponentCandidate] = []
    oversized_activation = False

    for component in components:
        ys = np.fromiter((point[0] for point in component), dtype=np.int32)
        xs = np.fromiter((point[1] for point in component), dtype=np.int32)
        y_min, y_max = int(ys.min()), int(ys.max())
        x_min, x_max = int(xs.min()), int(xs.max())
        component_area_ratio = len(component) / image_pixels
        bounding_box_area_ratio = (
            (x_max - x_min + 1) * (y_max - y_min + 1) / image_pixels
        )

        if (
            component_area_ratio > thresholds.max_component_area_ratio
            or bounding_box_area_ratio > thresholds.max_bounding_box_area_ratio
        ):
            oversized_activation = True
            continue

        confidence = float(foreground_scores[ys, xs].mean())
        if confidence < thresholds.minimum_mean_confidence:
            continue

        candidates.append(
            ComponentCandidate(
                y_min=y_min,
                y_max=y_max,
                x_min=x_min,
                x_max=x_max,
                confidence=confidence,
                component_area_ratio=component_area_ratio,
                bounding_box_area_ratio=bounding_box_area_ratio,
            )
        )
        if len(candidates) >= thresholds.maximum_detections:
            break

    return candidates, oversized_activation


def _create_session(manifest: ModelManifest, cuda_device_id: int = 0):
    """创建禁用一切 CPU 回退的 CUDA 会话，并执行一次真实热身。"""

    try:
        import onnxruntime as ort
    except ImportError as ex:
        raise ModelUnavailableError("未安装 onnxruntime-gpu") from ex

    try:
        available_providers = set(ort.get_available_providers())
        if CUDA_EXECUTION_PROVIDER not in available_providers:
            raise ModelUnavailableError("ONNX Runtime 未提供 CUDAExecutionProvider")

        session_options = ort.SessionOptions()
        session_options.add_session_config_entry(DISABLE_CPU_FALLBACK_KEY, "1")
        session_options.add_session_config_entry(RECORD_EP_GRAPH_ASSIGNMENT_KEY, "1")
        providers = [
            (
                CUDA_EXECUTION_PROVIDER,
                {
                    "device_id": str(cuda_device_id),
                    "do_copy_in_default_stream": "1",
                },
            )
        ]
        session = ort.InferenceSession(
            str(manifest.weight_path),
            sess_options=session_options,
            providers=providers,
        )
        disable_fallback = getattr(session, "disable_fallback", None)
        if not callable(disable_fallback):
            raise ModelUnavailableError("ONNX Runtime 不支持禁用执行后端回退")
        disable_fallback()
        _validate_cuda_only_session(session)
        _validate_session_contract(session, manifest)
        _warm_up_session(session, manifest)
        return session
    except ModelUnavailableError:
        raise
    except Exception as ex:
        raise ModelUnavailableError("CUDA ONNX 会话初始化失败") from ex


def _validate_cuda_only_session(session: Any) -> None:
    providers = list(session.get_providers())
    if not providers or providers[0] != CUDA_EXECUTION_PROVIDER:
        raise ModelUnavailableError("模型未运行在 CUDAExecutionProvider")
    _validate_cuda_graph_assignment(session)


def _validate_cuda_graph_assignment(session: Any) -> None:
    """校验 ONNX 图中的实际子图分配没有落到中央处理器。"""

    get_assignment_info = getattr(session, "get_provider_graph_assignment_info", None)
    if not callable(get_assignment_info):
        raise ModelUnavailableError("ONNX Runtime 不支持执行图分配校验")
    assignments = list(get_assignment_info())
    if not assignments:
        raise ModelUnavailableError("ONNX Runtime 未返回执行图分配信息")
    for assignment in assignments:
        ep_name = getattr(assignment, "ep_name", None)
        if ep_name == CPU_EXECUTION_PROVIDER:
            raise ModelUnavailableError("检测到 CPUExecutionProvider 图分配，拒绝启动真实模型")
        if ep_name != CUDA_EXECUTION_PROVIDER:
            raise ModelUnavailableError(f"检测到非 CUDA 执行图分配：{ep_name}")


def _validate_session_contract(session: Any, manifest: ModelManifest) -> None:
    inputs = list(session.get_inputs())
    outputs = list(session.get_outputs())
    if len(inputs) != 1 or inputs[0].name != OnnxCrackSegmentationAdapter.INPUT_NAME:
        raise ModelUnavailableError("ONNX 输入契约不匹配")
    if len(outputs) != 1 or outputs[0].name != OnnxCrackSegmentationAdapter.OUTPUT_NAME:
        raise ModelUnavailableError("ONNX 输出契约不匹配")

    expected_input_shape = (
        1,
        3,
        manifest.input.height,
        manifest.input.width,
    )
    expected_output_shape = (
        1,
        1,
        manifest.input.height,
        manifest.input.width,
    )
    if not _matches_shape(getattr(inputs[0], "shape", None), expected_input_shape):
        raise ModelUnavailableError("ONNX 输入形状不匹配")
    if not _matches_shape(getattr(outputs[0], "shape", None), expected_output_shape):
        raise ModelUnavailableError("ONNX 输出形状不匹配")
    if getattr(inputs[0], "type", None) not in (None, "tensor(float)"):
        raise ModelUnavailableError("ONNX 输入类型必须为 float32")
    if getattr(outputs[0], "type", None) not in (None, "tensor(float)"):
        raise ModelUnavailableError("ONNX 输出类型必须为 float32")


def _warm_up_session(session: Any, manifest: ModelManifest) -> None:
    warmup = np.zeros(
        (1, 3, manifest.input.height, manifest.input.width), dtype=np.float32
    )
    outputs = session.run(
        [OnnxCrackSegmentationAdapter.OUTPUT_NAME],
        {OnnxCrackSegmentationAdapter.INPUT_NAME: warmup},
    )
    _single_output(
        outputs,
        (1, 1, manifest.input.height, manifest.input.width),
    )


def _matches_shape(actual: Any, expected: tuple[int, ...]) -> bool:
    if actual is None:
        return False
    try:
        normalized = tuple(int(value) for value in actual)
    except (TypeError, ValueError):
        return False
    return normalized == expected


def _single_output(outputs: Any, expected_shape: tuple[int, ...]) -> np.ndarray:
    if not isinstance(outputs, (list, tuple)) or len(outputs) != 1:
        raise ModelUnavailableError("ONNX 输出数量不匹配")
    output = np.asarray(outputs[0], dtype=np.float32)
    if output.shape != expected_shape:
        raise ModelUnavailableError("ONNX 输出形状不匹配")
    if not np.isfinite(output).all():
        raise ModelUnavailableError("ONNX 输出包含非有限数值")
    return output


def _sigmoid(logits: np.ndarray) -> np.ndarray:
    clipped = np.clip(logits, -30.0, 30.0)
    return 1.0 / (1.0 + np.exp(-clipped))


def _output_probabilities(output: np.ndarray, activation: str) -> np.ndarray:
    """依据清单把原始模型输出转换为概率，禁止对概率重复执行 Sigmoid。"""

    values = np.asarray(output, dtype=np.float32)
    if not np.isfinite(values).all():
        raise ModelUnavailableError("模型输出包含非有限数值")
    normalized_activation = activation.strip().upper()
    if normalized_activation == "LOGITS":
        return _sigmoid(values)
    if normalized_activation == "PROBABILITY":
        if float(values.min()) < -1e-6 or float(values.max()) > 1.0 + 1e-6:
            raise ModelUnavailableError("概率输出必须位于 0 和 1 之间")
        return np.clip(values, 0.0, 1.0)
    raise ModelUnavailableError("不支持的模型输出激活类型")


def _foreground_probabilities(
    model_probabilities: np.ndarray, foreground_polarity: str
) -> np.ndarray:
    """把不同模型语义统一成值越高越像裂缝的前景分数。"""

    probabilities = np.asarray(model_probabilities, dtype=np.float32)
    if not np.isfinite(probabilities).all():
        raise ModelUnavailableError("模型概率包含非有限数值")
    if float(probabilities.min()) < -1e-6 or float(probabilities.max()) > 1.0 + 1e-6:
        raise ModelUnavailableError("模型概率必须位于 0 和 1 之间")
    normalized = foreground_polarity.strip().upper()
    if normalized == "HIGH_PROBABILITY":
        return np.clip(probabilities, 0.0, 1.0)
    if normalized == "LOW_PROBABILITY":
        return 1.0 - np.clip(probabilities, 0.0, 1.0)
    raise ModelUnavailableError("不支持的模型前景极性")


def summarize_output_tensor(
    values: np.ndarray, thresholds: Iterable[float]
) -> dict[str, Any]:
    """汇总原始输出或概率图的范围、分位数与阈值激活比例。"""

    array = np.asarray(values, dtype=np.float32)
    if array.size == 0 or not np.isfinite(array).all():
        raise ModelUnavailableError("诊断张量为空或包含非有限数值")
    flattened = array.reshape(-1)
    percentile_values = np.percentile(flattened, [1, 5, 25, 50, 75, 95, 99])
    percentiles = {
        name: float(value)
        for name, value in zip(
            ("p01", "p05", "p25", "p50", "p75", "p95", "p99"),
            percentile_values,
            strict=True,
        )
    }
    activation_ratios: dict[str, float] = {}
    for threshold in thresholds:
        numeric = float(threshold)
        if not 0.0 <= numeric <= 1.0:
            raise ModelUnavailableError("诊断阈值必须位于 0 和 1 之间")
        activation_ratios[f"{numeric:g}"] = float(np.mean(flattened >= numeric))
    return {
        "shape": list(array.shape),
        "minimum": float(flattened.min()),
        "maximum": float(flattened.max()),
        "mean": float(flattened.mean()),
        "standardDeviation": float(flattened.std()),
        "percentiles": percentiles,
        "activationRatios": activation_ratios,
    }


def _connected_components(
    mask: np.ndarray, min_component_pixels: int
) -> list[list[tuple[int, int]]]:
    if mask.ndim != 2:
        raise ModelUnavailableError("模型掩膜维度不合法")
    height, width = mask.shape
    visited = np.zeros_like(mask, dtype=bool)
    components: list[list[tuple[int, int]]] = []

    for y in range(height):
        for x in range(width):
            if not mask[y, x] or visited[y, x]:
                continue
            queue: deque[tuple[int, int]] = deque([(y, x)])
            visited[y, x] = True
            component: list[tuple[int, int]] = []
            while queue:
                current_y, current_x = queue.popleft()
                component.append((current_y, current_x))
                for next_y, next_x in (
                    (current_y - 1, current_x),
                    (current_y + 1, current_x),
                    (current_y, current_x - 1),
                    (current_y, current_x + 1),
                ):
                    if (
                        0 <= next_y < height
                        and 0 <= next_x < width
                        and mask[next_y, next_x]
                        and not visited[next_y, next_x]
                    ):
                        visited[next_y, next_x] = True
                        queue.append((next_y, next_x))
            if len(component) >= min_component_pixels:
                components.append(component)

    components.sort(key=len, reverse=True)
    return components
