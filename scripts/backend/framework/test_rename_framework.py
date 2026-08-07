#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("rename-framework.py")


def load_module():
    spec = importlib.util.spec_from_file_location("rename_framework", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class RenameFrameworkTest(unittest.TestCase):

    def test_text_replacement(self) -> None:
        module = load_module()
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "Example.java"
            target.write_text("package com.service.demo;", encoding="utf-8")
            changed = module.replace_text(
                target,
                [{"from": "com.service.demo", "to": "org.urbansafe.priority"}],
                dry_run=False,
            )
            self.assertTrue(changed)
            self.assertEqual(
                "package org.urbansafe.priority;",
                target.read_text(encoding="utf-8"),
            )

    def test_absolute_path_is_rejected(self) -> None:
        module = load_module()
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                module.resolve_relative(Path(directory), "/outside.json", "--config")


if __name__ == "__main__":
    unittest.main()
