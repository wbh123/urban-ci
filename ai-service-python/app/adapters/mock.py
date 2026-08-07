"""确定性 MOCK 推理适配器。"""

from __future__ import annotations

import hashlib

from ..image import DecodedImage
from ..schemas import Applicability, BoundingBox, CoordinateType, DetectionItem, ModelBrief

MOCK_CLASS_CODE = "CRACK"
MOCK_CLASS_NAME = "裂缝"
MOCK_WARNINGS = ["模拟结果仅用于业务链路验证"]


def _seed_bytes(image: DecodedImage, model_version: str) -> list[int]:
    digest = hashlib.sha256(image.bytes_ + model_version.encode("utf-8")).hexdigest()
    return [int(digest[i : i + 2], 16) for i in range(0, len(digest), 2)]


def _detection(seed: list[int], offset: int, sequence: int) -> DetectionItem:
    return DetectionItem(
        sequence=sequence,
        classCode=MOCK_CLASS_CODE,
        className=MOCK_CLASS_NAME,
        confidence=round(((seed[offset + 4] % 50) + 50) / 100.0, 5),
        boundingBox=BoundingBox(
            x=round((seed[offset] % 50) / 100.0, 5),
            y=round((seed[offset + 1] % 50) / 100.0, 5),
            width=round(((seed[offset + 2] % 30) + 10) / 100.0, 5),
            height=round(((seed[offset + 3] % 30) + 10) / 100.0, 5),
            coordinateType=CoordinateType.NORMALIZED_XYWH,
        ),
    )


class DeterministicMockAdapter:
    def __init__(self, model_id: str, model_name: str, model_version: str) -> None:
        self._model_id = model_id
        self._model_name = model_name
        self._model_version = model_version

    def model_info(self) -> ModelBrief:
        return ModelBrief(
            modelId=self._model_id,
            modelName=self._model_name,
            version=self._model_version,
        )

    def execution_provider(self) -> str:
        """模拟模型不占用硬件，只用于明确的业务链路测试。"""

        return "DETERMINISTIC_MOCK"

    def predict(self, image: DecodedImage) -> tuple[Applicability, list[DetectionItem]]:
        if image.applicability == Applicability.LOW_QUALITY:
            return Applicability.LOW_QUALITY, []
        seed = _seed_bytes(image, self._model_version)
        branch = seed[0] % 4
        if branch == 0:
            return Applicability.APPLICABLE, [_detection(seed, 1, 1)]
        if branch == 1:
            return Applicability.APPLICABLE, [
                _detection(seed, 1, 1),
                _detection(seed, 7, 2),
            ]
        if branch == 2:
            return Applicability.NO_DEFECT_FOUND, []
        return Applicability.NOT_APPLICABLE, []
