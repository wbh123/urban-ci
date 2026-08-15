from pathlib import Path

import pytest
from PIL import Image

from app.adapters.qwen3_vl_classifier import Qwen3VlClassifier
from app.errors import ModelUnavailableError


ALL_SCORES_JSON = (
    '{"scores":{'
    '"CRACK":0.88,'
    '"SPALLING":0.08,'
    '"EXPOSED_REBAR":0.07,'
    '"CORROSION":0.11,'
    '"WATER_STAIN":0.32,'
    '"SURFACE_DAMAGE":0.21'
    '}}'
)


class FakeBackend:
    def __init__(self, output):
        self.output = output
        self.closed = False
        self.prompts = []

    def generate_json(self, image, prompt):
        self.prompts.append(prompt)
        return self.output

    def close(self):
        self.closed = True


class SequenceBackend(FakeBackend):
    def __init__(self, outputs):
        super().__init__(None)
        self.outputs = list(outputs)

    def generate_json(self, image, prompt):
        self.prompts.append(prompt)
        return self.outputs.pop(0)


def test_qwen_classifier_requires_and_parses_all_six_presence_scores():
    backend = FakeBackend(ALL_SCORES_JSON)
    classifier = Qwen3VlClassifier(Path("unused"), backend=backend)
    result = classifier.classify(Image.new("RGB", (64, 64)))
    assert set(result.classes) == {
        "CRACK", "SPALLING", "EXPOSED_REBAR", "CORROSION", "WATER_STAIN", "SURFACE_DAMAGE"
    }
    assert result.classes["CRACK"].present is True
    assert result.classes["CRACK"].confidence == 0.88
    assert result.classes["WATER_STAIN"].present is False
    assert "CRACK" in backend.prompts[0]
    assert "SURFACE_DAMAGE" in backend.prompts[0]
    assert "六类" in backend.prompts[0]


def test_qwen_prompt_is_neutral_and_contains_no_prefilled_positive_answer():
    backend = FakeBackend(ALL_SCORES_JSON)
    classifier = Qwen3VlClassifier(Path("unused"), backend=backend)
    classifier.classify(Image.new("RGB", (64, 64)))
    prompt = backend.prompts[0]
    assert '"present":true' not in prompt.replace(" ", "").lower()
    assert '"crack":0.95' not in prompt.replace(" ", "").lower()
    assert "不要默认 CRACK" in prompt
    assert "六类全部低分" in prompt
    assert "多类同时存在" in prompt


def test_qwen_presence_score_thresholds_are_mapped_by_code_not_model_booleans():
    payload = (
        '{"scores":{'
        '"CRACK":0.70,'
        '"SPALLING":0.69,'
        '"EXPOSED_REBAR":0.45,'
        '"CORROSION":0.44,'
        '"WATER_STAIN":1.0,'
        '"SURFACE_DAMAGE":0.0'
        '}}'
    )
    classifier = Qwen3VlClassifier(Path("unused"), backend=FakeBackend(payload))
    result = classifier.classify(Image.new("RGB", (64, 64)))
    assert result.classes["CRACK"].present is True
    assert result.classes["SPALLING"].present is None
    assert result.classes["EXPOSED_REBAR"].present is None
    assert result.classes["CORROSION"].present is False
    assert result.classes["WATER_STAIN"].present is True
    assert result.classes["SURFACE_DAMAGE"].present is False


def test_qwen_classifier_missing_score_retries_once():
    incomplete = '{"scores":{"CRACK":0.88,"WATER_STAIN":0.12}}'
    backend = SequenceBackend([incomplete, ALL_SCORES_JSON])
    classifier = Qwen3VlClassifier(Path("unused"), backend=backend)
    result = classifier.classify(Image.new("RGB", (64, 64)))
    assert result.classes["CRACK"].present is True
    assert len(backend.prompts) == 2


def test_qwen_classifier_parses_json_markdown_fence():
    classifier = Qwen3VlClassifier(
        Path("unused"), backend=FakeBackend(f"```json\n{ALL_SCORES_JSON}\n```")
    )
    result = classifier.classify(Image.new("RGB", (64, 64)))
    assert result.classes["CRACK"].confidence == 0.88


def test_qwen_classifier_extracts_json_after_explanatory_prefix_and_suffix():
    classifier = Qwen3VlClassifier(
        Path("unused"), backend=FakeBackend(f"分析完成，结果如下：\n{ALL_SCORES_JSON}\n以上为识别结果。")
    )
    result = classifier.classify(Image.new("RGB", (64, 64)))
    assert result.classes["CRACK"].present is True


def test_qwen_classifier_retries_once_after_malformed_output():
    backend = SequenceBackend([
        "识别结果：存在裂缝，但第一次没有返回 JSON。",
        ALL_SCORES_JSON,
    ])
    classifier = Qwen3VlClassifier(Path("unused"), backend=backend)
    result = classifier.classify(Image.new("RGB", (64, 64)))
    assert result.classes["CRACK"].present is True
    assert len(backend.prompts) == 2
    assert "必须只输出一个 JSON 对象" in backend.prompts[1]


def test_qwen_classifier_rejects_non_json_output():
    backend = SequenceBackend(["这张图有裂缝", "仍然不是 JSON"])
    classifier = Qwen3VlClassifier(Path("unused"), backend=backend)
    with pytest.raises(ModelUnavailableError, match="JSON"):
        classifier.classify(Image.new("RGB", (64, 64)))


def test_qwen_classifier_invalid_output_error_contains_raw_preview():
    backend = SequenceBackend([
        "识别结果：CRACK=true，但没有 JSON",
        "第二次仍然没有 JSON",
    ])
    classifier = Qwen3VlClassifier(Path("unused"), backend=backend)
    with pytest.raises(ModelUnavailableError, match="first_raw="):
        classifier.classify(Image.new("RGB", (64, 64)))


def test_qwen_classifier_rejects_unknown_class_code():
    invalid = ALL_SCORES_JSON.replace('"SURFACE_DAMAGE":0.21', '"ALIEN":0.9')
    classifier = Qwen3VlClassifier(
        Path("unused"), backend=SequenceBackend([invalid, invalid])
    )
    with pytest.raises(ModelUnavailableError, match="未知病害类别"):
        classifier.classify(Image.new("RGB", (64, 64)))


def test_qwen_classifier_rejects_non_numeric_presence_score():
    invalid = ALL_SCORES_JSON.replace('"SPALLING":0.08', '"SPALLING":"no"')
    classifier = Qwen3VlClassifier(
        Path("unused"), backend=SequenceBackend([invalid, invalid])
    )
    with pytest.raises(ModelUnavailableError, match="presenceScore"):
        classifier.classify(Image.new("RGB", (64, 64)))


def test_qwen_default_generation_budget_is_128():
    classifier = Qwen3VlClassifier(Path("unused"), backend=FakeBackend(ALL_SCORES_JSON))
    assert classifier.max_new_tokens == 128


@pytest.mark.parametrize("value", [0, 31, 257])
def test_qwen_rejects_invalid_generation_budget(value):
    with pytest.raises(ValueError, match="max_new_tokens"):
        Qwen3VlClassifier(
            Path("unused"),
            backend=FakeBackend(ALL_SCORES_JSON),
            max_new_tokens=value,
        )


def test_qwen_custom_generation_budget_is_exposed_for_benchmarking():
    classifier = Qwen3VlClassifier(
        Path("unused"),
        backend=FakeBackend(ALL_SCORES_JSON),
        max_new_tokens=96,
    )
    assert classifier.max_new_tokens == 96


def test_qwen_classifier_close_delegates_to_backend():
    backend = FakeBackend(ALL_SCORES_JSON)
    classifier = Qwen3VlClassifier(Path("unused"), backend=backend)
    classifier.close()
    assert backend.closed is True
