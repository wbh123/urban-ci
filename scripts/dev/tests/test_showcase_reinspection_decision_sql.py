#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ENRICH = ROOT / "scripts/dev/enrich-feedback-reinspection-decisions.sql"
VERIFY = ROOT / "scripts/dev/verify-feedback-reinspection-decisions.sql"


def require_markers(path: Path, markers: list[str]) -> None:
    assert path.exists(), f"缺少比赛复检决策脚本：{path}"
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        assert marker in text, f"{path.name} 缺少关键契约：{marker}"


def main() -> None:
    require_markers(
        ENRICH,
        [
            "SHOWCASE-DECISION-LOW-PROCESSING",
            "SHOWCASE-DECISION-HIGH-RESOLVED",
            "SHOWCASE-DECISION-WAIVED-HISTORY",
            "SHOWCASE-DECISION-OVERRIDE-HISTORY",
            "RECTIFICATION_CLOSED_WITHOUT_REINSPECTION",
            "REINSPECTION_WAIVED",
            "reinspectionDecision",
            "recommendedDecision",
            "manualOverride",
            "decisionReason",
            "formalRiskChanged",
            "RECTIFICATION_PHOTO",
        ],
    )
    require_markers(
        VERIFY,
        [
            "SHOWCASE-DECISION-LOW-PROCESSING",
            "SHOWCASE-DECISION-HIGH-RESOLVED",
            "SHOWCASE-DECISION-WAIVED-HISTORY",
            "SHOWCASE-DECISION-OVERRIDE-HISTORY",
            "REINSPECTION_WAIVED",
            "RECTIFICATION_CLOSED_WITHOUT_REINSPECTION",
            "manualOverride",
            "formalRiskChanged",
        ],
    )
    print("复检人工决策 real-mode 演示 SQL 契约校验通过")


if __name__ == "__main__":
    main()
