"""离线校验 YOLOX 建筑病害候选 ONNX；通过校验仍保持 CANDIDATE，不自动批准。"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from app.errors import ModelUnavailableError
from app.yolox_manifest import YOLOX_BUILDING_DEFECT_CODES


CUDA_EXECUTION_PROVIDER = "CUDAExecutionProvider"
CPU_EXECUTION_PROVIDER = "CPUExecutionProvider"
DISABLE_CPU_FALLBACK_KEY = "session.disable_cpu_ep_fallback"
RECORD_EP_GRAPH_ASSIGNMENT_KEY = "session.record_ep_graph_assignment_info"


def load_candidate_profile(path: str | Path) -> dict[str, Any]:
    profile_path = Path(path)
    try:
        payload = json.loads(profile_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as ex:
        raise ValueError("YOLOX candidate profile 不是合法 UTF-8 JSON") from ex
    if not isinstance(payload, dict):
        raise ValueError("YOLOX candidate profile 根节点必须是对象")
    if payload.get("schemaVersion") != 1 or payload.get("candidateOnly") is not True:
        raise ValueError("YOLOX candidate profile 必须声明 schemaVersion=1 和 candidateOnly=true")
    if payload.get("status") != "CANDIDATE":
        raise ValueError("YOLOX candidate profile 状态必须为 CANDIDATE")
    if payload.get("task") != "BUILDING_DEFECT_DETECTION":
        raise ValueError("YOLOX candidate profile 任务不匹配")
    if payload.get("adapter") != "yolox-building-defect-v1":
        raise ValueError("YOLOX candidate profile 适配器不匹配")

    classes = payload.get("classes")
    if not isinstance(classes, list):
        raise ValueError("YOLOX candidate profile classes 必须是数组")
    codes = tuple(item.get("code") for item in classes if isinstance(item, dict))
    if codes != YOLOX_BUILDING_DEFECT_CODES:
        raise ValueError("YOLOX candidate profile 类别合同不匹配")

    input_spec = payload.get("input")
    if not isinstance(input_spec, dict):
        raise ValueError("YOLOX candidate profile input 必须是对象")
    width = _positive_int(input_spec.get("width"), "input.width")
    height = _positive_int(input_spec.get("height"), "input.height")
    if width % 32 != 0 or height % 32 != 0:
        raise ValueError("YOLOX candidate 输入尺寸必须为 32 的倍数")
    pad = input_spec.get("padValue")
    if isinstance(pad, bool) or not isinstance(pad, int) or not 0 <= pad <= 255:
        raise ValueError("YOLOX candidate input.padValue 必须位于 0~255")

    thresholds = payload.get("thresholds")
    if not isinstance(thresholds, dict):
        raise ValueError("YOLOX candidate thresholds 必须是对象")
    _ratio(thresholds.get("score"), "thresholds.score", open_zero=True)
    _ratio(thresholds.get("nmsIou"), "thresholds.nmsIou", open_zero=True)
    _positive_int(thresholds.get("maximumDetections"), "thresholds.maximumDetections")

    upstream = payload.get("upstream")
    if not isinstance(upstream, dict):
        raise ValueError("YOLOX candidate upstream 必须是对象")
    revision = upstream.get("revision")
    if not isinstance(revision, str) or len(revision) != 40 or any(ch not in "0123456789abcdef" for ch in revision.lower()):
        raise ValueError("YOLOX candidate upstream.revision 必须为固定 40 位 commit SHA")
    if upstream.get("license") != "Apache-2.0":
        raise ValueError("YOLOX candidate upstream.license 必须明确为 Apache-2.0")
    return payload


def inspect_yolox_session(session: Any, profile: dict[str, Any]) -> dict[str, Any]:
    """校验候选 ONNX 的静态输入输出和实际 CUDA 图分配。"""

    providers = list(session.get_providers())
    if not providers or providers[0] != CUDA_EXECUTION_PROVIDER:
        raise ModelUnavailableError("YOLOX 候选模型未运行在 CUDAExecutionProvider")

    assignments_fn = getattr(session, "get_provider_graph_assignment_info", None)
    if not callable(assignments_fn):
        raise ModelUnavailableError("ONNX Runtime 不支持执行图分配校验")
    assignments = list(assignments_fn())
    if not assignments:
        raise ModelUnavailableError("ONNX Runtime 未返回执行图分配信息")
    for assignment in assignments:
        ep_name = getattr(assignment, "ep_name", None)
        if ep_name == CPU_EXECUTION_PROVIDER:
            raise ModelUnavailableError("检测到 CPUExecutionProvider 图分配")
        if ep_name != CUDA_EXECUTION_PROVIDER:
            raise ModelUnavailableError(f"检测到非 CUDA 执行图分配：{ep_name}")

    width = int(profile["input"]["width"])
    height = int(profile["input"]["height"])
    class_count = len(profile["classes"])
    candidate_count = sum((height // stride) * (width // stride) for stride in (8, 16, 32))
    expected_input = [1, 3, height, width]
    expected_output = [1, candidate_count, 5 + class_count]

    inputs = list(session.get_inputs())
    outputs = list(session.get_outputs())
    if len(inputs) != 1 or getattr(inputs[0], "name", None) != "images":
        raise ModelUnavailableError("YOLOX 候选 ONNX 输入契约不匹配")
    if len(outputs) != 1 or getattr(outputs[0], "name", None) != "output":
        raise ModelUnavailableError("YOLOX 候选 ONNX 输出契约不匹配")
    if _shape(getattr(inputs[0], "shape", None)) != expected_input:
        raise ModelUnavailableError("YOLOX 候选 ONNX 输入形状不匹配")
    if _shape(getattr(outputs[0], "shape", None)) != expected_output:
        raise ModelUnavailableError("YOLOX 候选 ONNX 输出形状不匹配")
    if getattr(inputs[0], "type", None) not in (None, "tensor(float)"):
        raise ModelUnavailableError("YOLOX 候选 ONNX 输入必须为 float32")
    if getattr(outputs[0], "type", None) not in (None, "tensor(float)"):
        raise ModelUnavailableError("YOLOX 候选 ONNX 输出必须为 float32")
    return {
        "executionProvider": CUDA_EXECUTION_PROVIDER,
        "inputName": "images",
        "outputName": "output",
        "inputShape": expected_input,
        "outputShape": expected_output,
    }


def verification_report(profile: dict[str, Any], weight_path: str | Path, contract: dict[str, Any]) -> dict[str, Any]:
    weight = Path(weight_path)
    if not weight.is_file():
        raise ValueError("YOLOX 候选 ONNX 文件不存在")
    return {
        "modelId": profile["modelId"],
        "targetVersion": profile["targetVersion"],
        "weightFile": weight.name,
        "weightSha256": _sha256(weight),
        "runtimeContractPassed": True,
        "runtimeContract": contract,
        "statusRemains": "CANDIDATE",
        "eligibleForApprovalReview": True,
        "approvalRequirements": list(profile.get("approvalRequirements", [])),
    }


def verify_onnx(profile_path: str | Path, weight_path: str | Path, cuda_device_id: int = 0) -> dict[str, Any]:
    """创建真实 CUDA 会话执行静态契约校验；不会生成 APPROVED manifest。"""

    profile = load_candidate_profile(profile_path)
    try:
        import onnxruntime as ort
    except ImportError as ex:
        raise ModelUnavailableError("未安装 onnxruntime-gpu") from ex

    if CUDA_EXECUTION_PROVIDER not in set(ort.get_available_providers()):
        raise ModelUnavailableError("ONNX Runtime 未提供 CUDAExecutionProvider")
    options = ort.SessionOptions()
    options.add_session_config_entry(DISABLE_CPU_FALLBACK_KEY, "1")
    options.add_session_config_entry(RECORD_EP_GRAPH_ASSIGNMENT_KEY, "1")
    providers = [(CUDA_EXECUTION_PROVIDER, {"device_id": str(cuda_device_id), "do_copy_in_default_stream": "1"})]
    try:
        session = ort.InferenceSession(str(Path(weight_path)), sess_options=options, providers=providers)
        disable_fallback = getattr(session, "disable_fallback", None)
        if not callable(disable_fallback):
            raise ModelUnavailableError("ONNX Runtime 不支持禁用执行后端回退")
        disable_fallback()
        contract = inspect_yolox_session(session, profile)
        return verification_report(profile, weight_path, contract)
    except ModelUnavailableError:
        raise
    except Exception as ex:
        raise ModelUnavailableError("YOLOX 候选 ONNX CUDA 校验失败") from ex


def _shape(value: Any) -> list[int] | None:
    if value is None:
        return None
    try:
        return [int(item) for item in value]
    except (TypeError, ValueError):
        return None


def _positive_int(value: Any, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{field} 必须是正整数")
    return value


def _ratio(value: Any, field: str, *, open_zero: bool) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{field} 必须是数字")
    numeric = float(value)
    lower = numeric > 0.0 if open_zero else numeric >= 0.0
    if not lower or numeric > 1.0:
        raise ValueError(f"{field} 必须位于 0~1 范围")
    return numeric


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description="校验 YOLOX 建筑病害候选 ONNX，不自动批准模型")
    parser.add_argument("--profile", default="config/yolox-building-defect.candidate.json")
    parser.add_argument("--onnx", required=True)
    parser.add_argument("--cuda-device-id", type=int, default=0)
    parser.add_argument("--output")
    args = parser.parse_args()
    report = verify_onnx(args.profile, args.onnx, args.cuda_device_id)
    text = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        Path(args.output).write_text(text + "\n", encoding="utf-8")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
