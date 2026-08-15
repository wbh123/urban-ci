"""基于已生成的 FAST/PRECISION benchmark JSON 做人工真值质量评估。

该工具不重新运行 GPU 推理，而是读取：
1. data/validation/ai-vision/quality-manifest.json 中人工确认的图片级标签；
2. data/model-benchmarks/vision-precision-comparison.json 中已保存的逐图预测。

只统计 reviewStatus=CONFIRMED/REVIEWED/APPROVED 的样本。PENDING 不参与指标，
避免把未人工确认的数据当作真值。这里计算的是“图片级类别识别”指标，不代表
目标框定位精度或分割精度。
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
REVIEWED_STATUSES = {"CONFIRMED", "REVIEWED", "APPROVED"}


def _resolve(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def _round_metric(value: float) -> float:
    return round(float(value), 4)


def _safe_ratio(numerator: int, denominator: int) -> float:
    return 0.0 if denominator <= 0 else numerator / denominator


def _f_beta(precision: float, recall: float, beta: float) -> float:
    beta2 = beta * beta
    denominator = beta2 * precision + recall
    if denominator <= 0:
        return 0.0
    return (1.0 + beta2) * precision * recall / denominator


def _prediction_map(rows: list[dict]) -> dict[str, set[str]]:
    out: dict[str, set[str]] = {}
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("file"), str):
            continue
        labels: set[str] = set()
        for item in row.get("detections", []) or []:
            if isinstance(item, dict) and item.get("classCode"):
                labels.add(str(item["classCode"]).upper())
        out[row["file"]] = labels
    return out


def evaluate_predictions(truth_payload: dict, prediction_rows: list[dict]) -> dict:
    predictions = _prediction_map(prediction_rows)
    reviewed: list[dict] = []
    for entry in truth_payload.get("images", []) or []:
        if not isinstance(entry, dict) or not isinstance(entry.get("file"), str):
            continue
        status = str(entry.get("reviewStatus", "PENDING")).upper()
        if status not in REVIEWED_STATUSES:
            continue
        reviewed.append(entry)

    if not reviewed:
        return {
            "status": "QUALITY_NOT_EVALUATED",
            "reviewedSamples": 0,
            "tp": 0,
            "fp": 0,
            "fn": 0,
            "precision": 0.0,
            "recall": 0.0,
            "f1": 0.0,
            "f0_5": 0.0,
            "normalSamples": 0,
            "normalFalsePositiveImages": 0,
            "perClass": {},
            "rows": [],
        }

    tp = fp = fn = 0
    normal_samples = 0
    normal_fp_images = 0
    per_class: dict[str, dict[str, int]] = {}
    rows: list[dict] = []

    def bucket(label: str) -> dict[str, int]:
        return per_class.setdefault(
            label,
            {"expected": 0, "hit": 0, "miss": 0, "fp": 0},
        )

    for entry in reviewed:
        file_name = entry["file"]
        expected = {str(v).upper() for v in (entry.get("expected", []) or []) if str(v).strip()}
        detected = predictions.get(file_name, set())
        hit = expected & detected
        miss = expected - detected
        extra = detected - expected

        tp += len(hit)
        fn += len(miss)
        fp += len(extra)

        for label in expected:
            b = bucket(label)
            b["expected"] += 1
            if label in hit:
                b["hit"] += 1
            else:
                b["miss"] += 1
        for label in extra:
            bucket(label)["fp"] += 1

        if not expected:
            normal_samples += 1
            if detected:
                normal_fp_images += 1

        rows.append(
            {
                "file": file_name,
                "expected": sorted(expected),
                "detected": sorted(detected),
                "hit": sorted(hit),
                "miss": sorted(miss),
                "fp": sorted(extra),
            }
        )

    precision = _safe_ratio(tp, tp + fp)
    recall = _safe_ratio(tp, tp + fn)
    f1 = _f_beta(precision, recall, 1.0)
    f0_5 = _f_beta(precision, recall, 0.5)

    return {
        "status": "QUALITY_EVALUATED_IMAGE_LEVEL",
        "reviewedSamples": len(reviewed),
        "tp": tp,
        "fp": fp,
        "fn": fn,
        "precision": _round_metric(precision),
        "recall": _round_metric(recall),
        "f1": _round_metric(f1),
        "f0_5": _round_metric(f0_5),
        "normalSamples": normal_samples,
        "normalFalsePositiveImages": normal_fp_images,
        "perClass": dict(sorted(per_class.items())),
        "rows": rows,
    }


def build_report(result: dict, benchmark: dict, profile: str, truth_path: Path) -> str:
    lines = [
        "# AI-VISION PRECISION 人工真值质量评估",
        "",
        f"> 生成时间：{datetime.now(timezone.utc).isoformat()}",
        f"> 模型：{benchmark.get('modelId', 'UNKNOWN')} v{benchmark.get('modelVersion', 'UNKNOWN')}",
        f"> 推理档位：{profile.upper()}",
        f"> 人工真值：{truth_path}",
        "",
        "## 状态",
        "",
        f"- {result['status']}",
        f"- 已人工确认样本：{result['reviewedSamples']}",
    ]
    if result["reviewedSamples"] == 0:
        lines += [
            "- 当前没有 CONFIRMED/REVIEWED/APPROVED 样本，拒绝宣称 Precision、Recall 或 F1。",
            "",
        ]
        return "\n".join(lines) + "\n"

    lines += [
        "",
        "## 图片级类别指标",
        "",
        f"- Precision（精确率）：{result['precision']:.4f}",
        f"- Recall（召回率）：{result['recall']:.4f}",
        f"- F1：{result['f1']:.4f}",
        f"- F0.5（更偏重精确率）：{result['f0_5']:.4f}",
        f"- TP / FP / FN：{result['tp']} / {result['fp']} / {result['fn']}",
        f"- 人工确认的正常图片：{result['normalSamples']}，其中出现误报：{result['normalFalsePositiveImages']}",
        "",
        "## 按类别",
        "",
        "| 类别 | 期望样本 | 命中 | 漏检 | 误检 |",
        "|---|---:|---:|---:|---:|",
    ]
    for label, bucket in result["perClass"].items():
        lines.append(
            f"| {label} | {bucket['expected']} | {bucket['hit']} | {bucket['miss']} | {bucket['fp']} |"
        )

    lines += ["", "## 逐图", ""]
    for row in result["rows"]:
        detail = (
            f"- `{row['file']}`：期望 {row['expected']}；检测 {row['detected']}；"
            f"命中 {row['hit']}；漏检 {row['miss']}；误检 {row['fp']}"
        )
        lines.append(detail)

    lines += [
        "",
        "## 解释边界",
        "",
        "- 以上指标是图片级类别识别指标，不代表检测框 IoU、定位精度或 SAM2 分割精度。",
        "- 未人工确认（PENDING）的样本完全不参与统计。",
        "- F0.5 用于当前比赛的精度优先取向；它比 F1 更强调减少误报。",
        "",
    ]
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="用人工真值评估已保存的 PRECISION benchmark")
    parser.add_argument(
        "--truth",
        default="data/validation/ai-vision/quality-manifest.json",
        help="人工真值 JSON；只统计已确认样本",
    )
    parser.add_argument(
        "--benchmark",
        default="data/model-benchmarks/vision-precision-comparison.json",
    )
    parser.add_argument("--profile", choices=["fast", "precision"], default="precision")
    parser.add_argument(
        "--report",
        default="data/model-benchmarks/vision-precision-quality-report.md",
    )
    args = parser.parse_args()

    truth_path = _resolve(args.truth)
    benchmark_path = _resolve(args.benchmark)
    report_path = _resolve(args.report)

    if not truth_path.is_file():
        print(f"人工真值不存在：{truth_path}", file=sys.stderr)
        print("可先复制 quality-manifest.template.json 为 quality-manifest.json 并人工填写。", file=sys.stderr)
        return 2
    if not benchmark_path.is_file():
        print(f"benchmark JSON 不存在：{benchmark_path}", file=sys.stderr)
        return 2

    try:
        truth = json.loads(truth_path.read_text(encoding="utf-8"))
        benchmark = json.loads(benchmark_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as ex:
        print(f"读取评估输入失败：{ex}", file=sys.stderr)
        return 2

    prediction_rows = benchmark.get(args.profile, []) or []
    result = evaluate_predictions(truth, prediction_rows)
    report = build_report(result, benchmark, args.profile, truth_path)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report, encoding="utf-8")
    print(report)
    print(f"报告已写入：{report_path}")
    return 0 if result["reviewedSamples"] > 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
