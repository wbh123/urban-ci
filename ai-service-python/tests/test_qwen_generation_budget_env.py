from pathlib import Path

import pytest

from app.adapters.qwen3_vl_classifier import Qwen3VlClassifier


class FakeBackend:
    def close(self):
        pass


def test_qwen_token_budget_reads_environment(monkeypatch):
    monkeypatch.setenv("AI_QWEN_MAX_NEW_TOKENS", "96")
    classifier = Qwen3VlClassifier(Path("unused"), backend=FakeBackend())
    assert classifier.max_new_tokens == 96


def test_qwen_token_budget_rejects_invalid_environment(monkeypatch):
    monkeypatch.setenv("AI_QWEN_MAX_NEW_TOKENS", "abc")
    with pytest.raises(ValueError, match="AI_QWEN_MAX_NEW_TOKENS"):
        Qwen3VlClassifier(Path("unused"), backend=FakeBackend())
