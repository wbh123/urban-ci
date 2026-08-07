#!/usr/bin/env python3
"""Prepare application.yaml with deterministic local values for ApiFox execution."""

from __future__ import annotations

import argparse
from pathlib import Path

DEFAULTS = {
    "spring.datasource.password": "urban_safe_dev_password",
    "urban-safe.auth.jwt.secret": "urban-safe-local-jwt-secret-2026-0123456789abcdef",
    "urban-safe.auth.bootstrap-admin.enabled": "true",
    "urban-safe.auth.bootstrap-admin.password": "urban_safe_admin_password",
}


def prepare(path: Path) -> bool:
    lines = path.read_text(encoding="utf-8").splitlines()
    stack: list[tuple[int, str]] = []
    changed = False
    output: list[str] = []

    for raw in lines:
        stripped = raw.strip()
        indent = len(raw) - len(raw.lstrip(" "))
        if (
            stripped
            and not stripped.startswith("#")
            and not stripped.startswith("- ")
            and ":" in stripped
        ):
            key, value = stripped.split(":", 1)
            while stack and indent <= stack[-1][0]:
                stack.pop()
            full_key = ".".join([entry[1] for entry in stack] + [key.strip()])
            if not value.strip():
                stack.append((indent, key.strip()))
            if full_key in DEFAULTS:
                expected = DEFAULTS[full_key]
                replacement = " " * indent + key.strip() + ": " + expected
                if raw != replacement:
                    raw = replacement
                    changed = True
        output.append(raw)

    localhost_origin = "        - http://localhost:5173"
    ip_origin = "        - http://127.0.0.1:5173"
    if ip_origin not in output and localhost_origin in output:
        index = output.index(localhost_origin)
        output.insert(index + 1, ip_origin)
        changed = True

    content = "\n".join(output) + "\n"
    if changed:
        path.write_text(content, encoding="utf-8")
    return changed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("application", type=Path)
    args = parser.parse_args()

    changed = prepare(args.application)
    print(
        "已写入 ApiFox 本地验收配置。"
        if changed
        else "ApiFox 本地验收配置已存在，无需修改。"
    )


if __name__ == "__main__":
    main()
