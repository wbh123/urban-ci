"""GroundedSam2TinyAdapter 逻辑测试（monkeypatch torch/transformers，无需真实权重/CUDA）。"""

from __future__ import annotations

import sys
import types
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from app.adapters.grounded_sam2 import (
    GroundedSam2TinyAdapter,
    _extract_top_detections,
    _mask_to_polygon,
    _nms_per_class,
)
from app.errors import ModelUnavailableError
from app.model_digest import combine_digests, dir_digest
from app.model_manifest import ModelSource
from app.vision_manifest import (
    ZeroShotCheckpoint,
    ZeroShotClass,
    ZeroShotInput,
    ZeroShotModelManifest,
)

DETECTOR_SHA = "a2bb814dd30d776dcf7e30523b00659f4f141c71"
SEGMENTER_SHA = "de431c4043854a71d8101e17995dfe596bf101a5"


def _manifest(detector_dir=Path("d"), segmenter_dir=Path("s"), weight_sha256="a" * 64) -> ZeroShotModelManifest:
    return ZeroShotModelManifest(
        schema_version=1,
        model_id="AI-VISION-LOCAL-001",
        model_name="Test Vision",
        version="1.0.0",
        status="APPROVED",
        identity_verified=True,
        task="ZERO_SHOT_VISUAL_DEFECT",
        adapter="grounded-sam2-tiny-v1",
        weight_sha256=weight_sha256,
        source=ModelSource(
            type="ZERO_SHOT_OPEN_WEIGHTS",
            repository="modelscope",
            revision=f"{DETECTOR_SHA}/{SEGMENTER_SHA}",
            license="Apache-2.0",
        ),
        classes=(
            ZeroShotClass(code="CRACK", name="疑似裂缝", prompts=("wall crack", "concrete crack")),
            ZeroShotClass(code="SPALLING", name="疑似剥落", prompts=("concrete spalling",)),
        ),
        checkpoint=ZeroShotCheckpoint(
            detector_repository="IDEA-Research/grounding-dino-tiny",
            segmenter_repository="facebook/sam2.1-hiera-tiny",
            detector_revision=DETECTOR_SHA,
            segmenter_revision=SEGMENTER_SHA,
            detector_dir=Path(detector_dir),
            segmenter_dir=Path(segmenter_dir),
            sha256=weight_sha256,
            size_bytes=1024,
        ),
        input=ZeroShotInput(
            max_long_side=1280,
            box_threshold=0.25,
            text_threshold=0.25,
            max_detections=10,
        ),
        license="Apache-2.0",
        approved_by="T-REVIEWER",
        approved_at="2026-08-11T00:00:00Z",
    )


def _settings(**overrides):
    defaults = {
        "vision_device": "cuda",
        "vision_dtype": "float16",
        "vision_max_long_side": 1280,
        "vision_box_threshold": 0.25,
        "vision_text_threshold": 0.25,
        "vision_max_detections": 10,
        "vision_offline": True,
        "vision_hf_home": "",
        "vision_sha_mode": "STRICT",
    }
    defaults.update(overrides)
    return types.SimpleNamespace(**defaults)


def _bare_adapter(manifest=None, settings=None) -> GroundedSam2TinyAdapter:
    adapter = object.__new__(GroundedSam2TinyAdapter)
    adapter._manifest = manifest or _manifest()
    adapter._settings = settings or _settings()
    return adapter


def _fake_torch(cuda_available: bool = True, oom_type=RuntimeError):
    import contextlib

    cuda = types.SimpleNamespace(
        is_available=lambda: cuda_available,
        empty_cache=lambda: None,
        OutOfMemoryError=oom_type,
    )
    return types.SimpleNamespace(
        float16="fp16",
        float32="fp32",
        cuda=cuda,
        argmax=lambda x: 0,
        inference_mode=contextlib.nullcontext,
    )


class _FakeTensor:
    def __init__(self, array):
        self._a = np.asarray(array)

    @property
    def shape(self):
        return self._a.shape

    def __getitem__(self, key):
        return self._a[key]

    def detach(self):
        return self

    def cpu(self):
        return self

    def __array__(self):
        return self._a


class _FakeOOM(RuntimeError):
    pass


# ---------- 类别映射（多提示词） ----------

def test_match_class_by_integer_index():
    adapter = _bare_adapter()
    phrases = ["wall crack", "concrete crack", "concrete spalling"]
    assert adapter._match_class(0, phrases) == ("CRACK", "疑似裂缝")
    assert adapter._match_class(2, phrases) == ("SPALLING", "疑似剥落")
    assert adapter._match_class(99, phrases) == (None, None)


def test_match_class_by_any_prompt():
    adapter = _bare_adapter()
    phrases = ["wall crack", "concrete crack", "concrete spalling"]
    # “concrete crack” 是 CRACK 的第二个 prompt，应映射到 CRACK。
    assert adapter._match_class("concrete crack", phrases) == ("CRACK", "疑似裂缝")
    assert adapter._match_class("concrete spalling", phrases) == ("SPALLING", "疑似剥落")
    assert adapter._match_class("unrelated", phrases) == (None, None)


def test_match_class_rejects_boolean():
    adapter = _bare_adapter()
    assert adapter._match_class(True, ["wall crack"]) == (None, None)


def test_build_prompt_flattens_all_prompts():
    adapter = _bare_adapter()
    prompt, phrases = adapter._build_prompt()
    assert prompt == "wall crack. concrete crack. concrete spalling."
    assert phrases == ["wall crack", "concrete crack", "concrete spalling"]


def test_build_prompt_ends_with_period():
    adapter = _bare_adapter()
    prompt, _ = adapter._build_prompt()
    assert prompt.endswith(".")


def test_build_prompt_no_space_dot_space():
    adapter = _bare_adapter()
    prompt, _ = adapter._build_prompt()
    assert " . " not in prompt


def test_build_prompt_dedupes_preserving_order():
    manifest = _manifest()
    manifest = ZeroShotModelManifest(
        schema_version=1,
        model_id=manifest.model_id,
        model_name=manifest.model_name,
        version=manifest.version,
        status=manifest.status,
        identity_verified=manifest.identity_verified,
        task=manifest.task,
        adapter=manifest.adapter,
        weight_sha256=manifest.weight_sha256,
        source=manifest.source,
        classes=(
            ZeroShotClass(code="CRACK", name="疑似裂缝", prompts=("Wall Crack", "wall crack", "concrete crack")),
            ZeroShotClass(code="SPALLING", name="疑似剥落", prompts=("Concrete Spalling",)),
        ),
        checkpoint=manifest.checkpoint,
        input=manifest.input,
        license=manifest.license,
        approved_by=manifest.approved_by,
        approved_at=manifest.approved_at,
    )
    adapter = _bare_adapter(manifest=manifest)
    prompt, phrases = adapter._build_prompt()
    # 大小写不同视为重复，只保留首个。
    assert phrases == ["wall crack", "concrete crack", "concrete spalling"]
    assert prompt == "wall crack. concrete crack. concrete spalling."


def test_build_prompt_lowercases_all():
    manifest = _manifest()
    manifest = ZeroShotModelManifest(
        schema_version=1,
        model_id=manifest.model_id,
        model_name=manifest.model_name,
        version=manifest.version,
        status=manifest.status,
        identity_verified=manifest.identity_verified,
        task=manifest.task,
        adapter=manifest.adapter,
        weight_sha256=manifest.weight_sha256,
        source=manifest.source,
        classes=(ZeroShotClass(code="CRACK", name="疑似裂缝", prompts=("WALL CRACK",)),),
        checkpoint=manifest.checkpoint,
        input=manifest.input,
        license=manifest.license,
        approved_by=manifest.approved_by,
        approved_at=manifest.approved_at,
    )
    adapter = _bare_adapter(manifest=manifest)
    prompt, _ = adapter._build_prompt()
    assert prompt == "wall crack."
    assert prompt.islower()


# ---------- 标准化 Detection + segmentation ----------

def test_build_detections_normalizes_boxes_with_segmentation():
    adapter = _bare_adapter()
    mask = np.zeros((100, 100), dtype=bool)
    mask[30:60, 20:70] = True
    detections = adapter._build_detections(
        boxes=[[10.0, 20.0, 60.0, 70.0]],
        scores=[0.9],
        class_codes=["CRACK"],
        class_names=["疑似裂缝"],
        width=100,
        height=100,
        masks=[mask],
    )
    assert len(detections) == 1
    item = detections[0]
    assert item.classCode == "CRACK"
    assert item.confidence == 0.9
    assert item.boundingBox.x == 0.1
    assert item.boundingBox.y == 0.2
    assert item.boundingBox.width == 0.5
    assert item.boundingBox.height == 0.5
    assert item.segmentation is not None
    assert item.segmentation.type.value == "POLYGON"
    assert all(0.0 <= p[0] <= 1.0 and 0.0 <= p[1] <= 1.0 for p in item.segmentation.points)


def test_map_to_classes_skips_unknown_label():
    adapter = _bare_adapter()
    mapped = adapter._map_to_classes(
        boxes=[[10.0, 20.0, 60.0, 70.0]],
        scores=[0.9],
        labels=[7],
        phrases=["wall crack", "concrete crack", "concrete spalling"],
    )
    assert mapped == []


def test_map_to_classes_merges_multi_prompts_to_same_class():
    adapter = _bare_adapter()
    # “wall crack” 与 “concrete crack” 都映射到 CRACK。
    mapped = adapter._map_to_classes(
        boxes=[[10.0, 20.0, 60.0, 70.0], [80.0, 20.0, 120.0, 70.0]],
        scores=[0.9, 0.8],
        labels=["wall crack", "concrete crack"],
        phrases=["wall crack", "concrete crack", "concrete spalling"],
    )
    assert [item[2] for item in mapped] == ["CRACK", "CRACK"]


def test_build_detections_skips_degenerate_box():
    adapter = _bare_adapter()
    detections = adapter._build_detections(
        boxes=[[50.0, 50.0, 50.0, 50.0]],
        scores=[0.9],
        class_codes=["CRACK"],
        class_names=["疑似裂缝"],
        width=100,
        height=100,
    )
    assert detections == []


# ---------- mask → polygon ----------

def test_mask_to_polygon_normalizes_and_caps_points():
    mask = np.zeros((200, 200), dtype=bool)
    mask[40:160, 50:150] = True
    polygon = _mask_to_polygon(mask, 200, 200, max_points=40)
    assert polygon is not None
    assert len(polygon) <= 40
    assert all(len(point) == 2 and 0.0 <= point[0] <= 1.0 and 0.0 <= point[1] <= 1.0 for point in polygon)


def test_mask_to_polygon_empty_returns_none():
    mask = np.zeros((100, 100), dtype=bool)
    assert _mask_to_polygon(mask, 100, 100) is None


# ---------- 解析：text_labels 优先 ----------

def test_extract_top_detections_prefers_text_labels():
    results = [
        {
            "boxes": [[0, 0, 1, 1], [2, 2, 3, 3]],
            "scores": [0.9, 0.5],
            "labels": [3, 7],
            "text_labels": ["concrete crack", "unrelated"],
        }
    ]
    boxes, scores, labels = _extract_top_detections(results, 10)
    assert labels == ["concrete crack", "unrelated"]


def test_extract_top_detections_falls_back_to_labels():
    results = [{"boxes": [[0, 0, 1, 1]], "scores": [0.9], "labels": [0]}]
    boxes, scores, labels = _extract_top_detections(results, 10)
    assert labels == [0]


def test_extract_top_detections_falls_back_when_text_labels_length_mismatch():
    results = [
        {
            "boxes": [[0, 0, 1, 1]],
            "scores": [0.9],
            "labels": [2],
            "text_labels": ["concrete crack", "wall crack", "spalling"],  # 长度不匹配
        }
    ]
    boxes, scores, labels = _extract_top_detections(results, 10)
    assert labels == [2]


def test_extract_top_detections_empty_boxes_returns_empty():
    # v5 无检测时可能返回 1 个空字符串 labels；必须提前返回空。
    results = [{"boxes": [], "scores": [], "labels": [""], "text_labels": [""]}]
    boxes, scores, labels = _extract_top_detections(results, 10)
    assert boxes == [] and scores == [] and labels == []


def test_extract_top_detections_sorts_by_score():
    results = [
        {
            "boxes": [[0, 0, 1, 1], [2, 2, 3, 3], [4, 4, 5, 5]],
            "scores": [0.5, 0.9, 0.1],
            "labels": [0, 1, 2],
        }
    ]
    boxes, scores, labels = _extract_top_detections(results, 2)
    assert scores == [0.9, 0.5]
    assert len(boxes) == 2
    assert labels == [1, 0]


# ---------- NMS / IoU 合并 ----------

def test_nms_merges_same_class_overlapping_boxes():
    boxes = [[0, 0, 100, 100], [10, 10, 110, 110], [200, 200, 300, 300]]
    scores = [0.9, 0.8, 0.7]
    codes = ["CRACK", "CRACK", "SPALLING"]
    names = ["疑似裂缝", "疑似裂缝", "疑似剥落"]
    kept_boxes, kept_scores, kept_codes, kept_names = _nms_per_class(
        boxes, scores, codes, names, iou_threshold=0.5
    )
    # 前两个同属 CRACK 且高度重叠 → 只保留高分的那个；第三个不同类别保留。
    assert len(kept_boxes) == 2
    assert kept_scores == [0.9, 0.7]
    assert kept_codes == ["CRACK", "SPALLING"]


def test_nms_keeps_different_classes_even_when_overlapping():
    boxes = [[0, 0, 100, 100], [0, 0, 100, 100]]
    scores = [0.9, 0.8]
    codes = ["CRACK", "SPALLING"]
    names = ["疑似裂缝", "疑似剥落"]
    kept_boxes, kept_scores, kept_codes, kept_names = _nms_per_class(
        boxes, scores, codes, names, iou_threshold=0.5
    )
    assert len(kept_boxes) == 2


# ---------- SHA 门禁 ----------

def _write_weight_dir(path: Path, marker: str) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    (path / "model.safetensors").write_bytes(marker.encode())
    return path


def test_strict_sha_gate_rejects_mismatch(tmp_path):
    detector_dir = _write_weight_dir(tmp_path / "detector", "detector-weight")
    segmenter_dir = _write_weight_dir(tmp_path / "segmenter", "segmenter-weight")
    detector_sha, _ = dir_digest(detector_dir)
    segmenter_sha, _ = dir_digest(segmenter_dir)
    wrong_sha = "b" * 64
    manifest = _manifest(detector_dir, segmenter_dir, weight_sha256=wrong_sha)
    adapter = _bare_adapter(manifest=manifest, settings=_settings(vision_sha_mode="STRICT"))
    with pytest.raises(ModelUnavailableError, match="摘要与 manifest 不一致"):
        adapter._verify_weight_sha()


def test_strict_sha_gate_passes_when_match(tmp_path):
    detector_dir = _write_weight_dir(tmp_path / "detector", "detector-weight")
    segmenter_dir = _write_weight_dir(tmp_path / "segmenter", "segmenter-weight")
    detector_sha, _ = dir_digest(detector_dir)
    segmenter_sha, _ = dir_digest(segmenter_dir)
    correct_sha = combine_digests(detector_sha, segmenter_sha)
    manifest = _manifest(detector_dir, segmenter_dir, weight_sha256=correct_sha)
    adapter = _bare_adapter(manifest=manifest, settings=_settings(vision_sha_mode="STRICT"))
    adapter._verify_weight_sha()  # 不抛异常即通过


def test_fast_sha_mode_skips_digest_check(tmp_path):
    detector_dir = _write_weight_dir(tmp_path / "detector", "detector-weight")
    segmenter_dir = _write_weight_dir(tmp_path / "segmenter", "segmenter-weight")
    manifest = _manifest(detector_dir, segmenter_dir, weight_sha256="b" * 64)
    adapter = _bare_adapter(manifest=manifest, settings=_settings(vision_sha_mode="FAST"))
    adapter._verify_weight_sha()  # FAST 不重算摘要，不抛异常


def test_init_rejects_candidate_manifest():
    manifest = _manifest()
    manifest = ZeroShotModelManifest(
        schema_version=1,
        model_id="AI-VISION-LOCAL-001",
        model_name="Test Vision",
        version="1.0.0",
        status="CANDIDATE",
        identity_verified=False,
        task="ZERO_SHOT_VISUAL_DEFECT",
        adapter="grounded-sam2-tiny-v1",
        weight_sha256=manifest.weight_sha256,
        source=manifest.source,
        classes=manifest.classes,
        checkpoint=manifest.checkpoint,
        input=manifest.input,
        license=manifest.license,
        approved_by="",
        approved_at="",
    )
    with pytest.raises(ModelUnavailableError, match="未批准"):
        GroundedSam2TinyAdapter(manifest, _settings())


# ---------- SAM2 box prompt ----------

class _FakeSamProcessor:
    def __init__(self, n_objects=2):
        self.n_objects = n_objects
        self.last_call = None
        self._post_processed = [np.zeros((n_objects, 3, 8, 8), dtype=bool)]

    def __call__(self, images=None, input_boxes=None, input_points=None, input_labels=None, return_tensors=None):
        self.last_call = {
            "input_boxes": input_boxes,
            "input_points": input_points,
            "input_labels": input_labels,
        }
        return {
            "original_sizes": _FakeTensor([[8, 8]]),
            "reshaped_input_sizes": _FakeTensor([[8, 8]]),
        }

    def post_process_masks(self, masks, original_sizes, *rest):
        return self._post_processed


class _FakeSamModel:
    def __init__(self, n_objects=2):
        self.n_objects = n_objects

    def __call__(self, **inputs):
        return types.SimpleNamespace(
            pred_masks=_FakeTensor(np.zeros((1, self.n_objects, 3, 8, 8), dtype=bool)),
            iou_scores=_FakeTensor(np.zeros((1, self.n_objects, 3))),
        )


def test_sam2_uses_box_prompt():
    adapter = _bare_adapter()
    adapter._torch = _fake_torch(cuda_available=True)
    adapter._device = "cuda"
    adapter._dtype = "fp16"
    adapter._sam_processor = _FakeSamProcessor(n_objects=2)
    adapter._sam = _FakeSamModel(n_objects=2)
    boxes = [[10.0, 10.0, 50.0, 50.0], [60.0, 60.0, 90.0, 90.0]]
    masks = adapter._sam2_masks(Image.new("RGB", (64, 64)), boxes)
    assert adapter._sam_processor.last_call["input_boxes"] == [[[10.0, 10.0, 50.0, 50.0], [60.0, 60.0, 90.0, 90.0]]]
    assert adapter._sam_processor.last_call["input_points"] is None
    assert len(masks) == 2


# ---------- 分辨率与 OOM ----------

def test_resolution_ladder():
    assert _bare_adapter(settings=_settings(vision_max_long_side=1280))._resolution_ladder() == (1280, 1024, 896)
    assert _bare_adapter(settings=_settings(vision_max_long_side=896))._resolution_ladder() == (896,)
    assert _bare_adapter(settings=_settings(vision_max_long_side=1600))._resolution_ladder() == (1600, 1280, 1024, 896)


class _StubInferAdapter(GroundedSam2TinyAdapter):
    def __init__(self, settings, behaviors):
        self._manifest = _manifest()
        self._settings = settings
        self._torch = _fake_torch(cuda_available=True, oom_type=_FakeOOM)
        self.oom_fallback_count = 0
        self._behaviors = list(behaviors)
        self.calls: list[int] = []

    def _infer(self, pil, long_side):
        self.calls.append(long_side)
        behavior = self._behaviors.pop(0)
        if isinstance(behavior, Exception):
            raise behavior
        return behavior


def test_oom_falls_back_to_lower_resolution():
    adapter = _StubInferAdapter(_settings(), [_FakeOOM(), ["candidate"]])
    result = adapter._infer_with_oom_fallback(Image.new("RGB", (32, 32)))
    assert result == ["candidate"]
    assert adapter.calls == [1280, 1024]
    assert adapter.oom_fallback_count == 1


def test_oom_exhausts_all_resolutions():
    adapter = _StubInferAdapter(_settings(), [_FakeOOM(), _FakeOOM(), _FakeOOM()])
    with pytest.raises(ModelUnavailableError, match="显存不足"):
        adapter._infer_with_oom_fallback(Image.new("RGB", (32, 32)))
    assert adapter.calls == [1280, 1024, 896]
    assert adapter.oom_fallback_count == 3


def test_non_oom_error_is_not_retried():
    adapter = _StubInferAdapter(_settings(), [ValueError("boom")])
    with pytest.raises(ModelUnavailableError, match="推理失败"):
        adapter._infer_with_oom_fallback(Image.new("RGB", (32, 32)))
    assert adapter.calls == [1280]


# ---------- 运行时门禁 ----------

def test_runtime_requires_torch(monkeypatch):
    monkeypatch.setitem(sys.modules, "torch", None)
    adapter = _bare_adapter(settings=_settings())
    with pytest.raises(ModelUnavailableError, match="未安装 torch"):
        adapter._resolve_runtime()


def test_runtime_requires_cuda(monkeypatch):
    monkeypatch.setitem(sys.modules, "torch", _fake_torch(cuda_available=False))
    adapter = _bare_adapter(settings=_settings())
    with pytest.raises(ModelUnavailableError, match="CUDA 不可用"):
        adapter._resolve_runtime()


def test_runtime_rejects_cpu_device(monkeypatch):
    monkeypatch.setitem(sys.modules, "torch", _fake_torch(cuda_available=True))
    adapter = _bare_adapter(settings=_settings(vision_device="cpu"))
    with pytest.raises(ModelUnavailableError, match="只允许 CUDA"):
        adapter._resolve_runtime()


def test_runtime_rejects_unknown_dtype(monkeypatch):
    monkeypatch.setitem(sys.modules, "torch", _fake_torch(cuda_available=True))
    adapter = _bare_adapter(settings=_settings(vision_dtype="int8"))
    with pytest.raises(ModelUnavailableError, match="精度"):
        adapter._resolve_runtime()


def test_load_requires_transformers(monkeypatch):
    monkeypatch.setitem(sys.modules, "torch", _fake_torch(cuda_available=True))
    monkeypatch.setitem(sys.modules, "transformers", None)
    adapter = _bare_adapter(settings=_settings())
    with pytest.raises(ModelUnavailableError, match="transformers"):
        adapter._load_models()
