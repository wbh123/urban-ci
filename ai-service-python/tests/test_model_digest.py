"""模型目录摘要范围测试：下载元数据/缓存不参与摘要，真实模型文件必须参与。"""

from __future__ import annotations

from pathlib import Path

from app.model_digest import dir_digest


def _write(root: Path, relative: str, data: bytes) -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return path


def test_adding_cache_files_does_not_change_digest(tmp_path):
    _write(tmp_path, "model.safetensors", b"weights")
    _write(tmp_path, "config.json", b"{}")
    baseline, _ = dir_digest(tmp_path)

    _write(tmp_path, ".cache/huggingface/token", b"tok")
    _write(tmp_path, ".cache/huggingface/blobs/abc", b"blob")
    after_add, _ = dir_digest(tmp_path)

    assert after_add == baseline


def test_modifying_cache_files_does_not_change_digest(tmp_path):
    _write(tmp_path, "model.safetensors", b"weights")
    _write(tmp_path, ".cache/huggingface/token", b"tok")
    baseline, _ = dir_digest(tmp_path)

    _write(tmp_path, ".cache/huggingface/token", b"changed")
    after_change, _ = dir_digest(tmp_path)

    assert after_change == baseline


def test_modifying_model_safetensors_changes_digest(tmp_path):
    _write(tmp_path, "model.safetensors", b"weights-v1")
    _write(tmp_path, "config.json", b"{}")
    baseline, _ = dir_digest(tmp_path)

    _write(tmp_path, "model.safetensors", b"weights-v2")
    after_change, _ = dir_digest(tmp_path)

    assert after_change != baseline


def test_modifying_config_json_changes_digest(tmp_path):
    _write(tmp_path, "model.safetensors", b"weights")
    _write(tmp_path, "config.json", b'{"a": 1}')
    baseline, _ = dir_digest(tmp_path)

    _write(tmp_path, "config.json", b'{"a": 2}')
    after_change, _ = dir_digest(tmp_path)

    assert after_change != baseline


def test_ignored_metadata_files_excluded(tmp_path):
    _write(tmp_path, "model.safetensors", b"weights")
    _write(tmp_path, "preprocessor_config.json", b"{}")
    baseline, _ = dir_digest(tmp_path)

    _write(tmp_path, "model.safetensors.lock", b"lock")
    _write(tmp_path, "download.tmp", b"tmp")
    _write(tmp_path, "download.part", b"part")
    _write(tmp_path, ".DS_Store", b"ds")
    _write(tmp_path, "Thumbs.db", b"tb")
    _write(tmp_path, "__pycache__/x.pyc", b"pyc")
    after_add, _ = dir_digest(tmp_path)

    assert after_add == baseline


def test_deterministic_across_runs(tmp_path):
    _write(tmp_path, "config.json", b"{}")
    _write(tmp_path, "model.safetensors", b"weights")
    first, _ = dir_digest(tmp_path)
    second, _ = dir_digest(tmp_path)
    assert first == second
