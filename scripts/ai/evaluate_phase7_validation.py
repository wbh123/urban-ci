#!/usr/bin/env python3
"""Evaluate fixed phase-7 AI validation results without online calls or CUDA."""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any

NEGATIVE_CATEGORIES = {"hard_negative", "low_quality", "not_applicable"}
NEGATIVE_LABELS = {
    "", "NONE", "NORMAL", "UNKNOWN", "STAIN", "SHADOW", "BRICK_JOINT",
    "BLUR", "OVEREXPOSED", "UNDEREXPOSED", "OCCLUDED", "NOT_APPLICABLE",
}
LOW_QUALITY_CATEGORY = "low_quality"
NOT_APPLICABLE_CATEGORY = "not_applicable"
LOW_QUALITY_ERROR_CODE = "AI_IMAGE_LOW_QUALITY"
NOT_APPLICABLE_ERROR_CODE = "AI_IMAGE_NOT_APPLICABLE"
REQUIRED_STRUCTURED_FIELDS = {
    "summary", "detections", "riskSignals", "recommendations", "warnings", "confidence",
}


def read_manifest(path: Path) -> dict[str, dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    result: dict[str, dict[str, str]] = {}
    for row in rows:
        sample_id = (row.get("sample_id") or "").strip()
        if not sample_id:
            raise ValueError("manifest contains a row without sample_id")
        if sample_id in result:
            raise ValueError(f"duplicate sample_id in manifest: {sample_id}")
        result[sample_id] = row
    return result


def read_results(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"invalid JSONL at line {line_no}: {exc}") from exc
            if not isinstance(value, dict):
                raise ValueError(f"result line {line_no} must be an object")
            rows.append(value)
    return rows


def structured_payload(row: dict[str, Any]) -> dict[str, Any] | None:
    value = row.get("structuredResult")
    return value if isinstance(value, dict) else None


def validate_structured(row: dict[str, Any]) -> tuple[bool, list[str]]:
    payload = structured_payload(row)
    if payload is None:
        return False, ["structuredResult missing"]
    errors: list[str] = []
    missing = sorted(REQUIRED_STRUCTURED_FIELDS - payload.keys())
    if missing:
        errors.append("missing fields: " + ",".join(missing))
    if not isinstance(payload.get("summary"), str) or not payload.get("summary", "").strip():
        errors.append("summary empty")
    for field in ("detections", "riskSignals", "recommendations", "warnings"):
        if not isinstance(payload.get(field), list):
            errors.append(f"{field} must be an array")
    confidence = payload.get("confidence")
    if confidence is not None and (
        not isinstance(confidence, (int, float)) or isinstance(confidence, bool)
        or confidence < 0 or confidence > 1
    ):
        errors.append("confidence must be between 0 and 1")
    return not errors, errors


def expected_positive(manifest_row: dict[str, str]) -> bool | None:
    category = (manifest_row.get("primary_category") or "").strip().lower()
    label = (manifest_row.get("secondary_label") or "").strip().upper()
    review = (manifest_row.get("needs_manual_review") or "").strip().lower()
    if review in {"true", "1", "yes"} or label == "UNKNOWN":
        return None
    if category in NEGATIVE_CATEGORIES or label in NEGATIVE_LABELS:
        return False
    if category in {"obvious_defect", "difficult_defect"}:
        return True
    return None


def expected_precheck_rejection(manifest_row: dict[str, str]) -> bool:
    category = (manifest_row.get("primary_category") or "").strip().lower()
    return category == LOW_QUALITY_CATEGORY


def expected_semantic_rejection(manifest_row: dict[str, str]) -> bool | None:
    """返回本地语义门禁真值；UNKNOWN/待人工复核样本不强行作为语义门禁真值。"""

    explicit = str(
        manifest_row.get("expected_applicability")
        or manifest_row.get("expectedApplicability")
        or ""
    ).strip().upper()
    if explicit == "NOT_APPLICABLE":
        return True
    if explicit in {"APPLICABLE", "UNCERTAIN"}:
        return False

    category = (manifest_row.get("primary_category") or "").strip().lower()
    label = (manifest_row.get("secondary_label") or "").strip().upper()
    review = (manifest_row.get("needs_manual_review") or "").strip().lower()
    if review in {"true", "1", "yes"} or label == "UNKNOWN":
        return None
    if category == NOT_APPLICABLE_CATEGORY or label == "NOT_APPLICABLE":
        return True
    if category in {"obvious_defect", "difficult_defect", "hard_negative"}:
        return False
    return None


def result_error_code(row: dict[str, Any]) -> str:
    direct = str(row.get("errorCode") or "").strip().upper()
    if direct:
        return direct
    error = row.get("error")
    if isinstance(error, dict):
        return str(error.get("errorCode") or error.get("code") or "").strip().upper()
    return ""


def is_low_quality_precheck_rejection(row: dict[str, Any]) -> bool:
    return (
        str(row.get("status") or "").strip().upper() == "REJECTED"
        and result_error_code(row) == LOW_QUALITY_ERROR_CODE
    )


def is_not_applicable_rejection(row: dict[str, Any]) -> bool:
    return (
        str(row.get("status") or "").strip().upper() == "REJECTED"
        and result_error_code(row) == NOT_APPLICABLE_ERROR_CODE
    )


def did_call_dify(row: dict[str, Any]) -> bool | None:
    explicit = row.get("difyActuallyCalled")
    if isinstance(explicit, bool):
        return explicit
    raw_reference = str(row.get("rawResponseReference") or "").strip().lower()
    if raw_reference:
        return raw_reference.startswith("dify:")
    return None


def is_semantic_precheck_rejection(row: dict[str, Any]) -> bool:
    """识别 Dify 前由本地语义门禁产生的 AI_IMAGE_NOT_APPLICABLE。"""

    if not is_not_applicable_rejection(row):
        return False
    dify_called = did_call_dify(row)
    if dify_called is False:
        return True
    # 兼容本地采集器旧格式：明确执行过 precheck 且没有 Dify 原始响应引用。
    if dify_called is None and row.get("precheckCalled") is True:
        raw_reference = str(row.get("rawResponseReference") or "").strip()
        return not raw_reference
    return False


def predicted_labels(row: dict[str, Any]) -> set[str]:
    payload = structured_payload(row) or {}
    labels: set[str] = set()
    for item in payload.get("detections") or []:
        if isinstance(item, dict):
            code = str(item.get("classCode") or "").strip().upper()
            if code and code not in NEGATIVE_LABELS:
                labels.add(code)
    return labels


def percentile(values: list[float], percentile_value: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(percentile_value * len(ordered)) - 1))
    return ordered[index]


def jaccard(a: set[str], b: set[str]) -> float:
    if not a and not b:
        return 1.0
    union = a | b
    return len(a & b) / len(union) if union else 1.0


def evaluate(
    manifest: dict[str, dict[str, str]],
    results: list[dict[str, Any]],
    provider_filter: str | None = None,
) -> tuple[dict[str, Any], list[dict[str, str]]]:
    filtered = [
        row for row in results
        if provider_filter is None
        or str(row.get("providerCode") or "").upper() == provider_filter.upper()
    ]
    errors: list[dict[str, str]] = []
    durations: list[float] = []
    costs: list[float] = []
    valid_count = structured_required = succeeded = completed = 0
    expected_precheck_rows = correct_precheck_rejections = unexpected_precheck_rejections = 0
    expected_semantic_rows = semantic_precheck_rejections = 0
    correct_semantic_rejections = unexpected_semantic_rejections = 0
    expected_semantic_entered_dify = 0
    not_applicable_rejections = 0
    tp = fp = fn = tn = 0
    labeled = 0
    runs_by_sample: dict[str, list[set[str]]] = defaultdict(list)
    category_counts: dict[str, int] = defaultdict(int)

    for index, row in enumerate(filtered, start=1):
        sample_id = str(row.get("sampleId") or row.get("sample_id") or "").strip()
        if not sample_id or sample_id not in manifest:
            errors.append({"row": str(index), "sampleId": sample_id, "error": "unknown sampleId"})
            continue
        completed += 1
        manifest_row = manifest[sample_id]
        category = (manifest_row.get("primary_category") or "unknown").strip()
        category_counts[category] += 1
        status = str(row.get("status") or "").upper()
        if status == "SUCCEEDED":
            succeeded += 1

        expects_precheck = expected_precheck_rejection(manifest_row)
        if expects_precheck:
            expected_precheck_rows += 1
        expected_semantic = expected_semantic_rejection(manifest_row)
        if expected_semantic is True:
            expected_semantic_rows += 1
            if did_call_dify(row) is True:
                expected_semantic_entered_dify += 1

        low_quality_rejected = is_low_quality_precheck_rejection(row)
        not_applicable_rejected = is_not_applicable_rejection(row)
        semantic_rejected = is_semantic_precheck_rejection(row)
        if not_applicable_rejected:
            not_applicable_rejections += 1
        if semantic_rejected:
            semantic_precheck_rejections += 1
            if expected_semantic is True:
                correct_semantic_rejections += 1
            elif expected_semantic is False:
                unexpected_semantic_rejections += 1
                errors.append({
                    "row": str(index),
                    "sampleId": sample_id,
                    "error": "unexpected local semantic AI_IMAGE_NOT_APPLICABLE rejection",
                })

        if low_quality_rejected:
            if expects_precheck:
                correct_precheck_rejections += 1
            else:
                unexpected_precheck_rejections += 1
                errors.append({
                    "row": str(index),
                    "sampleId": sample_id,
                    "error": "unexpected AI_IMAGE_LOW_QUALITY rejection",
                })

        local_rejection = low_quality_rejected or semantic_rejected
        if not local_rejection:
            structured_required += 1
            valid, validation_errors = validate_structured(row)
            if valid:
                valid_count += 1
            else:
                errors.append({
                    "row": str(index),
                    "sampleId": sample_id,
                    "error": "; ".join(validation_errors),
                })

        duration = row.get("durationMs")
        if isinstance(duration, (int, float)) and not isinstance(duration, bool) and duration >= 0:
            durations.append(float(duration))
        cost = row.get("estimatedCost")
        if isinstance(cost, (int, float)) and not isinstance(cost, bool) and cost >= 0:
            costs.append(float(cost))

        expected = expected_positive(manifest_row)
        if local_rejection:
            labels: set[str] = set()
            runs_by_sample[sample_id].append(labels)
            if expected is None:
                continue
            labeled += 1
            if expected:
                fn += 1
            else:
                tn += 1
            continue

        labels = predicted_labels(row)
        runs_by_sample[sample_id].append(labels)
        if expected is None or (status != "SUCCEEDED" and not not_applicable_rejected):
            continue
        labeled += 1
        predicted = bool(labels)
        if expected and predicted:
            tp += 1
        elif expected and not predicted:
            fn += 1
        elif not expected and predicted:
            fp += 1
        else:
            tn += 1

    pair_scores: list[float] = []
    for runs in runs_by_sample.values():
        if len(runs) < 2:
            continue
        for left in range(len(runs)):
            for right in range(left + 1, len(runs)):
                pair_scores.append(jaccard(runs[left], runs[right]))

    total = len(filtered)
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    specificity = tn / (tn + fp) if tn + fp else 0.0
    terminal_accepted = succeeded + correct_precheck_rejections + not_applicable_rejections
    metrics = {
        "providerFilter": provider_filter,
        "resultRows": total,
        "matchedRows": completed,
        "succeededRows": succeeded,
        "successRate": succeeded / total if total else 0.0,
        "terminalAcceptedRows": terminal_accepted,
        "terminalAcceptedRate": terminal_accepted / total if total else 0.0,
        "structuredRequiredRows": structured_required,
        "structuredValidRows": valid_count,
        "structuredValidRate": valid_count / structured_required if structured_required else 1.0,
        "expectedPrecheckRows": expected_precheck_rows,
        "correctPrecheckRejections": correct_precheck_rejections,
        "unexpectedPrecheckRejections": unexpected_precheck_rejections,
        "precheckRejectionRate": (
            correct_precheck_rejections / expected_precheck_rows
            if expected_precheck_rows else None
        ),
        "expectedSemanticPrecheckRows": expected_semantic_rows,
        "semanticPrecheckRejections": semantic_precheck_rejections,
        "correctSemanticPrecheckRejections": correct_semantic_rejections,
        "unexpectedSemanticPrecheckRejections": unexpected_semantic_rejections,
        "semanticPrecheckRejectionRate": (
            correct_semantic_rejections / expected_semantic_rows
            if expected_semantic_rows else None
        ),
        "expectedSemanticRowsEnteredDify": expected_semantic_entered_dify,
        "notApplicableRejections": not_applicable_rejections,
        "averageDurationMs": statistics.fmean(durations) if durations else 0.0,
        "p95DurationMs": percentile(durations, 0.95),
        "totalEstimatedCost": sum(costs),
        "averageEstimatedCost": statistics.fmean(costs) if costs else 0.0,
        "labeledRows": labeled,
        "confusionMatrix": {
            "truePositive": tp,
            "falsePositive": fp,
            "falseNegative": fn,
            "trueNegative": tn,
        },
        "precision": precision,
        "recall": recall,
        "specificity": specificity,
        "repeatLabelJaccard": statistics.fmean(pair_scores) if pair_scores else None,
        "repeatPairCount": len(pair_scores),
        "categoryCounts": dict(sorted(category_counts.items())),
        "errorCount": len(errors),
    }
    return metrics, errors


def _format_optional_rate(value: Any) -> str:
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return f"{value:.2%}"
    return "N/A"


def write_outputs(output_dir: Path, metrics: dict[str, Any], errors: list[dict[str, str]]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "metrics.json").write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    with (output_dir / "errors.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["row", "sampleId", "error"])
        writer.writeheader()
        writer.writerows(errors)
    summary = f"""# 第七阶段固定样本评估摘要

- 结果行数：{metrics['resultRows']}
- 匹配样本数：{metrics['matchedRows']}
- 模型调用成功率：{metrics['successRate']:.2%}
- 业务可接受终态率：{metrics['terminalAcceptedRate']:.2%}
- 需要结构化结果的行数：{metrics['structuredRequiredRows']}
- 结构化输出通过率：{metrics['structuredValidRate']:.2%}
- 预期低质量门禁样本数：{metrics['expectedPrecheckRows']}
- 正确低质量门禁拒绝数：{metrics['correctPrecheckRejections']}
- 低质量门禁命中率：{_format_optional_rate(metrics['precheckRejectionRate'])}
- 非预期低质量门禁拒绝数：{metrics['unexpectedPrecheckRejections']}
- 预期语义门禁拒绝样本数：{metrics['expectedSemanticPrecheckRows']}
- 本地语义门禁拒绝数：{metrics['semanticPrecheckRejections']}
- 正确本地语义门禁拒绝数：{metrics['correctSemanticPrecheckRejections']}
- 非预期本地语义门禁拒绝数：{metrics['unexpectedSemanticPrecheckRejections']}
- 本地语义门禁命中率：{_format_optional_rate(metrics['semanticPrecheckRejectionRate'])}
- 应被本地语义门禁拒绝但仍进入 Dify 的结果数：{metrics['expectedSemanticRowsEnteredDify']}
- 全链路不适用场景稳定拒绝数：{metrics['notApplicableRejections']}
- 平均耗时：{metrics['averageDurationMs']:.2f} ms
- 第 95 百分位耗时：{metrics['p95DurationMs']:.2f} ms
- 总估算费用：{metrics['totalEstimatedCost']:.6f}
- 有人工参考标签的结果：{metrics['labeledRows']}
- 精确率：{metrics['precision']:.2%}
- 召回率：{metrics['recall']:.2%}
- 负样本识别率：{metrics['specificity']:.2%}
- 重复调用标签一致性：{metrics['repeatLabelJaccard']}
- 结构或样本错误数：{metrics['errorCount']}

> 本报告用于固定诊断集质量比较，不等同于法定房屋安全鉴定。低质量或本地语义门禁在 Dify 前正确拒绝属于有效业务终态，不要求结构化 Dify 结果；由 Dify 返回的 `AI_IMAGE_NOT_APPLICABLE` 仍必须具有合法结构化结果。没有人工确认适用性标签的 UNKNOWN 样本不强行作为本地语义门禁真值。
"""
    (output_dir / "summary.md").write_text(summary, encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--provider")
    parser.add_argument("--minimum-structured-valid-rate", type=float, default=0.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    manifest = read_manifest(args.manifest)
    results = read_results(args.results)
    metrics, errors = evaluate(manifest, results, args.provider)
    write_outputs(args.output_dir, metrics, errors)
    if metrics["structuredValidRate"] < args.minimum_structured_valid_rate:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
