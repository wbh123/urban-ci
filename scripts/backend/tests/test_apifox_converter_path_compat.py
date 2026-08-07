from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
SCRIPT = REPOSITORY_ROOT / "scripts/backend/openapi/finalize_apifox_export.py"


def load_module():
    spec = importlib.util.spec_from_file_location("finalize_apifox_export", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载脚本：{SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ApiFoxConverterPathCompatibilityTest(unittest.TestCase):
    def test_public_paths_with_api_prefix_folded_into_base_url_are_noauth(self) -> None:
        module = load_module()
        collection = {
            "item": [
                {
                    "name": "health",
                    "request": {
                        "method": "GET",
                        "url": {
                            "raw": "{{baseUrl}}/system/health",
                            "path": ["system", "health"],
                        },
                    },
                },
                {
                    "name": "login",
                    "request": {
                        "method": "POST",
                        "url": {
                            "raw": "{{baseUrl}}/auth/login",
                            "path": ["auth", "login"],
                        },
                    },
                },
                {
                    "name": "communities",
                    "request": {
                        "method": "GET",
                        "url": {
                            "raw": "{{baseUrl}}/communities",
                            "path": ["communities"],
                        },
                    },
                },
            ]
        }
        variables = [
            {"key": "baseUrl", "value": "http://localhost:8888/api/v1", "type": "string"},
            {"key": "username", "value": "admin", "type": "string"},
            {"key": "password", "value": "pwd", "type": "string"},
            {"key": "accessToken", "value": "", "type": "string"},
        ]

        module.normalize_collection(collection, variables)
        module.validate_collection(collection)
        requests = list(module.iter_requests(collection["item"]))

        self.assertEqual("noauth", requests[0]["request"]["auth"]["type"])
        self.assertEqual("noauth", requests[1]["request"]["auth"]["type"])
        self.assertEqual("bearer", requests[2]["request"]["auth"]["type"])
        self.assertEqual(
            "{{accessToken}}",
            requests[2]["request"]["auth"]["bearer"][0]["value"],
        )

    def test_v2_converter_uses_parameters_resolution_option(self) -> None:
        shell = (
            REPOSITORY_ROOT / "scripts/backend/openapi/export-apifox.sh"
        ).read_text(encoding="utf-8")
        powershell = (
            REPOSITORY_ROOT / "scripts/backend/openapi/export-apifox.ps1"
        ).read_text(encoding="utf-8")

        self.assertIn("parametersResolution=Example", shell)
        self.assertIn("parametersResolution=Example", powershell)
        self.assertNotIn("requestParametersResolution=Example", shell)
        self.assertNotIn("requestParametersResolution=Example", powershell)


if __name__ == "__main__":
    unittest.main()
