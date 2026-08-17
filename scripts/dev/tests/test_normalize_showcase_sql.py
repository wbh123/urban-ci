#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import tempfile
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
NORMALIZER = REPOSITORY_ROOT / "scripts/dev/normalize-showcase-diversity-sql.py"


def main() -> None:
    source = """
SELECT CURRENT_TIMESTAMP - make_interval(days => CASE e.seq WHEN 1 THEN 6 + (e.rn%150) ELSE 2 + (e.rn%90) END);
SELECT CURRENT_TIMESTAMP - make_interval(days => 10 + (s.rn%180));
SELECT CURRENT_TIMESTAMP - make_interval(days => 3 + (s.rn%60));
SELECT CURRENT_TIMESTAMP - make_interval(hours => 1 + (b.rn%23));
SELECT CURRENT_TIMESTAMP - make_interval(days => 4 + ((b.rn*13+b.seq*7)%720));
""".strip()

    with tempfile.TemporaryDirectory() as temp_dir:
        sql_file = Path(temp_dir) / "closure.sql"
        sql_file.write_text(source, encoding="utf-8")
        env = os.environ.copy()
        env["SHOWCASE_SQL_FILE"] = str(sql_file)
        subprocess.run(["python3", str(NORMALIZER)], env=env, check=True)
        normalized = sql_file.read_text(encoding="utf-8")

    assert normalized.count("::int" ) == 5, normalized
    assert "make_interval(days => ((CASE e.seq" in normalized, normalized
    assert "make_interval(days => ((10 + (s.rn%180))::int))" in normalized, normalized
    assert "make_interval(days => ((3 + (s.rn%60))::int))" in normalized, normalized
    assert "make_interval(hours => ((1 + (b.rn%23))::int))" in normalized, normalized
    assert "make_interval(days => ((4 + ((b.rn*13+b.seq*7)%720))::int))" in normalized, normalized
    print("showcase interval normalizer regression test passed")


if __name__ == "__main__":
    main()
