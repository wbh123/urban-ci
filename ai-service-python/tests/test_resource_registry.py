"""本地下载资源登记工具测试。"""

from __future__ import annotations

import json

from tools.resource_registry import LocalResourceRegistrationRequest, register_local_resource


def test_register_single_archive_writes_candidate_source_and_sha256(tmp_path):
    """登记单个压缩包时，应输出候选来源清单和 SHA-256 摘要。"""

    archive_path = tmp_path / "dataset.zip"
    archive_path.write_bytes(b"urban-safe-dataset")
    output_dir = tmp_path / "registry"

    record = register_local_resource(
        LocalResourceRegistrationRequest(
            provider="MENDELEY",
                resource_type="MODEL_ARCHIVE",
            resource_id="10.17632/example.1",
            local_path=archive_path,
            output_dir=output_dir,
            license_name="CC-BY-4.0",
            downloaded_at="2026-07-27T12:00:00Z",
        )
    )

    source_payload = json.loads((output_dir / "source.json").read_text(encoding="utf-8"))
    sha256_content = (output_dir / "SHA256SUMS").read_text(encoding="utf-8")
    readme_content = (output_dir / "README.local.md").read_text(encoding="utf-8")

    assert record.approval_status == "CANDIDATE"
    assert source_payload["approvalStatus"] == "CANDIDATE"
    assert source_payload["provider"] == "MENDELEY"
    assert source_payload["resourceType"] == "MODEL_ARCHIVE"
    assert source_payload["fileCount"] == 1
    assert source_payload["totalBytes"] == len(b"urban-safe-dataset")
    assert "dataset.zip" in sha256_content
    assert "不得直接进入 APPROVED" in readme_content


def test_register_directory_copies_license_and_uses_relative_sha_paths(tmp_path):
    """登记目录资源时，应复制许可证文件，并在摘要文件中使用相对路径。"""

    resource_dir = tmp_path / "hf-model"
    resource_dir.mkdir()
    (resource_dir / "LICENSE").write_text("MIT\n", encoding="utf-8")
    weights_dir = resource_dir / "weights"
    weights_dir.mkdir()
    (weights_dir / "model.safetensors").write_bytes(b"weights")
    output_dir = tmp_path / "registry"

    register_local_resource(
        LocalResourceRegistrationRequest(
            provider="HUGGING_FACE",
            resource_type="MODEL_SNAPSHOT",
            resource_id="org/model",
            local_path=resource_dir,
            output_dir=output_dir,
            license_name="MIT",
            requested_revision="abc123",
            resolved_revision="abc123",
            downloaded_at="2026-07-27T12:00:00Z",
        )
    )

    source_payload = json.loads((output_dir / "source.json").read_text(encoding="utf-8"))
    sha256_content = (output_dir / "SHA256SUMS").read_text(encoding="utf-8")

    assert source_payload["requestedRevision"] == "abc123"
    assert source_payload["resolvedRevision"] == "abc123"
    assert source_payload["licenseFile"] == "LICENSE.txt"
    assert (output_dir / "LICENSE.txt").read_text(encoding="utf-8") == "MIT\n"
    assert "weights/model.safetensors" in sha256_content
    assert str(resource_dir) not in sha256_content


def test_register_resource_rejects_unknown_license(tmp_path):
    """许可证未知时，不允许生成可误导后续准入的登记文件。"""

    resource_path = tmp_path / "model.bin"
    resource_path.write_bytes(b"model")

    try:
        register_local_resource(
            LocalResourceRegistrationRequest(
                provider="HUGGING_FACE",
                resource_type="MODEL_SNAPSHOT",
                resource_id="org/model",
                local_path=resource_path,
                output_dir=tmp_path / "registry",
                license_name="UNKNOWN",
                downloaded_at="2026-07-27T12:00:00Z",
            )
        )
    except ValueError as exc:
        assert "许可证" in str(exc)
    else:
        raise AssertionError("未知许可证必须被拒绝")


def test_register_dataset_archive_writes_dataset_manifest(tmp_path):
    """登记数据集压缩包时，应额外输出项目准入所需的数据集清单。"""

    archive_path = tmp_path / "jwsn7tfbrp-1.zip"
    archive_path.write_bytes(b"concrete-crack-segmentation")
    output_dir = tmp_path / "registry"

    register_local_resource(
        LocalResourceRegistrationRequest(
            provider="MENDELEY",
            resource_type="DATASET_ARCHIVE",
            resource_id="10.17632/jwsn7tfbrp.1",
            local_path=archive_path,
            output_dir=output_dir,
            license_name="CC-BY-4.0",
            downloaded_at="2026-07-27T12:00:00Z",
            dataset_id="CONCRETE-CRACK-SEG-001",
            dataset_name="Concrete Crack Segmentation Dataset",
            dataset_version="1",
            annotation_type="BINARY_MASK",
        )
    )

    source_payload = json.loads((output_dir / "source.json").read_text(encoding="utf-8"))
    dataset_payload = json.loads((output_dir / "dataset_manifest.json").read_text(encoding="utf-8"))

    assert dataset_payload["datasetId"] == "CONCRETE-CRACK-SEG-001"
    assert dataset_payload["name"] == "Concrete Crack Segmentation Dataset"
    assert dataset_payload["sourceType"] == "MENDELEY"
    assert dataset_payload["sourceId"] == "10.17632/jwsn7tfbrp.1"
    assert dataset_payload["version"] == "1"
    assert dataset_payload["license"] == "CC-BY-4.0"
    assert dataset_payload["annotationType"] == "BINARY_MASK"
    assert dataset_payload["approvalStatus"] == "CANDIDATE"
    assert dataset_payload["archiveSha256"] == source_payload["archiveSha256"]
