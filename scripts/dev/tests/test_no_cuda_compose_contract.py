from __future__ import annotations

from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[3]
DOCKERFILE = ROOT / "docker/ai-service/Dockerfile.no-cuda"
COMPOSE_FILE = ROOT / "docker/docker-compose.no-cuda.yml"
LAUNCHER = ROOT / "scripts/dev/start-docker-no-cuda-compose.sh"


class NoCudaComposeContractTest(unittest.TestCase):

    def test_required_no_cuda_assets_exist(self):
        self.assertTrue(DOCKERFILE.is_file(), f"缺少 {DOCKERFILE}")
        self.assertTrue(COMPOSE_FILE.is_file(), f"缺少 {COMPOSE_FILE}")
        self.assertTrue(LAUNCHER.is_file(), f"缺少 {LAUNCHER}")

    def test_no_cuda_image_uses_only_base_ai_dependencies(self):
        content = DOCKERFILE.read_text(encoding="utf-8")
        self.assertIn("FROM python:3.11-slim", content)
        self.assertIn("ai-service-python/requirements.txt", content)
        self.assertNotIn("onnxruntime-gpu", content.lower())
        self.assertNotIn("nvidia", content.lower())
        self.assertNotIn("cuda", content.lower().replace("dockerfile.no-cuda", ""))

    def test_compose_ai_service_has_no_gpu_runtime_and_disables_real_models(self):
        payload = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))
        ai_service = payload["services"]["ai-service"]
        self.assertNotIn("gpus", ai_service)
        self.assertNotIn("runtime", ai_service)
        self.assertEqual(
            ai_service["build"]["dockerfile"],
            "docker/ai-service/Dockerfile.no-cuda",
        )
        environment = ai_service["environment"]
        self.assertEqual(environment["AI_REAL_MODEL_STATUS"], "UNAVAILABLE")
        self.assertEqual(environment["AI_MODEL_ROOT"], "/app/models")

    def test_launcher_uses_only_no_cuda_compose_entrypoint(self):
        content = LAUNCHER.read_text(encoding="utf-8")
        self.assertIn("docker/docker-compose.no-cuda.yml", content)
        self.assertIn("compose up -d --build", content)
        self.assertIn("compose config", content)
        self.assertNotIn("docker run", content)
        self.assertNotIn("gpus", content)


if __name__ == "__main__":
    unittest.main()
