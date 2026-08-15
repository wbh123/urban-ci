"""统一模型目录、适配器工厂和 CUDA-only 推理注册表。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Mapping

from .adapters import DeterministicMockAdapter, GroundedSam2TinyAdapter, OnnxCrackSegmentationAdapter
from .adapters.protocol import InferenceAdapter
from .config import Settings
from .errors import ModelUnavailableError
from .model_manifest import ModelManifest, ModelManifestError, load_model_manifest
from .schemas import (
    InferenceMode,
    ModelCatalogResponse,
    ModelInfo,
    RuntimeReadiness,
)
from .vision_manifest import ZeroShotModelManifest


CUDA_ONLY_RUNTIME = "CUDA_ONLY"
CUDA_EXECUTION_PROVIDER = "CUDAExecutionProvider"
PY_TORCH_CUDA_PROVIDER = "PyTorch-CUDA"
# 真实模型必须运行在 NVIDIA CUDA 上；ONNX 适配器返回 CUDAExecutionProvider，
# Transformers 视觉适配器返回 PyTorch-CUDA。两者都不允许 CPU 回退。
CUDA_CAPABLE_PROVIDERS = {CUDA_EXECUTION_PROVIDER, PY_TORCH_CUDA_PROVIDER}


class RuntimeCatalogError(ValueError):
    """运行时模型目录格式、路径或身份不合法。"""


@dataclass(frozen=True)
class RuntimeCatalogEntry:
    model_id: str | None
    version: str | None
    manifest_path: Path


@dataclass(frozen=True)
class RuntimeCatalog:
    runtime: str
    default_model_id: str | None
    entries: tuple[RuntimeCatalogEntry, ...]


AdapterFactory = Callable[[ModelManifest | ZeroShotModelManifest], InferenceAdapter]


def load_runtime_catalog(settings: Settings) -> RuntimeCatalog:
    """读取显式运行时目录；没有目录时只兼容一次旧版单模型配置。"""

    root = settings.model_root.resolve()
    catalog_path = _resolve_within_root(settings.model_catalog_path, root, "模型目录")
    if catalog_path.is_file():
        return _read_explicit_catalog(catalog_path, root)
    if catalog_path.exists():
        raise RuntimeCatalogError("模型目录路径存在但不是文件")

    if (
        settings.legacy_real_model_status == "APPROVED"
        and settings.legacy_real_model_manifest_path is not None
    ):
        manifest_path = _resolve_within_root(
            settings.legacy_real_model_manifest_path,
            root,
            "旧版模型清单",
        )
        return RuntimeCatalog(
            runtime=CUDA_ONLY_RUNTIME,
            default_model_id=None,
            entries=(RuntimeCatalogEntry(None, None, manifest_path),),
        )

    return RuntimeCatalog(
        runtime=CUDA_ONLY_RUNTIME,
        default_model_id=None,
        entries=(),
    )


def _read_explicit_catalog(path: Path, root: Path) -> RuntimeCatalog:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as ex:
        raise RuntimeCatalogError("模型目录不是合法 UTF-8 JSON") from ex
    if not isinstance(payload, dict):
        raise RuntimeCatalogError("模型目录根节点必须是对象")
    if _integer(payload, "schemaVersion") != 1:
        raise RuntimeCatalogError("模型目录仅支持 schemaVersion=1")

    runtime = _text(payload, "runtime").upper()
    if runtime != CUDA_ONLY_RUNTIME:
        raise RuntimeCatalogError("模型目录 runtime 必须为 CUDA_ONLY")

    default_model_id = _optional_text(payload.get("defaultModelId"))
    models = payload.get("models")
    if not isinstance(models, list):
        raise RuntimeCatalogError("模型目录 models 必须是数组")

    entries: list[RuntimeCatalogEntry] = []
    seen_model_ids: set[str] = set()
    enabled_model_ids: set[str] = set()
    for item in models:
        if not isinstance(item, dict):
            raise RuntimeCatalogError("模型目录 models 元素必须是对象")
        model_id = _text(item, "modelId")
        if model_id in seen_model_ids:
            raise RuntimeCatalogError(f"模型目录包含重复模型编号：{model_id}")
        seen_model_ids.add(model_id)
        version = _text(item, "version")
        enabled = item.get("enabled", True)
        if not isinstance(enabled, bool):
            raise RuntimeCatalogError(f"模型 {model_id} 的 enabled 必须是布尔值")
        manifest_value = Path(_text(item, "manifestPath"))
        if manifest_value.is_absolute():
            raise RuntimeCatalogError(f"模型 {model_id} 清单必须使用模型根目录内相对路径")
        manifest_path = _resolve_within_root(
            manifest_value, root, f"模型 {model_id} 清单"
        )
        if enabled:
            enabled_model_ids.add(model_id)
            entries.append(RuntimeCatalogEntry(model_id, version, manifest_path))

    if default_model_id is not None and default_model_id not in enabled_model_ids:
        raise RuntimeCatalogError("默认模型必须出现在已启用模型列表中")
    if entries and default_model_id is None and len(entries) > 1:
        raise RuntimeCatalogError("启用多个真实模型时必须配置 defaultModelId")

    return RuntimeCatalog(
        runtime=runtime,
        default_model_id=default_model_id,
        entries=tuple(entries),
    )


def _resolve_within_root(path: Path, root: Path, field_name: str) -> Path:
    expanded = path.expanduser()
    if not expanded.is_absolute() and ".." in expanded.parts:
        raise RuntimeCatalogError(f"{field_name}必须位于模型根目录内")
    resolved = expanded.resolve() if expanded.is_absolute() else (root / expanded).resolve()
    try:
        resolved.relative_to(root)
    except ValueError as ex:
        raise RuntimeCatalogError(f"{field_name}必须位于模型根目录内") from ex
    return resolved


def _text(payload: dict, key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise RuntimeCatalogError(f"字段 {key} 不能为空")
    return value.strip()


def _optional_text(value) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise RuntimeCatalogError("defaultModelId 必须是字符串")
    stripped = value.strip()
    return stripped or None


def _integer(payload: dict, key: str) -> int:
    value = payload.get(key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise RuntimeCatalogError(f"字段 {key} 必须是整数")
    return value


class ModelRegistry:
    """进程级模型注册表；启用的真实模型必须全部在 CUDA 上完成热身。"""

    def __init__(
        self,
        settings: Settings,
        adapter_factories: Mapping[str, AdapterFactory] | None = None,
    ) -> None:
        self._settings = settings
        self._mock_adapter = DeterministicMockAdapter(
            model_id=settings.mock_model_id,
            model_name=settings.mock_model_name,
            model_version=settings.mock_model_version,
        )
        self._factories: Mapping[str, AdapterFactory] = adapter_factories or {
            "onnx-crack-segmentation-v1": lambda manifest: OnnxCrackSegmentationAdapter(
                manifest, cuda_device_id=settings.cuda_device_id
            ),
            "grounded-sam2-tiny-v1": lambda manifest: GroundedSam2TinyAdapter(
                manifest, settings=settings
            ),
            # 中性协议；Tiny 旧 ID 保留用于向后兼容。
            "grounded-sam2-v1": lambda manifest: GroundedSam2TinyAdapter(
                manifest, settings=settings
            ),
        }
        self._catalog = load_runtime_catalog(settings)
        self._real_adapters: dict[str, InferenceAdapter] = {}
        self._real_manifests: dict[str, ModelManifest | ZeroShotModelManifest] = {}
        self._default_real_model_id: str | None = None
        self._load_real_models()

    def _load_real_models(self) -> None:
        try:
            for entry in self._catalog.entries:
                manifest = load_model_manifest(entry.manifest_path, self._settings.model_root)
                if entry.model_id is not None and manifest.model_id != entry.model_id:
                    raise RuntimeCatalogError("模型目录编号与模型清单不一致")
                if entry.version is not None and manifest.version != entry.version:
                    raise RuntimeCatalogError("模型目录版本与模型清单不一致")
                if manifest.model_id in self._real_adapters:
                    raise RuntimeCatalogError("模型清单解析后出现重复模型编号")
                if manifest.status != "APPROVED":
                    raise RuntimeCatalogError(
                        f"模型 {manifest.model_id} 未批准（status={manifest.status}），"
                        "禁止进入 REAL 运行时"
                    )
                factory = self._factories.get(manifest.adapter)
                if factory is None:
                    raise RuntimeCatalogError(f"缺少适配器工厂：{manifest.adapter}")
                adapter = factory(manifest)
                provider = _execution_provider(adapter)
                if provider not in CUDA_CAPABLE_PROVIDERS:
                    raise RuntimeCatalogError(
                        f"模型 {manifest.model_id} 未运行在 CUDA 上：{provider}"
                    )
                self._real_adapters[manifest.model_id] = adapter
                self._real_manifests[manifest.model_id] = manifest

            if self._catalog.default_model_id is not None:
                self._default_real_model_id = self._catalog.default_model_id
            elif len(self._real_adapters) == 1:
                self._default_real_model_id = next(iter(self._real_adapters))
            elif len(self._real_adapters) > 1:
                raise RuntimeCatalogError("多个真实模型缺少默认模型")
        except (RuntimeCatalogError, ModelManifestError, ModelUnavailableError, OSError, ValueError) as ex:
            self._real_adapters.clear()
            self._real_manifests.clear()
            self._default_real_model_id = None
            raise ModelUnavailableError("模型运行时目录加载失败") from ex

    def resolve(
        self, mode: InferenceMode, requested_model_id: str | None = None
    ) -> InferenceAdapter:
        if mode == InferenceMode.MOCK:
            if requested_model_id and requested_model_id != self._settings.mock_model_id:
                raise ModelUnavailableError("请求的模拟模型未加载")
            return self._mock_adapter

        model_id = requested_model_id.strip() if requested_model_id else None
        if model_id is None:
            raise ModelUnavailableError("REAL 请求必须指定模型编号")
        adapter = self._real_adapters.get(model_id)
        if adapter is None:
            raise ModelUnavailableError(f"请求的真实模型未加载：{model_id}")
        return adapter

    def current_model_info(self) -> ModelInfo:
        if self._default_real_model_id is not None:
            return self.model_info(self._default_real_model_id)
        return self._mock_model_info()

    def model_info(self, model_id: str) -> ModelInfo:
        if model_id == self._settings.mock_model_id:
            return self._mock_model_info()
        manifest = self._real_manifests.get(model_id)
        adapter = self._real_adapters.get(model_id)
        if manifest is None or adapter is None:
            raise ModelUnavailableError(f"请求的模型未加载：{model_id}")
        return ModelInfo(
            modelId=manifest.model_id,
            modelName=manifest.model_name,
            version=manifest.version,
            mode=InferenceMode.REAL,
            status=manifest.status,
            supportedDefects=[item.code.lower() for item in manifest.classes],
            license=manifest.license,
            weightSha256=manifest.weight_sha256,
            ready=True,
            executionProvider=_execution_provider(adapter),
            deviceId=self._settings.cuda_device_id,
            task=manifest.task,
            adapter=manifest.adapter,
        )

    def model_brief(self, model_id: str | None):
        if model_id is None:
            return None
        try:
            return self.resolve(
                InferenceMode.MOCK
                if model_id == self._settings.mock_model_id
                else InferenceMode.REAL,
                model_id,
            ).model_info()
        except ModelUnavailableError:
            return None

    def catalog(self) -> ModelCatalogResponse:
        models = [self._mock_model_info()]
        models.extend(self.model_info(model_id) for model_id in sorted(self._real_adapters))
        return ModelCatalogResponse(
            runtime=CUDA_ONLY_RUNTIME,
            defaultRealModelId=self._default_real_model_id,
            models=models,
        )

    def readiness(self) -> RuntimeReadiness:
        return RuntimeReadiness(
            status="READY",
            runtime=CUDA_ONLY_RUNTIME,
            cudaDeviceId=self._settings.cuda_device_id,
            realModelCount=len(self._real_adapters),
            defaultRealModelId=self._default_real_model_id,
        )

    def _mock_model_info(self) -> ModelInfo:
        return ModelInfo(
            modelId=self._settings.mock_model_id,
            modelName=self._settings.mock_model_name,
            version=self._settings.mock_model_version,
            mode=InferenceMode.MOCK,
            status="MOCK",
            supportedDefects=["crack"],
            license="PROJECT-INTERNAL-MOCK",
            weightSha256=None,
            ready=True,
            executionProvider="DETERMINISTIC_MOCK",
            deviceId=None,
            task="DEFECT_DETECTION",
            adapter="deterministic-mock-v1",
        )


def _execution_provider(adapter: InferenceAdapter) -> str | None:
    method = getattr(adapter, "execution_provider", None)
    if method is None:
        return None
    return method()
