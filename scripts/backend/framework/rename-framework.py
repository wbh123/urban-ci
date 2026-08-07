#!/usr/bin/env python3
"""Apply configuration-driven text replacements and path moves to the Java backend."""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path
from typing import Any

REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_CONFIG = "scripts/backend/framework/rename-framework.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default=DEFAULT_CONFIG)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def resolve_relative(root: Path, value: str, field: str) -> Path:
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError(f"{field} 必须是仓库内相对路径：{value}")
    result = (root / relative).resolve()
    result.relative_to(root.resolve())
    return result


def load_settings(config_path: Path) -> dict[str, Any]:
    settings = json.loads(config_path.read_text(encoding="utf-8"))
    if settings.get("schema_version") != 1:
        raise ValueError("仅支持 schema_version=1")
    return settings


def iter_text_files(project_root: Path, text: dict[str, Any]) -> list[Path]:
    excluded_dirs = set(text.get("exclude_dirs", []))
    extensions = set(text.get("include_extensions", []))
    names = set(text.get("include_names", []))
    files: set[Path] = set()

    for value in text.get("roots", ["."]):
        root = resolve_relative(project_root, value, "text.roots")
        if not root.exists():
            continue
        candidates = [root] if root.is_file() else root.rglob("*")
        for path in candidates:
            if not path.is_file():
                continue
            relative = path.relative_to(project_root)
            if any(part in excluded_dirs for part in relative.parts):
                continue
            if extensions or names:
                if path.suffix not in extensions and path.name not in names:
                    continue
            files.add(path)
    return sorted(files)


def replace_text(path: Path, replacements: list[dict[str, str]], dry_run: bool) -> bool:
    try:
        original = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return False
    updated = original
    for replacement in replacements:
        source = replacement.get("from")
        target = replacement.get("to")
        if not isinstance(source, str) or not source:
            raise ValueError("replacement.from 必须是非空字符串")
        if not isinstance(target, str):
            raise ValueError("replacement.to 必须是字符串")
        updated = updated.replace(source, target)
    if updated == original:
        return False
    if not dry_run:
        path.write_text(updated, encoding="utf-8")
    return True


def move_path(project_root: Path, move: dict[str, Any], dry_run: bool) -> str:
    source = resolve_relative(project_root, move["source"], "move.source")
    target = resolve_relative(project_root, move["target"], "move.target")
    required = bool(move.get("required", True))

    if not source.exists():
        if target.exists():
            return f"已完成，跳过：{target.relative_to(REPOSITORY_ROOT)}"
        if required:
            raise FileNotFoundError(f"移动源不存在：{source}")
        return f"可选源不存在，跳过：{source.relative_to(REPOSITORY_ROOT)}"
    if target.exists():
        raise FileExistsError(f"移动目标已存在：{target}")
    if not dry_run:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(source), str(target))
    return f"{source.relative_to(REPOSITORY_ROOT)} -> {target.relative_to(REPOSITORY_ROOT)}"


def main() -> None:
    args = parse_args()
    config_path = resolve_relative(REPOSITORY_ROOT, args.config, "--config")
    settings = load_settings(config_path)
    project_root = resolve_relative(
        REPOSITORY_ROOT, settings.get("project_root", "backend-java"), "project_root"
    )
    if not project_root.is_dir():
        raise NotADirectoryError(project_root)

    changed = []
    for path in iter_text_files(project_root, settings.get("text", {})):
        if replace_text(path, settings.get("replacements", []), args.dry_run):
            changed.append(path.relative_to(REPOSITORY_ROOT).as_posix())
            print(("[预览] " if args.dry_run else "") + f"更新：{changed[-1]}")

    for move in settings.get("moves", []):
        print(("[预览] " if args.dry_run else "") + move_path(project_root, move, args.dry_run))

    print(("[预览] " if args.dry_run else "") + f"适配完成，更新文本文件 {len(changed)} 个。")
    build = settings.get("build", {})
    if build.get("command"):
        print(f"建议验证命令：{build['command']}")
    if build.get("jar"):
        print(f"预期可执行 JAR：{build['jar']}")


if __name__ == "__main__":
    main()
