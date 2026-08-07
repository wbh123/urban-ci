from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
OPENAPI_SCRIPT = REPOSITORY_ROOT / "scripts/backend/openapi/enrich_openapi.py"
ASSET_SCRIPT = REPOSITORY_ROOT / "scripts/backend/openapi/generate_apifox_assets.py"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载脚本：{path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def collection_items(collection: dict) -> list[dict]:
    return collection["item"][0]["item"]


def event_script(item: dict, listen: str) -> str:
    for event in item.get("event", []):
        if event.get("listen") == listen:
            return "\n".join(event["script"]["exec"])
    return ""


class ApiFoxExportTest(unittest.TestCase):

    def test_enriched_openapi_uses_login_noauth_and_bearer_for_protected_operations(self) -> None:
        module = load_module("enrich_openapi", OPENAPI_SCRIPT)
        document = {
            "openapi": "3.0.3",
            "info": {"title": "test", "version": "1"},
            "paths": {
                "/api/v1/auth/login": {
                    "post": {
                        "operationId": "login",
                        "requestBody": {
                            "content": {
                                "application/json": {
                                    "schema": {"type": "object"}
                                }
                            }
                        },
                        "responses": {"200": {"description": "ok"}},
                    }
                },
                "/api/v1/communities": {
                    "get": {
                        "operationId": "listCommunities",
                        "responses": {"200": {"description": "ok"}},
                    }
                },
            },
        }

        module.enrich(document)

        login = document["paths"]["/api/v1/auth/login"]["post"]
        protected = document["paths"]["/api/v1/communities"]["get"]
        bearer = document["components"]["securitySchemes"]["bearerAuth"]

        self.assertEqual([{"bearerAuth": []}], document["security"])
        self.assertEqual([], login["security"])
        self.assertEqual([{"bearerAuth": []}], protected["security"])
        self.assertIn("data.accessToken", login["description"])
        self.assertIn("Bearer {{accessToken}}", protected["description"])
        self.assertIn("accessToken", bearer["description"])
        self.assertEqual("认证", login["x-apifox-folder"])
        self.assertEqual("tested", login["x-apifox-status"])
        self.assertEqual(
            "admin",
            login["requestBody"]["content"]["application/json"]["example"]["username"],
        )
        self.assertTrue(
            login["responses"]["200"]["content"]["application/json"]["example"]["success"]
        )

    def test_generated_collection_extracts_token_and_unifies_protected_auth(self) -> None:
        module = load_module("generate_apifox_assets", ASSET_SCRIPT)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            application = root / "application.yaml"
            output = root / "out"
            application.write_text(
                """
server:
  port: 8888
urban-safe:
  auth:
    bootstrap-admin:
      enabled: true
      username: admin
      password: urban_safe_admin_password
""".strip()
                + "\n",
                encoding="utf-8",
            )

            config = module.parse_scalar_yaml(application)
            environment = module.build_environment(config)
            image = output / "inspection-sample.png"
            collection = module.build_collection(image, include_upload=True)

        environment_values = {
            entry["key"]: entry["value"] for entry in environment["values"]
        }
        self.assertEqual("http://localhost:8888", environment_values["baseUrl"])
        self.assertEqual("admin", environment_values["username"])
        self.assertEqual("urban_safe_admin_password", environment_values["password"])
        self.assertEqual("", environment_values["accessToken"])

        self.assertEqual("bearer", collection["auth"]["type"])
        self.assertEqual(
            "{{accessToken}}",
            collection["auth"]["bearer"][0]["value"],
        )

        items = collection_items(collection)
        health = items[0]
        login = items[1]
        protected_items = items[2:]

        self.assertEqual("noauth", health["request"]["auth"]["type"])
        self.assertEqual("noauth", login["request"]["auth"]["type"])
        self.assertNotIn("Authorization", json.dumps(login, ensure_ascii=False))

        login_post_script = event_script(login, "test")
        self.assertIn('pm.environment.set("accessToken", accessToken);', login_post_script)
        self.assertIn("body.data.accessToken", login_post_script)
        self.assertIn("accessToken 已写入环境变量", login_post_script)

        for protected in protected_items:
            auth = protected["request"]["auth"]
            self.assertEqual("bearer", auth["type"], protected["name"])
            self.assertEqual("{{accessToken}}", auth["bearer"][0]["value"], protected["name"])
            pre_script = event_script(protected, "prerequest")
            self.assertIn("缺少 accessToken", pre_script, protected["name"])
            self.assertIn('pm.environment.get("accessToken")', pre_script, protected["name"])

        serialized = json.dumps(collection, ensure_ascii=False)
        for variable in (
            "accessToken",
            "communityId",
            "buildingId",
            "longitude",
            "latitude",
            "taskId",
            "recordId",
            "assetId",
        ):
            self.assertIn(f'pm.environment.set(\\"{variable}\\"', serialized)
        self.assertIn("pm.response.to.have.status", serialized)
        self.assertIn("统一响应 success=true", serialized)

    def test_openapi_aggregator_declares_global_bearer_auth(self) -> None:
        aggregator = (
            REPOSITORY_ROOT
            / "backend-java/model/src/main/resources/openapi-interface.yaml"
        ).read_text(encoding="utf-8")
        self.assertIn("/api/v1/auth/login:", aggregator)
        self.assertIn("bearerAuth:", aggregator)
        self.assertIn("Authorization: Bearer {{accessToken}}", aggregator)

    def test_login_contract_explicitly_disables_authentication(self) -> None:
        contract = (
            REPOSITORY_ROOT
            / "backend-java/model/src/main/resources/auth-login/openapi-auth-login.yaml"
        ).read_text(encoding="utf-8")
        self.assertIn("security: []", contract)
        self.assertIn("data.accessToken", contract)
        self.assertIn("accessToken", contract)


if __name__ == "__main__":
    unittest.main()
