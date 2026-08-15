import hashlib

from tools import download_vision_models as base
from tools import download_vision_models_resilient as resilient


def test_direct_download_retries_next_endpoint_when_mirror_weight_sha_is_wrong(tmp_path, monkeypatch):
    repository = base.DETECTOR_REPOSITORY
    revision = base.DETECTOR_REVISION
    expected = hashlib.sha256(b"official-weight").hexdigest()

    monkeypatch.setitem(base.DIRECT_FILE_SETS, repository, ("model.safetensors",))
    monkeypatch.setitem(base.PINNED_WEIGHT_SHA256, (repository, revision), expected)
    monkeypatch.setattr(base, "_direct_endpoints", lambda: ("https://mirror.invalid", "https://official.invalid"))

    calls: list[str] = []

    def fake_curl(url, destination):
        calls.append(url)
        if "mirror.invalid" in url:
            destination.write_bytes(b"wrong-weight")
        else:
            destination.write_bytes(b"official-weight")

    monkeypatch.setattr(base, "_curl_download", fake_curl)

    destination = tmp_path / "detector"
    resilient._download_direct(repository, revision, destination)

    assert len(calls) == 2
    assert "mirror.invalid" in calls[0]
    assert "official.invalid" in calls[1]
    assert destination.joinpath("model.safetensors").read_bytes() == b"official-weight"
    assert base._file_sha256(destination / "model.safetensors") == expected


def test_source_is_not_considered_successful_when_only_stale_residual_files_exist(tmp_path, monkeypatch):
    repository = base.DETECTOR_REPOSITORY
    revision = base.DETECTOR_REVISION
    expected = hashlib.sha256(b"official-weight").hexdigest()
    destination = tmp_path / "detector"
    destination.mkdir()
    destination.joinpath("model.safetensors").write_bytes(b"stale-weight")

    monkeypatch.setitem(base.DIRECT_FILE_SETS, repository, ("model.safetensors",))
    monkeypatch.setitem(base.PINNED_WEIGHT_SHA256, (repository, revision), expected)
    monkeypatch.setattr(base, "_download_model_scope", lambda *args, **kwargs: None)
    monkeypatch.setattr(base, "_download_huggingface", lambda *args, **kwargs: None)

    direct_calls: list[bool] = []

    def fake_direct(model_id, model_revision, target):
        direct_calls.append(True)
        target.joinpath("model.safetensors").write_bytes(b"official-weight")

    monkeypatch.setattr(resilient, "_download_direct", fake_direct)

    source = resilient._download(repository, revision, destination, None)

    assert source == "direct"
    assert direct_calls == [True]
    assert destination.joinpath("model.safetensors").read_bytes() == b"official-weight"
