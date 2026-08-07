#!/usr/bin/env python3
"""检查当前运行代码和配置中是否残留旧项目命名。"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

FORBIDDEN_NAMES = (
    "building-select",
    "building_select",
    "smart-city-renewal-system",
    "Smart City Renewal",
    "城更智检",
    "住安智脑",
)

SCAN_TARGETS = (
    ROOT / "docker" / "docker-compose.yml",
    ROOT / "docker" / "minio" / "init-buckets.sh",
    ROOT / "ai-service-python" / "app",
    ROOT / "backend-java" / "pom.xml",
    ROOT / "backend-java" / "client" / "pom.xml",
    ROOT / "backend-java" / "model" / "pom.xml",
    ROOT / "backend-java" / "model" / "src",
    ROOT / "backend-java" / "server" / "pom.xml",
    ROOT / "backend-java" / "server" / "src",
    ROOT / "backend-java" / "starter" / "pom.xml",
    ROOT / "backend-java" / "starter" / "src",
    ROOT / "database" / "schema.sql",
    ROOT / "database" / "init_data.sql",
)

TEXT_SUFFIXES = {
    ".java",
    ".xml",
    ".yaml",
    ".yml",
    ".sql",
    ".py",
    ".sh",
    ".json",
    ".properties",
}


def iter_files(target: Path):
    if target.is_file():
        yield target
        return

    if not target.exists():
        return

    for path in target.rglob("*"):
        if not path.is_file():
            continue
        if any(part in {"target", "node_modules", "__pycache__"} for part in path.parts):
            continue
        if path.suffix.lower() in TEXT_SUFFIXES:
            yield path


def main() -> int:
    violations: list[str] = []

    for target in SCAN_TARGETS:
        for path in iter_files(target):
            content = path.read_text(encoding="utf-8")
            for forbidden_name in FORBIDDEN_NAMES:
                if forbidden_name in content:
                    relative_path = path.relative_to(ROOT)
                    violations.append(f"{relative_path}: {forbidden_name}")

    if violations:
        print("Current naming check failed:", file=sys.stderr)
        for violation in sorted(violations):
            print(f"- {violation}", file=sys.stderr)
        return 1

    print("Current naming check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
