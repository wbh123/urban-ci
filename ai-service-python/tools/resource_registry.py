"""本地模型与数据资源登记工具。

本模块只负责把已经手工下载到本机的候选资源登记成可追溯元数据。
它不会批准模型、不会移动大文件、不会修改运行时 REAL 配置，也不会把
任何资源复制到 `data/ai-service/models/`。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


# 缺省登记状态。所有本地下载资源在人工核验和独立评估前都必须保持候选状态。
DEFAULT_APPROVAL_STATUS = "CANDIDATE"

# 本项目允许进入候选登记流程的许可证名称。UNKNOWN 不进入登记，避免误导后续审批。
ALLOWED_LICENSE_NAMES = {
    "APACHE-2.0",
    "BSD",
    "BSD-2-CLAUSE",
    "BSD-3-CLAUSE",
    "CC-BY-4.0",
    "MIT",
}

# 常见许可证文件名。目录型 Hugging Face 快照通常会包含这些文件。
LICENSE_FILE_NAMES = ("LICENSE", "LICENSE.txt", "LICENSE.md", "COPYING", "COPYING.txt")


@dataclass(frozen=True)
class LocalResourceRegistrationRequest:
    """描述一次本地资源登记请求。

    字段说明：
    - `provider`：资源来源平台，例如 HUGGING_FACE、KAGGLE、MENDELEY；
    - `resource_type`：资源类型，例如 MODEL_SNAPSHOT 或 DATASET_ARCHIVE；
    - `resource_id`：平台资源标识，必须能回溯到原始页面、DOI 或仓库；
    - `local_path`：本机已经下载好的文件或目录；
    - `output_dir`：登记文件输出目录，不保存大模型权重或数据集副本；
    - `license_name`：人工核验后的许可证名称；
    - `requested_revision`：下载时请求的固定版本或提交；
    - `resolved_revision`：平台实际解析得到的固定版本或提交；
    - `source_url`：原始资源页面地址；
    - `downloaded_at`：下载时间，未传入时使用当前 UTC 时间。
    - `dataset_id`：项目内数据集编号，仅数据集资源需要；
    - `dataset_name`：数据集名称，仅数据集资源需要；
    - `dataset_version`：数据集版本，仅数据集资源需要；
    - `annotation_type`：标注类型，例如 BINARY_MASK、VOC_XML。
    """

    provider: str
    resource_type: str
    resource_id: str
    local_path: Path
    output_dir: Path
    license_name: str
    requested_revision: str = ""
    resolved_revision: str = ""
    source_url: str = ""
    downloaded_at: str = ""
    dataset_id: str = ""
    dataset_name: str = ""
    dataset_version: str = ""
    annotation_type: str = ""


@dataclass(frozen=True)
class LocalResourceRegistrationRecord:
    """登记完成后的摘要结果，供 CLI 输出和测试断言使用。"""

    provider: str
    resource_type: str
    resource_id: str
    approval_status: str
    source_json: Path
    sha256_sums: Path
    file_count: int
    total_bytes: int


@dataclass(frozen=True)
class _HashedFile:
    """单个文件的相对路径、字节数和 SHA-256 摘要。"""

    relative_path: str
    size_bytes: int
    sha256: str


def register_local_resource(request: LocalResourceRegistrationRequest) -> LocalResourceRegistrationRecord:
    """登记本地模型或数据资源，并输出 `source.json`、`SHA256SUMS` 和说明文件。"""

    local_path = request.local_path.expanduser().resolve()
    output_dir = request.output_dir.expanduser().resolve()
    provider = _required_upper_text(request.provider, "provider")
    resource_type = _required_upper_text(request.resource_type, "resource_type")
    resource_id = _required_text(request.resource_id, "resource_id")
    license_name = _normalize_license_name(request.license_name)

    if not local_path.exists():
        raise FileNotFoundError(f"本地资源不存在：{local_path}")

    output_dir.mkdir(parents=True, exist_ok=True)
    hashed_files = _hash_resource_files(local_path)
    if not hashed_files:
        raise ValueError("本地资源没有可登记的文件")

    sha256_sums = output_dir / "SHA256SUMS"
    _write_sha256_sums(sha256_sums, hashed_files)

    license_file_name = _copy_license_file(local_path, output_dir)
    source_json = output_dir / "source.json"
    source_payload = _build_source_payload(
        request=request,
        provider=provider,
        resource_type=resource_type,
        resource_id=resource_id,
        local_path=local_path,
        license_name=license_name,
        license_file_name=license_file_name,
        hashed_files=hashed_files,
    )
    source_json.write_text(json.dumps(source_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if resource_type == "DATASET_ARCHIVE":
        _write_dataset_manifest(output_dir / "dataset_manifest.json", request, source_payload)
    _write_local_readme(output_dir / "README.local.md", source_payload)

    return LocalResourceRegistrationRecord(
        provider=provider,
        resource_type=resource_type,
        resource_id=resource_id,
        approval_status=DEFAULT_APPROVAL_STATUS,
        source_json=source_json,
        sha256_sums=sha256_sums,
        file_count=len(hashed_files),
        total_bytes=sum(file.size_bytes for file in hashed_files),
    )


def _build_source_payload(
    *,
    request: LocalResourceRegistrationRequest,
    provider: str,
    resource_type: str,
    resource_id: str,
    local_path: Path,
    license_name: str,
    license_file_name: str,
    hashed_files: list[_HashedFile],
) -> dict[str, object]:
    """组装写入 `source.json` 的本地登记载荷。"""

    downloaded_at = request.downloaded_at.strip() or datetime.now(timezone.utc).isoformat()
    payload: dict[str, object] = {
        "schemaVersion": 1,
        "provider": provider,
        "resourceType": resource_type,
        "resourceId": resource_id,
        "sourceUrl": request.source_url.strip(),
        "requestedRevision": request.requested_revision.strip(),
        "resolvedRevision": request.resolved_revision.strip(),
        "license": license_name,
        "downloadedAt": downloaded_at,
        "downloadMethod": "MANUAL_LOCAL",
        "localPath": str(local_path),
        "approvalStatus": DEFAULT_APPROVAL_STATUS,
        "sha256SumsFile": "SHA256SUMS",
        "fileCount": len(hashed_files),
        "totalBytes": sum(file.size_bytes for file in hashed_files),
        "files": [
            {"path": file.relative_path, "sizeBytes": file.size_bytes, "sha256": file.sha256}
            for file in hashed_files
        ],
        "notes": "本记录仅表示资源已本地登记；许可证、来源、独立评估和人工审批完成前不得进入 APPROVED。",
    }
    if local_path.is_file():
        payload["archiveSha256"] = hashed_files[0].sha256
    if license_file_name:
        payload["licenseFile"] = license_file_name
    return payload


def _write_dataset_manifest(path: Path, request: LocalResourceRegistrationRequest, source_payload: dict[str, object]) -> None:
    """写出项目数据集准入清单，供后续解包、划分和训练流程使用。"""

    dataset_id = _required_text(request.dataset_id, "dataset_id")
    dataset_name = _required_text(request.dataset_name, "dataset_name")
    dataset_version = _required_text(request.dataset_version, "dataset_version")
    annotation_type = _required_upper_text(request.annotation_type, "annotation_type")
    archive_sha256 = source_payload.get("archiveSha256")
    if not isinstance(archive_sha256, str) or not archive_sha256:
        raise ValueError("数据集压缩包登记必须包含 archiveSha256")

    dataset_payload: dict[str, object] = {
        "schemaVersion": 1,
        "datasetId": dataset_id,
        "name": dataset_name,
        "sourceType": source_payload["provider"],
        "sourceId": source_payload["resourceId"],
        "sourceUrl": source_payload["sourceUrl"],
        "version": dataset_version,
        "license": source_payload["license"],
        "downloadedAt": source_payload["downloadedAt"],
        "archiveSha256": archive_sha256,
        "archivePath": source_payload["localPath"],
        "annotationType": annotation_type,
        "approvalStatus": DEFAULT_APPROVAL_STATUS,
        "fileCount": source_payload["fileCount"],
        "totalBytes": source_payload["totalBytes"],
        "sha256SumsFile": source_payload["sha256SumsFile"],
        "notes": "该数据集仅完成本地候选登记；解包、去重、标签统一、划分和独立评估完成前不得用于正式准入。",
    }
    path.write_text(json.dumps(dataset_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _hash_resource_files(local_path: Path) -> list[_HashedFile]:
    """计算文件或目录下全部普通文件的 SHA-256 摘要。"""

    if local_path.is_file():
        return [_HashedFile(relative_path=local_path.name, size_bytes=local_path.stat().st_size, sha256=_sha256(local_path))]

    hashed_files: list[_HashedFile] = []
    for file_path in sorted(path for path in local_path.rglob("*") if path.is_file()):
        relative_path = file_path.relative_to(local_path).as_posix()
        hashed_files.append(
            _HashedFile(
                relative_path=relative_path,
                size_bytes=file_path.stat().st_size,
                sha256=_sha256(file_path),
            )
        )
    return hashed_files


def _write_sha256_sums(path: Path, hashed_files: list[_HashedFile]) -> None:
    """按常见 `sha256sum` 文本格式写出摘要清单。"""

    lines = [f"{file.sha256}  {file.relative_path}" for file in hashed_files]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _copy_license_file(local_path: Path, output_dir: Path) -> str:
    """从目录资源中复制许可证文件；单文件压缩包不尝试解包查找许可证。"""

    if not local_path.is_dir():
        return ""
    for license_name in LICENSE_FILE_NAMES:
        source = local_path / license_name
        if source.is_file():
            target = output_dir / "LICENSE.txt"
            shutil.copy2(source, target)
            return target.name
    return ""


def _write_local_readme(path: Path, source_payload: dict[str, object]) -> None:
    """写出面向人工复核人员的本地登记说明。"""

    content = f"""# 本地资源登记说明

- 资源标识：`{source_payload["resourceId"]}`
- 来源平台：`{source_payload["provider"]}`
- 资源类型：`{source_payload["resourceType"]}`
- 当前状态：`{source_payload["approvalStatus"]}`
- 许可证：`{source_payload["license"]}`
- 文件数量：`{source_payload["fileCount"]}`
- 总字节数：`{source_payload["totalBytes"]}`

本目录只记录本地下载资源的来源和摘要，不包含正式准入结论。该资源在完成许可证复核、
固定版本确认、独立评估、断网验证和人工审批前，不得直接进入 APPROVED，也不得安装到
`data/ai-service/models/` 作为 REAL 运行时模型。
"""
    path.write_text(content, encoding="utf-8")


def _normalize_license_name(value: str) -> str:
    """规范化许可证名称，并拒绝未知或未核验许可证。"""

    license_name = _required_text(value, "license_name").upper()
    if license_name in {"UNKNOWN", "UNLICENSED", "NONE"}:
        raise ValueError("许可证未核验，不能登记为候选资源")
    if license_name.startswith("CC BY"):
        license_name = license_name.replace("CC BY", "CC-BY").replace(" ", "-")
    if license_name not in ALLOWED_LICENSE_NAMES:
        raise ValueError(f"许可证不在当前允许候选清单中：{license_name}")
    return license_name


def _required_upper_text(value: str, field_name: str) -> str:
    """读取必填文本字段并转换为大写标识。"""

    return _required_text(value, field_name).upper()


def _required_text(value: str, field_name: str) -> str:
    """读取必填文本字段，避免空白字符串进入登记文件。"""

    text = value.strip()
    if not text:
        raise ValueError(f"字段 {field_name} 不能为空")
    return text


def _sha256(path: Path) -> str:
    """以流式方式计算大文件 SHA-256，避免一次性读入内存。"""

    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def add_register_resource_parser(subparsers: argparse._SubParsersAction[argparse.ArgumentParser]) -> None:
    """向模型流水线 CLI 注册 `register-resource` 子命令。"""

    parser = subparsers.add_parser("register-resource", help="登记本地手工下载的模型或数据资源")
    parser.add_argument("--provider", required=True, help="来源平台，例如 HUGGING_FACE、KAGGLE、MENDELEY")
    parser.add_argument("--resource-type", required=True, help="资源类型，例如 MODEL_SNAPSHOT 或 DATASET_ARCHIVE")
    parser.add_argument("--resource-id", required=True, help="平台资源标识、DOI 或仓库名")
    parser.add_argument("--local-path", type=Path, required=True, help="本地已下载文件或目录")
    parser.add_argument("--output", type=Path, required=True, help="登记文件输出目录")
    parser.add_argument("--license", dest="license_name", required=True, help="已核验许可证名称")
    parser.add_argument("--requested-revision", default="", help="请求下载的固定版本或提交")
    parser.add_argument("--resolved-revision", default="", help="实际解析得到的固定版本或提交")
    parser.add_argument("--source-url", default="", help="原始资源页面地址")
    parser.add_argument("--downloaded-at", default="", help="下载时间，ISO-8601 格式")
    parser.add_argument("--dataset-id", default="", help="项目内数据集编号，仅 DATASET_ARCHIVE 需要")
    parser.add_argument("--dataset-name", default="", help="数据集名称，仅 DATASET_ARCHIVE 需要")
    parser.add_argument("--dataset-version", default="", help="数据集版本，仅 DATASET_ARCHIVE 需要")
    parser.add_argument("--annotation-type", default="", help="标注类型，例如 BINARY_MASK、VOC_XML")


def command_register_resource(args: argparse.Namespace) -> None:
    """执行 `register-resource` CLI 子命令，并打印登记结果摘要。"""

    record = register_local_resource(
        LocalResourceRegistrationRequest(
            provider=args.provider,
            resource_type=args.resource_type,
            resource_id=args.resource_id,
            local_path=args.local_path,
            output_dir=args.output,
            license_name=args.license_name,
            requested_revision=args.requested_revision,
            resolved_revision=args.resolved_revision,
            source_url=args.source_url,
            downloaded_at=args.downloaded_at,
            dataset_id=args.dataset_id,
            dataset_name=args.dataset_name,
            dataset_version=args.dataset_version,
            annotation_type=args.annotation_type,
        )
    )
    print(json.dumps({
        "provider": record.provider,
        "resourceType": record.resource_type,
        "resourceId": record.resource_id,
        "approvalStatus": record.approval_status,
        "sourceJson": str(record.source_json),
        "sha256Sums": str(record.sha256_sums),
        "fileCount": record.file_count,
        "totalBytes": record.total_bytes,
    }, ensure_ascii=False, indent=2))
