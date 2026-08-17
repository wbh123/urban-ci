#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "[preflight] 校验 Python 语法..."
python3 -m py_compile \
  "${repository_root}/scripts/dev/showcase_amap_budget.py" \
  "${repository_root}/scripts/dev/showcase_wuhan_search.py" \
  "${repository_root}/scripts/dev/generate-showcase-data-cached.py" \
  "${repository_root}/scripts/dev/prepare-showcase-assets.py" \
  "${repository_root}/scripts/dev/generate-showcase-closure.py" \
  "${repository_root}/scripts/dev/normalize-showcase-diversity-sql.py"

echo "[preflight] 运行无网络单元测试..."
for test_file in \
  "${repository_root}/scripts/dev/tests/test_showcase_amap_budget.py" \
  "${repository_root}/scripts/dev/tests/test_showcase_wuhan_search.py" \
  "${repository_root}/scripts/dev/tests/test_showcase_wuhan_adcode_fallback.py" \
  "${repository_root}/scripts/dev/tests/test_prepare_showcase_assets.py" \
  "${repository_root}/scripts/dev/tests/test_generate_showcase_closure.py" \
  "${repository_root}/scripts/dev/tests/test_normalize_showcase_sql.py" \
  "${repository_root}/scripts/dev/tests/test_showcase_reinspection_decision_sql.py"; do
  python3 "${test_file}"
done

echo "[preflight] 校验 Bash 语法..."
for script_file in \
  "${repository_root}/scripts/dev/generate-showcase-data.sh" \
  "${repository_root}/scripts/dev/prepare-showcase-assets.sh" \
  "${repository_root}/scripts/dev/calculate-showcase-assessments.sh" \
  "${repository_root}/scripts/dev/generate-wuhan-competition-data.sh"; do
  bash -n "${script_file}"
done

echo "[preflight] 校验公开资料目录 JSON..."
CATALOG="${SHOWCASE_WUHAN_PUBLIC_CATALOG_FILE:-${repository_root}/data/showcase-sources/wuhan-old-community-catalog-v1.json}" \
python3 - <<'PY'
import json
import os
from pathlib import Path
path = Path(os.environ["CATALOG"])
raw = json.loads(path.read_text(encoding="utf-8"))
assert raw.get("schemaVersion") == 1, "公开资料目录 schemaVersion 必须为 1"
assert raw.get("city") == "武汉市", "公开资料目录城市必须为武汉市"
entries = raw.get("entries") or []
assert len(entries) >= 20, f"公开资料目录样本过少：{len(entries)}"
for item in entries:
    assert item.get("name") and item.get("district") and item.get("sourceUrl"), item
print(f"公开资料目录校验通过：{len(entries)} 条")
PY

echo "[preflight] 生成器离线自检通过；尚未发起任何高德网络请求。"
