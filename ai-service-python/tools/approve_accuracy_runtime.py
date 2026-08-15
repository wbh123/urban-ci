"""人工批准 ACCURACY Profile：CANDIDATE → APPROVED。"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from app.accuracy_profile import DEFAULT_PROFILE_RELATIVE_PATH, load_accuracy_profile
from app.accuracy_runtime import _verify_profile

PROJECT_ROOT = Path(__file__).resolve().parents[2]


def _resolve_project(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def main() -> int:
    parser = argparse.ArgumentParser(description="人工批准 ACCURACY 正式运行时")
    parser.add_argument("--model-root", default="data/ai-service/models")
    parser.add_argument("--approver", required=True)
    parser.add_argument(
        "--confirm-manual-review",
        action="store_true",
        help="确认已经人工逐图复核冻结的 26 张最终 benchmark",
    )
    args = parser.parse_args()

    approver = args.approver.strip()
    if not approver:
        print("必须提供非空 --approver", file=sys.stderr)
        return 2
    if not args.confirm_manual_review:
        print("必须显式提供 --confirm-manual-review，禁止自动批准", file=sys.stderr)
        return 2

    model_root = _resolve_project(args.model_root)
    profile_path = model_root / DEFAULT_PROFILE_RELATIVE_PATH
    try:
        profile = load_accuracy_profile(profile_path, model_root, require_approved=False)
        if profile.status != "CANDIDATE":
            raise RuntimeError("只有 CANDIDATE Profile 可以执行首次批准")
        _verify_profile(profile)
        details = json.loads(profile.benchmark_details_path.read_text(encoding="utf-8"))
        summary = details.get("accuracySummary") or {}
        if int(details.get("imageCount", 0)) < 26:
            raise RuntimeError("冻结 benchmark 图片数不足 26")
        if int(summary.get("failures", -1)) != 0:
            raise RuntimeError("冻结 benchmark 存在失败")
        if int(summary.get("noDetectionImages", -1)) != 0:
            raise RuntimeError("冻结 benchmark 存在无检测图片")

        payload = json.loads(profile_path.read_text(encoding="utf-8"))
        payload["status"] = "APPROVED"
        payload["approvedBy"] = approver
        payload["approvedAt"] = datetime.now(timezone.utc).isoformat()
        profile_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        approved = load_accuracy_profile(profile_path, model_root, require_approved=True)
        _verify_profile(approved)
    except Exception as ex:
        print("批准 ACCURACY Profile 失败：" + str(ex), file=sys.stderr)
        return 2

    print("[PASS] ACCURACY Profile 已 APPROVED")
    print("  profileId=" + approved.profile_id)
    print("  version=" + approved.version)
    print("  approvedBy=" + str(approved.approved_by))
    print("重启 FastAPI 后正式 ACCURACY runner 才会安装。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
