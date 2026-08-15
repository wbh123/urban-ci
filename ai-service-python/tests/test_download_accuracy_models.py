from tools.download_accuracy_models import (
    FLORENCE_HF_REPO,
    _candidate_sources,
    _specs,
    accuracy_model_paths,
    check_model_dir,
    source_order,
)


def test_accuracy_model_paths_are_under_model_root(tmp_path):
    paths = accuracy_model_paths(tmp_path)
    assert paths.qwen == tmp_path / "AI-VISION-ACCURACY" / "qwen3-vl-2b-instruct"
    assert paths.florence == tmp_path / "AI-VISION-ACCURACY" / "florence-2-large-ft-native"


def test_florence_uses_native_transformers_converted_checkpoint():
    assert FLORENCE_HF_REPO == "florence-community/Florence-2-large-ft"


def test_florence_auto_skips_incompatible_modelscope_remote_code_source(tmp_path):
    paths = accuracy_model_paths(tmp_path)
    spec = _specs(paths, "florence")[0]
    assert spec.modelscope_repo is None
    assert _candidate_sources(spec, "auto", True) == ["hf-mirror", "huggingface"]


def test_check_model_dir_requires_config_and_weight(tmp_path):
    model_dir = tmp_path / "model"
    model_dir.mkdir()
    ok, missing = check_model_dir(model_dir)
    assert ok is False
    assert "config.json" in missing
    (model_dir / "config.json").write_text("{}", encoding="utf-8")
    (model_dir / "model.safetensors").write_bytes(b"x")
    ok, missing = check_model_dir(model_dir)
    assert ok is True
    assert missing == []


def test_auto_source_prefers_mainland_china_sources():
    assert source_order("auto") == ["modelscope", "hf-mirror", "huggingface"]


def test_explicit_source_can_disable_fallback():
    assert source_order("hf-mirror", allow_fallback=False) == ["hf-mirror"]


def test_explicit_modelscope_still_has_safe_fallbacks():
    assert source_order("modelscope", allow_fallback=True) == [
        "modelscope",
        "hf-mirror",
        "huggingface",
    ]
