"""CUDA-only YOLOX 建筑病害目标检测适配器。"""

from __future__ import annotations

import io
from typing import Any

import numpy as np
from PIL import Image

from ..errors import ModelUnavailableError
from ..image import DecodedImage
from ..model_manifest import ModelManifestError
from ..schemas import (
    Applicability,
    BoundingBox,
    CoordinateType,
    DetectionItem,
    ModelBrief,
)
from ..yolox_manifest import YoloXModelManifest
from ..yolox_ops import decode_yolox, letterbox_rgb, multiclass_nms, to_normalized_xywh


CUDA_EXECUTION_PROVIDER = "CUDAExecutionProvider"
CPU_EXECUTION_PROVIDER = "CPUExecutionProvider"
DISABLE_CPU_FALLBACK_KEY = "session.disable_cpu_ep_fallback"
RECORD_EP_GRAPH_ASSIGNMENT_KEY = "session.record_ep_graph_assignment_info"


class YoloXBuildingDefectAdapter:
    """将 YOLOX ONNX 检测结果转换成统一 DetectionItem。"""

    INPUT_NAME = "images"
    OUTPUT_NAME = "output"

    def __init__(
        self,
        manifest: YoloXModelManifest,
        session: Any | None = None,
        cuda_device_id: int = 0,
    ) -> None:
        if manifest.adapter != "yolox-building-defect-v1":
            raise ModelManifestError("清单适配器与 YOLOX 建筑病害检测不匹配")
        self._manifest = manifest
        self._session = session or _create_yolox_session(manifest, cuda_device_id)

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
        self,
        image: DecodedImage,
    ) -> tuple[Applicability, list[DetectionItem]]:
        if image.applicability == Applicability.LOW_QUALITY:
            return Applicability.LOW_QUALITY, []

        try:
            pil = Image.open(io.BytesIO(image.bytes_)).convert("RGB")
            tensor, ratio = letterbox_rgb(
                pil,
                self._manifest.input.width,
                self._manifest.input.height,
                self._manifest.input.pad_value,
            )
        except (OSError, ValueError) as ex:
            raise ModelUnavailableError("YOLOX 图片预处理失败") from ex

        batch = tensor[None, ...]
        try:
            outputs = self._session.run(
                [self.OUTPUT_NAME],
                {self.INPUT_NAME: batch},
            )
        except Exception as ex:
            raise ModelUnavailableError("YOLOX CUDA 推理失败") from ex

        output = _single_yolox_output(outputs, self._expected_output_shape())
        try:
            boxes, scores, class_ids = decode_yolox(
                output,
                (self._manifest.input.height, self._manifest.input.width),
                len(self._manifest.classes),
            )
        except ValueError as ex:
            raise ModelUnavailableError("YOLOX 输出解码失败") from ex

        finite = (
            np.isfinite(scores)
            & np.isfinite(boxes).all(axis=1)
            & (scores >= self._manifest.thresholds.score)
        )
        candidate_indices = np.flatnonzero(finite)
        if candidate_indices.size == 0:
            return Applicability.NO_DEFECT_FOUND, []

        candidate_boxes = boxes[candidate_indices]
        candidate_scores = scores[candidate_indices]
        candidate_classes = class_ids[candidate_indices]
        keep_local = multiclass_nms(
            candidate_boxes,
            candidate_scores,
            candidate_classes,
            iou_threshold=self._manifest.thresholds.nms_iou,
            max_detections=self._manifest.thresholds.maximum_detections,
        )

        detections: list[DetectionItem] = []
        for local_index in keep_local:
            class_index = int(candidate_classes[local_index])
            if class_index < 0 or class_index >= len(self._manifest.classes):
                continue
            normalized = to_normalized_xywh(
                candidate_boxes[local_index],
                ratio=ratio,
                source_width=image.width,
                source_height=image.height,
            )
            if normalized is None:
                continue
            x, y, width, height = normalized
            model_class = self._manifest.classes[class_index]
            detections.append(
                DetectionItem(
                    sequence=len(detections) + 1,
                    classCode=model_class.code,
                    className=model_class.name,
                    confidence=float(np.clip(candidate_scores[local_index], 0.0, 1.0)),
                    boundingBox=BoundingBox(
                        x=x,
                        y=y,
                        width=width,
                        height=height,
                        coordinateType=CoordinateType.NORMALIZED_XYWH,
                    ),
                )
            )

        if not detections:
            return Applicability.NO_DEFECT_FOUND, []
        return Applicability.APPLICABLE, detections

    def _expected_output_shape(self) -> tuple[int, int, int]:
        candidate_count = sum(
            (self._manifest.input.height // stride)
            * (self._manifest.input.width // stride)
            for stride in (8, 16, 32)
        )
        return (1, candidate_count, 5 + len(self._manifest.classes))


def _create_yolox_session(manifest: YoloXModelManifest, cuda_device_id: int = 0):
    """创建禁用中央处理器回退的 YOLOX CUDA ONNX 会话并执行热身。"""

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
        _validate_cuda_graph(session)
        _validate_yolox_session_contract(session, manifest)
        _warm_up_yolox_session(session, manifest)
        return session
    except ModelUnavailableError:
        raise
    except Exception as ex:
        raise ModelUnavailableError("YOLOX CUDA ONNX 会话初始化失败") from ex


def _validate_cuda_graph(session: Any) -> None:
    providers = list(session.get_providers())
    if not providers or providers[0] != CUDA_EXECUTION_PROVIDER:
        raise ModelUnavailableError("YOLOX 模型未运行在 CUDAExecutionProvider")

    get_assignment_info = getattr(session, "get_provider_graph_assignment_info", None)
    if not callable(get_assignment_info):
        raise ModelUnavailableError("ONNX Runtime 不支持执行图分配校验")
    assignments = list(get_assignment_info())
    if not assignments:
        raise ModelUnavailableError("ONNX Runtime 未返回执行图分配信息")
    for assignment in assignments:
        ep_name = getattr(assignment, "ep_name", None)
        if ep_name == CPU_EXECUTION_PROVIDER:
            raise ModelUnavailableError(
                "检测到 CPUExecutionProvider 图分配，拒绝启动 YOLOX 真实模型"
            )
        if ep_name != CUDA_EXECUTION_PROVIDER:
            raise ModelUnavailableError(f"检测到非 CUDA 执行图分配：{ep_name}")


def _validate_yolox_session_contract(session: Any, manifest: YoloXModelManifest) -> None:
    inputs = list(session.get_inputs())
    outputs = list(session.get_outputs())
    if len(inputs) != 1 or inputs[0].name != YoloXBuildingDefectAdapter.INPUT_NAME:
        raise ModelUnavailableError("YOLOX ONNX 输入契约不匹配")
    if len(outputs) != 1 or outputs[0].name != YoloXBuildingDefectAdapter.OUTPUT_NAME:
        raise ModelUnavailableError("YOLOX ONNX 输出契约不匹配")

    expected_input = (1, 3, manifest.input.height, manifest.input.width)
    expected_output = _expected_output_shape(manifest)
    if not _matches_shape(getattr(inputs[0], "shape", None), expected_input):
        raise ModelUnavailableError("YOLOX ONNX 输入形状不匹配")
    if not _matches_shape(getattr(outputs[0], "shape", None), expected_output):
        raise ModelUnavailableError("YOLOX ONNX 输出形状不匹配")
    if getattr(inputs[0], "type", None) not in (None, "tensor(float)"):
        raise ModelUnavailableError("YOLOX ONNX 输入类型必须为 float32")
    if getattr(outputs[0], "type", None) not in (None, "tensor(float)"):
        raise ModelUnavailableError("YOLOX ONNX 输出类型必须为 float32")


def _warm_up_yolox_session(session: Any, manifest: YoloXModelManifest) -> None:
    warmup = np.zeros(
        (1, 3, manifest.input.height, manifest.input.width),
        dtype=np.float32,
    )
    outputs = session.run(
        [YoloXBuildingDefectAdapter.OUTPUT_NAME],
        {YoloXBuildingDefectAdapter.INPUT_NAME: warmup},
    )
    _single_yolox_output(outputs, _expected_output_shape(manifest))


def _expected_output_shape(manifest: YoloXModelManifest) -> tuple[int, int, int]:
    candidate_count = sum(
        (manifest.input.height // stride) * (manifest.input.width // stride)
        for stride in (8, 16, 32)
    )
    return (1, candidate_count, 5 + len(manifest.classes))


def _matches_shape(actual: Any, expected: tuple[int, ...]) -> bool:
    if actual is None:
        return False
    try:
        normalized = tuple(int(value) for value in actual)
    except (TypeError, ValueError):
        return False
    return normalized == expected


def _single_yolox_output(outputs: Any, expected_shape: tuple[int, int, int]) -> np.ndarray:
    if not isinstance(outputs, (list, tuple)) or len(outputs) != 1:
        raise ModelUnavailableError("YOLOX ONNX 输出数量不匹配")
    output = np.asarray(outputs[0], dtype=np.float32)
    if output.shape != expected_shape:
        raise ModelUnavailableError("YOLOX ONNX 输出形状不匹配")
    if not np.isfinite(output).all():
        raise ModelUnavailableError("YOLOX ONNX 输出包含非有限数值")
    return output
