"""Spring Boot 与 FastAPI 之间的稳定推理契约。"""

from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field, model_validator


class InferenceMode(str, Enum):
    MOCK = "MOCK"
    REAL = "REAL"


class InferenceProfile(str, Enum):
    """视觉推理档位；FAST/PRECISION 保持兼容，ACCURACY 受 APPROVED 门禁约束。"""

    FAST = "FAST"
    PRECISION = "PRECISION"
    ACCURACY = "ACCURACY"


class TrustLevel(str, Enum):
    """视觉候选可信度，仅用于人工复核，不等同于房屋安全风险等级。"""

    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


class InferenceStatus(str, Enum):
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    REJECTED = "REJECTED"


class QualityStatus(str, Enum):
    ACCEPTABLE = "ACCEPTABLE"
    LOW_QUALITY = "LOW_QUALITY"


class Applicability(str, Enum):
    APPLICABLE = "APPLICABLE"
    NO_DEFECT_FOUND = "NO_DEFECT_FOUND"
    LOW_QUALITY = "LOW_QUALITY"
    NOT_APPLICABLE = "NOT_APPLICABLE"
    INVALID_IMAGE = "INVALID_IMAGE"


class ImageApplicabilityDecision(str, Enum):
    """本地图片语义适用性门禁的稳定三态决策。"""

    APPLICABLE = "APPLICABLE"
    NOT_APPLICABLE = "NOT_APPLICABLE"
    UNCERTAIN = "UNCERTAIN"


class CoordinateType(str, Enum):
    NORMALIZED_XYWH = "NORMALIZED_XYWH"


class SegmentationType(str, Enum):
    POLYGON = "POLYGON"


class SegmentationPolygon(BaseModel):
    """SAM2 掩膜简化后的轮廓多边形；坐标为 0~1 归一化。"""

    type: SegmentationType = SegmentationType.POLYGON
    points: list[list[float]] = Field(
        ...,
        description="轮廓点序列 [[x, y], ...]，坐标归一化 0~1",
    )


class InferenceMetadata(BaseModel):
    requestId: str = Field(..., min_length=1)
    mode: InferenceMode = Field(default=InferenceMode.MOCK)
    assetId: Optional[str] = None
    filename: Optional[str] = None
    contentType: Optional[str] = None
    sha256: Optional[str] = None
    requestedModelId: Optional[str] = None
    inferenceProfile: InferenceProfile = Field(
        default=InferenceProfile.FAST,
        description=(
            "FAST 保持现有路径；PRECISION 启用多尺度与局部复核；"
            "ACCURACY 为多模型精度档位且必须通过 APPROVED 运行时准入"
        ),
    )


class ModelBrief(BaseModel):
    modelId: str
    modelName: str
    version: str


class BoundingBox(BaseModel):
    x: float = Field(ge=0.0, le=1.0)
    y: float = Field(ge=0.0, le=1.0)
    width: float = Field(gt=0.0, le=1.0)
    height: float = Field(gt=0.0, le=1.0)
    coordinateType: CoordinateType = CoordinateType.NORMALIZED_XYWH


class DetectionItem(BaseModel):
    sequence: int = Field(ge=1)
    classCode: str
    className: str
    confidence: float = Field(ge=0.0, le=1.0)
    boundingBox: BoundingBox
    segmentation: Optional[SegmentationPolygon] = Field(
        default=None,
        description="可选分割轮廓；旧调用方忽略该字段仍兼容",
    )
    trustLevel: Optional[TrustLevel] = Field(
        default=None,
        description="PRECISION/ACCURACY 模式可选模型候选可信度，不代表房屋风险等级",
    )
    trustReasons: list[str] = Field(
        default_factory=list,
        description="PRECISION/ACCURACY 可信度的可解释依据",
    )
    diagnostics: dict[str, object] = Field(
        default_factory=dict,
        description="可选几何/多尺度诊断信息；旧调用方可忽略",
    )


class ImageInfo(BaseModel):
    width: int = Field(ge=1)
    height: int = Field(ge=1)
    qualityStatus: QualityStatus
    applicability: Applicability


class DetectionSummary(BaseModel):
    detectionCount: int = Field(ge=0)
    classCounts: dict[str, int] = Field(default_factory=dict)


class InferenceResponse(BaseModel):
    requestId: str
    status: InferenceStatus
    mode: InferenceMode
    model: ModelBrief
    image: ImageInfo
    detections: list[DetectionItem] = Field(default_factory=list)
    summary: DetectionSummary
    durationMs: int = Field(ge=0)
    warnings: list[str] = Field(default_factory=list)


class InferenceErrorDetail(BaseModel):
    requestId: str
    status: InferenceStatus
    errorCode: str
    errorMessage: str
    mode: InferenceMode
    model: Optional[ModelBrief] = None
    warnings: list[str] = Field(default_factory=list)


class ModelInfo(BaseModel):
    modelId: str
    modelName: str
    version: str
    mode: InferenceMode
    status: str
    supportedDefects: list[str] = Field(default_factory=list)
    license: str
    weightSha256: Optional[str] = None
    ready: bool = True
    executionProvider: Optional[str] = None
    deviceId: Optional[int] = None
    task: Optional[str] = None
    adapter: Optional[str] = None


class ModelCatalogResponse(BaseModel):
    runtime: str = "CUDA_ONLY"
    defaultRealModelId: Optional[str] = None
    models: list[ModelInfo] = Field(default_factory=list)


class RuntimeReadiness(BaseModel):
    status: str = "READY"
    runtime: str = "CUDA_ONLY"
    cudaDeviceId: int = Field(ge=0)
    realModelCount: int = Field(ge=0)
    defaultRealModelId: Optional[str] = None


class ImageQualityResponse(BaseModel):
    """本地确定性图片质量分析结果。"""

    requestId: str
    modelId: str
    modelVersion: str
    status: str = "SUCCEEDED"
    decodeStatus: str = "DECODED"
    contentType: str
    width: int = Field(ge=1)
    height: int = Field(ge=1)
    brightness: float = Field(ge=0.0, le=1.0)
    contrast: float = Field(ge=0.0, le=1.0)
    sharpness: float = Field(ge=0.0)
    blank: bool
    underexposed: bool
    overexposed: bool
    blurDetected: bool
    lowResolution: bool
    lowQuality: bool
    reshootRecommended: bool
    reasons: list[str] = Field(default_factory=list)


class ImageQualityErrorResponse(BaseModel):
    """图片质量接口稳定错误结构。"""

    requestId: str
    status: str = "REJECTED"
    errorCode: str
    errorMessage: str


class ImageApplicabilityResponse(BaseModel):
    """本地语义适用性分类结果；只有 NOT_APPLICABLE 才允许阻断 Dify。"""

    requestId: str
    modelId: str
    modelVersion: str
    status: str = "SUCCEEDED"
    decision: ImageApplicabilityDecision
    confidence: float = Field(ge=0.0, le=1.0)
    scores: dict[str, float] = Field(default_factory=dict)
    allowDify: bool
    reason: str

    @model_validator(mode="after")
    def validate_dify_gate(self) -> "ImageApplicabilityResponse":
        if self.decision == ImageApplicabilityDecision.NOT_APPLICABLE and self.allowDify:
            raise ValueError("NOT_APPLICABLE 不得继续调用 Dify")
        if self.decision != ImageApplicabilityDecision.NOT_APPLICABLE and not self.allowDify:
            raise ValueError("APPLICABLE/UNCERTAIN 必须继续调用 Dify")
        return self


class ImageApplicabilityErrorResponse(BaseModel):
    """图片语义适用性接口稳定输入错误结构。"""

    requestId: str
    status: str = "REJECTED"
    errorCode: str
    errorMessage: str
