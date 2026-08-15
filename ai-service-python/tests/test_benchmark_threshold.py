"""benchmark_vision 阈值覆盖逻辑测试（CLI > 环境变量 > 默认）。"""

from tools.benchmark_vision import _threshold_overrides


def test_cli_overrides_env_and_default():
    env = {"URBAN_SAFE_AI_VISION_BOX_THRESHOLD": "0.25", "URBAN_SAFE_AI_VISION_TEXT_THRESHOLD": "0.25"}
    overrides = _threshold_overrides(0.30, 0.30, env)
    assert float(overrides["URBAN_SAFE_AI_VISION_BOX_THRESHOLD"]) == 0.30
    assert float(overrides["URBAN_SAFE_AI_VISION_TEXT_THRESHOLD"]) == 0.30


def test_cli_partial_override_only_sets_provided():
    overrides = _threshold_overrides(0.35, None, {})
    assert float(overrides["URBAN_SAFE_AI_VISION_BOX_THRESHOLD"]) == 0.35
    assert "URBAN_SAFE_AI_VISION_TEXT_THRESHOLD" not in overrides


def test_no_cli_means_no_override():
    assert _threshold_overrides(None, None, {"URBAN_SAFE_AI_VISION_BOX_THRESHOLD": "0.25"}) == {}


def test_precedence_cli_beats_env_value():
    env = {"URBAN_SAFE_AI_VISION_BOX_THRESHOLD": "0.25"}
    assert float(_threshold_overrides(0.40, None, env)["URBAN_SAFE_AI_VISION_BOX_THRESHOLD"]) == 0.40
