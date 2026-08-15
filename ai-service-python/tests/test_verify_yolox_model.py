from __future__ import annotations

import hashlib
from pathlib import Path

import pytest

from app.errors import ModelUnavailableError
from app.model_manifest import ModelManifestError, load_model_manifest
from tools.verify_yolox_model import inspect_yolox_session, load_candidate_profile, verification_report


class FakeNode:
    def __init__(self, name, shape, type_="tensor(float)"):
        self.name = name
        self.shape = shape
        self.type = type_


class FakeAssignment:
    def __init__(self, ep_name):
        self.ep_name = ep_name


class FakeSession:
    def __init__(self, output_shape=(1, 8400, 12), graph_providers=None):
        self.output_shape = output_shape
        self.graph_providers = graph_providers or ["CUDAExecutionProvider"]

    def get_providers(self):
        return ["CUDAExecutionProvider"]

    def get_inputs(self):
        return [FakeNode("images", [1, 3, 640, 640])]

    def get_outputs(self):
        return [FakeNode("output", list(self.output_shape))]

    def get_provider_graph_assignment_info(self):
        return [FakeAssignment(provider) for provider in self.graph_providers]


def _profile(tmp_path: Path) -> Path:
    source = Path(__file__).parents[1] / "config" / "yolox-building-defect.candidate.json"
    target = tmp_path / "candidate.json"
    target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
    return target


def test_candidate_profile_is_valid_but_cannot_enter_formal_runtime(tmp_path: Path):
    path = _profile(tmp_path)
    profile = load_candidate_profile(path)
    assert profile["status"] == "CANDIDATE"
    assert profile["candidateOnly"] is True
    assert profile["upstream"]["license"] == "Apache-2.0"
    with pytest.raises(ModelManifestError, match="APPROVED"):
        load_model_manifest(path, tmp_path)


def test_inspect_session_accepts_exact_yolox_cuda_contract(tmp_path: Path):
    profile = load_candidate_profile(_profile(tmp_path))
    contract = inspect_yolox_session(FakeSession(), profile)
    assert contract["inputShape"] == [1, 3, 640, 640]
    assert contract["outputShape"] == [1, 8400, 12]
    assert contract["executionProvider"] == "CUDAExecutionProvider"


def test_inspect_session_rejects_wrong_class_dimension(tmp_path: Path):
    profile = load_candidate_profile(_profile(tmp_path))
    with pytest.raises(ModelUnavailableError, match="输出形状"):
        inspect_yolox_session(FakeSession(output_shape=(1, 8400, 11)), profile)


def test_inspect_session_rejects_cpu_graph_assignment(tmp_path: Path):
    profile = load_candidate_profile(_profile(tmp_path))
    with pytest.raises(ModelUnavailableError, match="CPUExecutionProvider"):
        inspect_yolox_session(FakeSession(graph_providers=["CUDAExecutionProvider", "CPUExecutionProvider"]), profile)


def test_verification_report_records_digest_but_does_not_approve_model(tmp_path: Path):
    profile = load_candidate_profile(_profile(tmp_path))
    weight = tmp_path / "model.onnx"
    weight.write_bytes(b"future-real-yolox-weight")
    report = verification_report(profile, weight, inspect_yolox_session(FakeSession(), profile))
    assert report["modelId"] == "AI-BUILDING-YOLOX-001"
    assert report["weightSha256"] == hashlib.sha256(weight.read_bytes()).hexdigest()
    assert report["runtimeContractPassed"] is True
    assert report["statusRemains"] == "CANDIDATE"
    assert report["eligibleForApprovalReview"] is True
