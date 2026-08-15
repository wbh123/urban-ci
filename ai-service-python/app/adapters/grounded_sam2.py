"""Grounding DINO Tiny + SAM 2.1 Hiera Tiny 零样本建筑表观病害适配器。

使用官方 Transformers 稳定加载方式，不需要自定义 CUDA 扩展：

- GroundingDinoProcessor / GroundingDinoForObjectDetection：根据多提示词出检测框；
- Sam2Processor / Sam2Model：以检测框作为 Bounding Box Prompt 生成掩膜，掩膜
  简化为归一化轮廓多边形进入统一 Detection.segmentation（可选字段）。

准入门禁：

- 仅 APPROVED 清单允许加载进 REAL 运行时；
- SHA 门禁默认 STRICT：启动加载阶段重算 detector/segmenter 目录摘要并与 manifest
  对比，不一致拒绝启动（不进入 REAL READY）。

显存策略：batch=1、SAM2 float16、长边默认 1280；OOM 时 1280 → 1024 → 896，仍失败则
抛出 ModelUnavailableError，绝不静默切换 MOCK。权重只从本地目录加载，运行时不得访问公网。
"""

from __future__ import annotations

import inspect
import io

import numpy as np
from PIL import Image

from ..config import Settings
from ..errors import ModelUnavailableError
from ..image import DecodedImage
from ..model_digest import combine_digests, dir_digest
from ..schemas import (
    Applicability,
    BoundingBox,
    CoordinateType,
    DetectionItem,
    ModelBrief,
    SegmentationPolygon,
)

PY_TORCH_CUDA_PROVIDER = "PyTorch-CUDA"
DEFAULT_RESOLUTION_LADDER = (1280, 1024, 896)
NMS_IOU_THRESHOLD = 0.5
POLYGON_MAX_POINTS = 40


class GroundedSam2TinyAdapter:
    """把 Grounding DINO Tiny + SAM 2.1 Tiny 转换为项目统一 Detection。"""

    def __init__(
        self,
        manifest,
        settings: Settings,
        require_approved: bool = True,
    ) -> None:
        self._manifest = manifest
        self._settings = settings
        self._torch = None
        self.oom_fallback_count = 0
        self.sam2_box_fallback = False
        # 注册表工厂默认 require_approved=True（CANDIDATE 禁止进入 REAL 运行时）；
        # benchmark 工具显式传 False，以便在批准前对 CANDIDATE 做真实基准。
        if require_approved and manifest.status != "APPROVED":
            raise ModelUnavailableError("模型未批准，禁止加载进 REAL 运行时")
        self._verify_weight_sha()
        self._resolve_runtime()
        self._load_models()

    # ---------- 推理协议 ----------

    def model_info(self) -> ModelBrief:
        return ModelBrief(
            modelId=self._manifest.model_id,
            modelName=self._manifest.model_name,
            version=self._manifest.version,
        )

    def execution_provider(self) -> str:
        """真实 Transformers 视觉模型只允许 CUDA，返回 PyTorch-CUDA 标识。"""

        return PY_TORCH_CUDA_PROVIDER

    def predict(self, image: DecodedImage) -> tuple[Applicability, list[DetectionItem]]:
        if image.applicability == Applicability.LOW_QUALITY:
            return Applicability.LOW_QUALITY, []

        try:
            pil = Image.open(io.BytesIO(image.bytes_)).convert("RGB")
        except Exception as ex:
            raise ModelUnavailableError("视觉模型图片解码失败") from ex

        detections = self._infer_with_oom_fallback(pil)
        if not detections:
            return Applicability.NO_DEFECT_FOUND, []
        return Applicability.APPLICABLE, detections

    # ---------- 运行时与模型加载 ----------

    def _verify_weight_sha(self) -> None:
        """STRICT 模式：加载阶段重算权重目录摘要并与 manifest 对比。

        只在模型启动加载阶段执行一次，不进入每个推理请求。
        """

        if self._settings.vision_sha_mode != "STRICT":
            return
        try:
            detector_sha, _ = dir_digest(self._manifest.checkpoint.detector_dir)
            segmenter_sha, _ = dir_digest(self._manifest.checkpoint.segmenter_dir)
        except OSError as ex:
            raise ModelUnavailableError(
                "权重目录不存在或不可读（请先运行 download-models-demo.sh）"
            ) from ex
        expected = self._manifest.weight_sha256
        actual = combine_digests(detector_sha, segmenter_sha)
        if actual != expected:
            raise ModelUnavailableError(
                "模型权重摘要与 manifest 不一致，拒绝启动（STRICT 门禁）"
            )

    def _resolve_runtime(self) -> None:
        try:
            import torch
        except ImportError as ex:
            raise ModelUnavailableError("未安装 torch，无法加载视觉模型") from ex
        self._torch = torch
        if not torch.cuda.is_available():
            raise ModelUnavailableError("零样本视觉模型需要 CUDA，但当前 CUDA 不可用")
        device = (self._settings.vision_device or "cuda").strip().lower()
        if device not in ("cuda", "gpu"):
            raise ModelUnavailableError("零样本视觉模型只允许 CUDA 推理，不接受 CPU 回退")
        self._device = "cuda"
        dtype_name = (self._settings.vision_dtype or "float16").strip().lower()
        if dtype_name == "float16":
            self._dtype = torch.float16
        elif dtype_name in ("float32", "float"):
            self._dtype = torch.float32
        else:
            raise ModelUnavailableError(f"不支持的视觉模型精度：{dtype_name}")
        # Grounding DINO 的可变形注意力（deformable attention）在 Transformers 中
        # 不支持 float16（grid_sample 存在 Half/Float 不匹配），检测器固定 float32；
        # SAM 2 分割器按配置使用 float16 以节省显存。
        self._dino_dtype = torch.float32

    def _load_models(self) -> None:
        try:
            from transformers import (
                GroundingDinoForObjectDetection,
                GroundingDinoProcessor,
                Sam2Model,
                Sam2Processor,
            )
        except ImportError as ex:
            raise ModelUnavailableError("未安装 transformers 视觉模型依赖") from ex

        offline = self._settings.vision_offline
        try:
            detector_dir = str(self._manifest.checkpoint.detector_dir)
            segmenter_dir = str(self._manifest.checkpoint.segmenter_dir)
            self._dino_processor = GroundingDinoProcessor.from_pretrained(
                detector_dir, local_files_only=offline
            )
            self._dino = GroundingDinoForObjectDetection.from_pretrained(
                detector_dir,
                local_files_only=offline,
                torch_dtype=self._dino_dtype,
            ).to(self._device).eval()
            self._sam_processor = Sam2Processor.from_pretrained(
                segmenter_dir, local_files_only=offline
            )
            self._sam = Sam2Model.from_pretrained(
                segmenter_dir,
                local_files_only=offline,
                torch_dtype=self._dtype,
            ).to(self._device).eval()
        except ModelUnavailableError:
            raise
        except Exception as ex:
            raise ModelUnavailableError(
                f"视觉模型权重加载失败（{detector_dir} / {segmenter_dir}；"
                "请先运行 download-models-demo.sh）"
            ) from ex

        self._post_kwargs = self._build_post_kwargs()
        self._warm_up()

    def _build_post_kwargs(self) -> dict:
        """按已安装 transformers 版本匹配 Grounding DINO 后处理签名。

        v5 使用 threshold + text_threshold；旧版使用 box_threshold + text_threshold。
        """

        parameters = inspect.signature(
            self._dino_processor.post_process_grounded_object_detection
        ).parameters
        kwargs: dict = {}
        if "box_threshold" in parameters:
            kwargs["box_threshold"] = self._settings.vision_box_threshold
        if "threshold" in parameters:
            kwargs["threshold"] = self._settings.vision_box_threshold
        if "text_threshold" in parameters:
            kwargs["text_threshold"] = self._settings.vision_text_threshold
        if not any(key.startswith("box_threshold") or key.startswith("threshold") for key in kwargs):
            raise ModelUnavailableError(
                "当前 transformers 版本的 Grounding DINO 后处理签名不受支持"
            )
        return kwargs

    def _warm_up(self) -> None:
        """启动时在 CUDA 上实际执行 DINO 与 SAM2 各一次，保证 READY 声明诚实。"""

        try:
            blank = Image.new("RGB", (256, 256), (120, 120, 120))
            self._infer(blank, 256)
            # 空白图可能没有 DINO 检测框，单独用一个 Bounding Box 执行一次 SAM2 前向。
            self._sam2_masks(blank, [[32.0, 32.0, 224.0, 224.0]])
        except ModelUnavailableError:
            raise
        except Exception as ex:
            raise ModelUnavailableError("视觉模型热身失败") from ex

    # ---------- 推理 ----------

    def _infer_with_oom_fallback(self, pil: Image.Image) -> list[DetectionItem]:
        last_error: Exception | None = None
        for long_side in self._resolution_ladder():
            try:
                return self._infer(pil, long_side)
            except ModelUnavailableError:
                raise
            except Exception as ex:
                if not self._is_oom(ex):
                    raise ModelUnavailableError(f"视觉模型推理失败：{ex}") from ex
                last_error = ex
                self.oom_fallback_count += 1
                if self._torch.cuda.is_available():
                    self._torch.cuda.empty_cache()
        raise ModelUnavailableError(
            f"视觉模型显存不足，最低分辨率 {self._resolution_ladder()[-1]} 仍失败"
        ) from last_error

    def _resolution_ladder(self) -> tuple[int, ...]:
        configured = int(self._settings.vision_max_long_side or 1280)
        candidates = [configured]
        candidates.extend(value for value in DEFAULT_RESOLUTION_LADDER if value < configured)
        result: list[int] = []
        for value in candidates:
            if value > 0 and value not in result:
                result.append(value)
        return tuple(result)

    def _infer(self, pil: Image.Image, long_side: int) -> list[DetectionItem]:
        resized = _resize_long_side(pil, long_side)
        width, height = resized.size
        prompt, phrases = self._build_prompt()

        inputs = self._dino_processor(
            images=resized, text=prompt, return_tensors="pt"
        ).to(self._device)
        # 检测器以 float32 加载，处理器返回 float32，精度天然一致。
        for key, value in inputs.items():
            if value.is_floating_point():
                inputs[key] = value.to(self._dino_dtype)
        with self._torch.inference_mode():
            outputs = self._dino(**inputs)

        results = self._dino_processor.post_process_grounded_object_detection(
            outputs,
            inputs.input_ids,
            target_sizes=[(height, width)],
            **self._post_kwargs,
        )

        boxes, scores, labels = _extract_top_detections(
            results, self._manifest.input.max_detections
        )
        mapped = self._map_to_classes(boxes, scores, labels, phrases)
        if not mapped:
            return []
        m_boxes = [item[0] for item in mapped]
        m_scores = [item[1] for item in mapped]
        m_codes = [item[2] for item in mapped]
        m_names = [item[3] for item in mapped]
        # 多个提示词映射同一类别；NMS 按类别代码合并重叠框，避免重复 Detection。
        m_boxes, m_scores, m_codes, m_names = _nms_per_class(
            m_boxes, m_scores, m_codes, m_names, iou_threshold=NMS_IOU_THRESHOLD
        )
        if not m_boxes:
            return []

        masks = self._sam2_masks(resized, m_boxes)
        return self._build_detections(m_boxes, m_scores, m_codes, m_names, width, height, masks)

    def _build_prompt(self) -> tuple[str, list[str]]:
        """构造 Grounding DINO query。

        标准格式：全部提示词转小写、strip、按序去重，以 ". " 连接并以 "." 结尾。
        例如：wall crack. concrete crack. surface crack. concrete spalling.
        """

        phrases: list[str] = []
        for item in self._manifest.classes:
            for prompt in item.prompts:
                cleaned = prompt.strip().lower()
                if cleaned and cleaned not in phrases:
                    phrases.append(cleaned)
        return ". ".join(phrases) + ".", phrases

    def _map_to_classes(
        self,
        boxes: list[list[float]],
        scores: list[float],
        labels: list,
        phrases: list[str],
    ) -> list[tuple[list[float], float, str, str]]:
        """把 label 映射为类别代码并丢弃无法映射/退化框。"""

        mapped: list[tuple[list[float], float, str, str]] = []
        for box, score, label in zip(boxes, scores, labels, strict=True):
            code, name = self._match_class(label, phrases)
            if code is None:
                continue
            x1, y1, x2, y2 = box
            if not (x2 > x1 and y2 > y1):
                continue
            mapped.append((box, score, code, name))
        return mapped

    def _build_detections(
        self,
        boxes: list[list[float]],
        scores: list[float],
        class_codes: list[str],
        class_names: list[str],
        width: int,
        height: int,
        masks: list[np.ndarray | None] | None = None,
    ) -> list[DetectionItem]:
        detections: list[DetectionItem] = []
        for index, (box, score, code, name) in enumerate(
            zip(boxes, scores, class_codes, class_names, strict=True)
        ):
            x1, y1, x2, y2 = box
            if not (x2 > x1 and y2 > y1):
                continue
            segmentation = None
            if masks is not None and index < len(masks) and masks[index] is not None:
                polygon = _mask_to_polygon(masks[index], width, height, POLYGON_MAX_POINTS)
                if polygon:
                    segmentation = SegmentationPolygon(points=polygon)
            detections.append(
                DetectionItem(
                    sequence=len(detections) + 1,
                    classCode=code,
                    className=name,
                    confidence=_clamp01(score),
                    boundingBox=BoundingBox(
                        x=_clamp01(x1 / width),
                        y=_clamp01(y1 / height),
                        width=_clamp01((x2 - x1) / width),
                        height=_clamp01((y2 - y1) / height),
                        coordinateType=CoordinateType.NORMALIZED_XYWH,
                    ),
                    segmentation=segmentation,
                )
            )
        return detections

    def _match_class(self, label, phrases: list[str]) -> tuple[str | None, str | None]:
        """把 Grounding DINO 返回的 label 映射回清单类别。

        label 可以是提示词短语索引（int）、短语文本（str，来自 text_labels 或旧版
        labels）。映射顺序：精确匹配任一 prompt → 子串包含匹配任一 prompt。
        """

        if isinstance(label, bool):
            return None, None
        if isinstance(label, int):
            phrase = phrases[label] if 0 <= label < len(phrases) else None
        elif isinstance(label, str):
            phrase = label.strip()
        else:
            phrase = None
        if not phrase:
            return None, None

        for item in self._manifest.classes:
            if any(phrase.lower() == p.lower() for p in item.prompts):
                return item.code, item.name
        for item in self._manifest.classes:
            if any(phrase.lower() in p.lower() or p.lower() in phrase.lower() for p in item.prompts):
                return item.code, item.name
        return None, None

    def _sam2_masks(
        self, pil: Image.Image, boxes: list[list[float]]
    ) -> list[np.ndarray | None]:
        """以 Grounding DINO 检测框作为 Bounding Box Prompt 生成掩膜。

        兼容性问题时回退到框中心点提示，并记录警告。返回每框最优掩膜（bool 数组）。
        """

        if not boxes:
            return []
        torch = self._torch
        inputs: dict
        try:
            input_boxes = [[[float(b[0]), float(b[1]), float(b[2]), float(b[3])] for b in boxes]]
            inputs = self._sam_processor(
                images=pil, input_boxes=input_boxes, return_tensors="pt"
            )
        except Exception:
            self.sam2_box_fallback = True
            centers = [
                ((box[0] + box[2]) / 2.0, (box[1] + box[3]) / 2.0) for box in boxes
            ]
            input_points = [[[[float(x), float(y)]] for (x, y) in centers]]
            input_labels = [[[1] for _ in centers]]
            inputs = self._sam_processor(
                images=pil,
                input_points=input_points,
                input_labels=input_labels,
                return_tensors="pt",
            )
        inputs = {key: self._move_input(value) for key, value in inputs.items()}
        with torch.inference_mode():
            outputs = self._sam(**inputs)

        pred = outputs.pred_masks.detach().cpu()
        iou_scores = outputs.iou_scores.detach().cpu()
        original_sizes = inputs.get("original_sizes")
        if original_sizes is not None:
            original_sizes = original_sizes.detach().cpu()
        try:
            processed = self._sam_processor.post_process_masks(pred, original_sizes)
        except TypeError:
            reshaped = inputs.get("reshaped_input_sizes")
            if reshaped is not None:
                reshaped = reshaped.detach().cpu()
            processed = self._sam_processor.post_process_masks(pred, original_sizes, reshaped)
        per_image = _as_list(processed)[0]

        masks: list[np.ndarray | None] = []
        for obj in range(pred.shape[1]):
            best = int(torch.argmax(iou_scores[0, obj]))
            try:
                raw = np.asarray(per_image[obj][best])
            except (IndexError, TypeError):
                masks.append(None)
                continue
            masks.append(raw > 0)
        return masks

    # ---------- 辅助 ----------

    def _move_input(self, value):
        """把处理器输出张量迁移到设备并对齐模型精度；非张量原样返回。"""

        if not hasattr(value, "to"):
            return value
        moved = value.to(self._device)
        if hasattr(moved, "is_floating_point") and moved.is_floating_point():
            return moved.to(self._dtype)
        return moved

    def _is_oom(self, ex: Exception) -> bool:
        oom_type = getattr(self._torch.cuda, "OutOfMemoryError", RuntimeError)
        if isinstance(ex, oom_type):
            return True
        message = str(ex).lower()
        return "out of memory" in message or "cuda out of memory" in message


def _resize_long_side(pil: Image.Image, long_side: int) -> Image.Image:
    width, height = pil.size
    longest = max(width, height)
    if longest <= long_side:
        return pil
    scale = long_side / longest
    new_size = (max(1, round(width * scale)), max(1, round(height * scale)))
    return pil.resize(new_size, Image.Resampling.BILINEAR)


def _extract_top_detections(
    results: list, maximum: int
) -> tuple[list[list[float]], list[float], list]:
    if not results:
        return [], [], []
    result = results[0]
    boxes = _to_float_boxes(result.get("boxes", []))
    scores = _to_float_list(result.get("scores", []))
    # 无检测时 v5 仍可能返回 1 个空字符串 labels，必须先按 boxes 判断并提前返回。
    if not boxes:
        return [], [], []
    # 优先读取 text_labels（部分版本返回短语文本），不存在再兼容 labels（v5 返回整数索引）。
    # 若长度与 boxes 不一致（版本差异），回退 labels；仍不一致则用范围索引兜底。
    labels = _to_python_list(result.get("text_labels", result.get("labels", [])))
    if len(labels) != len(boxes):
        labels = _to_python_list(result.get("labels", []))
    if len(labels) != len(boxes):
        labels = list(range(len(boxes)))

    ordered = sorted(
        zip(boxes, scores, labels, strict=True),
        key=lambda item: item[1],
        reverse=True,
    )[:maximum]
    if not ordered:
        return [], [], []
    out_boxes, out_scores, out_labels = zip(*ordered)
    return list(out_boxes), list(out_scores), list(out_labels)


def _nms_per_class(
    boxes: list[list[float]],
    scores: list[float],
    class_codes: list[str],
    class_names: list[str],
    iou_threshold: float,
) -> tuple[list[list[float]], list[float], list[str], list[str]]:
    """按类别代码执行 IoU 非极大值抑制。

    多个提示词映射到同一类别代码后，同一病害的重复框在此合并，避免
    wall crack / concrete crack / surface crack 对同一裂缝生成三条 Detection。
    """

    if not boxes:
        return boxes, scores, class_codes, class_names
    kept: list[int] = []
    order = sorted(range(len(scores)), key=lambda i: scores[i], reverse=True)
    for index in order:
        box = boxes[index]
        code = _label_key(class_codes[index])
        suppress = False
        for other in kept:
            if _label_key(class_codes[other]) != code:
                continue
            if _iou(box, boxes[other]) >= iou_threshold:
                suppress = True
                break
        if not suppress:
            kept.append(index)
    kept.sort(key=lambda i: scores[i], reverse=True)
    return (
        [boxes[i] for i in kept],
        [scores[i] for i in kept],
        [class_codes[i] for i in kept],
        [class_names[i] for i in kept],
    )


def _label_key(code) -> str:
    """把类别代码统一为比较键。"""

    if isinstance(code, str):
        return code.strip().lower()
    return f"idx:{code}"


def _iou(box_a: list[float], box_b: list[float]) -> float:
    a_x1, a_y1, a_x2, a_y2 = box_a
    b_x1, b_y1, b_x2, b_y2 = box_b
    inter_x1 = max(a_x1, b_x1)
    inter_y1 = max(a_y1, b_y1)
    inter_x2 = min(a_x2, b_x2)
    inter_y2 = min(a_y2, b_y2)
    inter = max(0.0, inter_x2 - inter_x1) * max(0.0, inter_y2 - inter_y1)
    area_a = max(0.0, a_x2 - a_x1) * max(0.0, a_y2 - a_y1)
    area_b = max(0.0, b_x2 - b_x1) * max(0.0, b_y2 - b_y1)
    union = area_a + area_b - inter
    if union <= 0:
        return 0.0
    return inter / union


def _mask_to_polygon(
    mask: np.ndarray,
    width: int,
    height: int,
    max_points: int = POLYGON_MAX_POINTS,
) -> list[list[float]] | None:
    """把掩膜简化为归一化轮廓多边形；空掩膜返回 None。"""

    import cv2

    if mask is None or not mask.any():
        return None
    mask_uint8 = (mask > 0).astype(np.uint8) * 255
    contours, _ = cv2.findContours(mask_uint8, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None
    contour = max(contours, key=cv2.contourArea)
    perimeter = cv2.arcLength(contour, True)
    epsilon = 0.01 * perimeter if perimeter > 0 else 1.0
    approx = cv2.approxPolyDP(contour, epsilon, True)
    points = approx.reshape(-1, 2).astype(np.float32)
    if len(points) > max_points:
        step = len(points) / max_points
        points = points[[int(i * step) for i in range(max_points)]]
    if width > 0 and height > 0:
        points[:, 0] = np.clip(points[:, 0] / width, 0.0, 1.0)
        points[:, 1] = np.clip(points[:, 1] / height, 0.0, 1.0)
    return [[round(float(x), 4), round(float(y), 4)] for x, y in points]


def _to_float_boxes(value) -> list[list[float]]:
    items = _to_python_list(value)
    return [[float(coordinate) for coordinate in box] for box in items]


def _to_float_list(value) -> list[float]:
    return [float(item) for item in _to_python_list(value)]


def _to_python_list(value) -> list:
    if value is None:
        return []
    if hasattr(value, "tolist"):
        return value.tolist()
    return list(value)


def _as_list(value):
    if isinstance(value, list):
        return value
    if hasattr(value, "tolist"):
        return value.tolist()
    return list(value)


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))
