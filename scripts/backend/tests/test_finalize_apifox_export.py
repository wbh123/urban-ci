from __future__ import annotations

import importlib.util
import json
import tempfile
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


def sample_collection(folder_name: str = "flow") -> dict:
    return {
        "info": {"name": "test"},
        "auth": {
            "type": "bearer",
            "bearer": [{"key": "token", "value": "{{token}}"}],
        },
        "item": [
            {
                "name": folder_name,
                "item": [
                    {
                        "name": "health",
                        "request": {
                            "method": "GET",
                            "url": "{{baseUrl}}/api/v1/system/health",
                        },
                    },
                    {
                        "name": "login",
                        "request": {
                            "method": "POST",
                            "url": "{{baseUrl}}/api/v1/auth/login",
                        },
                        "event": [
                            {
                                "listen": "test",
                                "script": {
                                    "exec": [
                                        'pm.environment.set("accessToken", body.data.accessToken);'
                                    ]
                                },
                            }
                        ],
                    },
                    {
                        "name": "protected",
                        "request": {
                            "method": "GET",
                            "url": {
                                "raw": "http://localhost:8888/api/v1/communities",
                                "host": ["localhost"],
                            },
                        },
                        "event": [
                            {
                                "listen": "prerequest",
                                "script": {
                                    "exec": ['pm.environment.get("accessToken");']
                                },
                            }
                        ],
                    },
                ],
            }
        ],
    }


class FinalizeApiFoxExportTest(unittest.TestCase):
    def test_collection_is_self_contained_and_auth_is_explicit(self) -> None:
        module = load_module()
        collection = sample_collection()
        variables = [
            {"key": "baseUrl", "value": "http://localhost:8888", "type": "string"},
            {"key": "username", "value": "admin", "type": "string"},
            {"key": "password", "value": "pwd", "type": "string"},
            {"key": "accessToken", "value": "", "type": "string"},
        ]

        module.normalize_collection(collection, variables)
        module.validate_collection(collection)
        items = list(module.iter_requests(collection["item"]))

        self.assertEqual("noauth", items[0]["request"]["auth"]["type"])
        self.assertEqual("noauth", items[1]["request"]["auth"]["type"])
        self.assertEqual(
            "{{accessToken}}",
            items[2]["request"]["auth"]["bearer"][0]["value"],
        )
        self.assertEqual(
            "{{baseUrl}}/api/v1/communities",
            items[2]["request"]["url"]["raw"],
        )
        serialized = json.dumps(collection, ensure_ascii=False)
        self.assertNotIn("pm.environment.", serialized)
        self.assertIn('pm.globals.set(\\"accessToken\\"', serialized)
        self.assertEqual(
            {"baseUrl", "username", "password", "accessToken"},
            {variable["key"] for variable in collection["variable"]},
        )

    def test_openapi_has_no_global_security_and_public_operations_are_noauth(self) -> None:
        module = load_module()
        document = {
            "security": [{"bearerAuth": []}],
            "paths": {
                "/api/v1/system/health": {"get": {}},
                "/api/v1/auth/login": {"post": {}},
                "/api/v1/communities": {"get": {}},
            },
        }

        module.normalize_openapi(document)

        self.assertNotIn("security", document)
        self.assertEqual([], document["paths"]["/api/v1/system/health"]["get"]["security"])
        self.assertEqual([], document["paths"]["/api/v1/auth/login"]["post"]["security"])
        self.assertEqual(
            [{"bearerAuth": []}],
            document["paths"]["/api/v1/communities"]["get"]["security"],
        )

    def test_directory_combines_all_interfaces_and_automatic_flow(self) -> None:
        module = load_module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            environment = {
                "values": [
                    {"key": "baseUrl", "value": "http://localhost:8888", "enabled": True},
                    {"key": "username", "value": "admin", "enabled": True},
                    {"key": "password", "value": "pwd", "enabled": True},
                    {"key": "accessToken", "value": "", "enabled": True},
                ]
            }
            (root / "urban-safe-priority-local.postman_environment.json").write_text(
                json.dumps(environment), encoding="utf-8"
            )
            collections = {
                "smoke": sample_collection("核心验收"),
                "full": sample_collection("第二阶段自动验收"),
                "all": sample_collection("全部接口"),
            }
            for name, collection in collections.items():
                (root / f"urban-safe-priority-{name}.postman_collection.json").write_text(
                    json.dumps(collection), encoding="utf-8"
                )
            openapi = root / "openapi.json"
            openapi.write_text(
                json.dumps({"security": [{"bearerAuth": []}], "paths": {}}),
                encoding="utf-8",
            )

            primary = module.normalize_directory(root, openapi)
            primary_text = primary.read_text(encoding="utf-8")

            self.assertTrue(primary.exists())
            self.assertIn("accessToken", primary_text)
            self.assertIn("全部接口", primary_text)
            self.assertIn("第二阶段自动验收", primary_text)


if __name__ == "__main__":
    unittest.main()
