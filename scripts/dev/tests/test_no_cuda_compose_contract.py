from __future__ import annotations

from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[3]
DOCKERFILE = ROOT / "docker/ai-service/Dockerfile.no-cuda"
COMPOSE_FILE = ROOT / "docker/docker-compose.no-cuda.yml"
BASE_REQUIREMENTS = ROOT / "ai-service-python/requirements.txt"
NO_CUDA_REQUIREMENTS = ROOT / "ai-service-python/requirements-no-cuda.txt"
REAL_REQUIREMENTS = ROOT / "ai-service-python/requirements-real.txt"
LAUNCHER = ROOT / "scripts/dev/start-docker-no-cuda-compose.sh"


def _active_requirement_lines(path: Path) -> list[str]:
    return [
        line.strip().lower()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


class NoCudaComposeContractTest(unittest.TestCase):

    def test_required_no_cuda_assets_exist(self):
        self.assertTrue(DOCKERFILE.is_file(), f"缺少 {DOCKERFILE}")
        self.assertTrue(COMPOSE_FILE.is_file(), f"缺少 {COMPOSE_FILE}")
        self.assertTrue(BASE_REQUIREMENTS.is_file(), f"缺少 {BASE_REQUIREMENTS}")
        self.assertTrue(NO_CUDA_REQUIREMENTS.is_file(), f"缺少 {NO_CUDA_REQUIREMENTS}")
        self.assertTrue(REAL_REQUIREMENTS.is_file(), f"缺少 {REAL_REQUIREMENTS}")
        self.assertTrue(LAUNCHER.is_file(), f"缺少 {LAUNCHER}")

    def test_no_cuda_image_uses_only_cpu_ai_dependencies(self):
        dockerfile = DOCKERFILE.read_text(encoding="utf-8")
        base_requirements = _active_requirement_lines(BASE_REQUIREMENTS)
        no_cuda_requirements = _active_requirement_lines(NO_CUDA_REQUIREMENTS)
        real_requirements = _active_requirement_lines(REAL_REQUIREMENTS)

        self.assertIn("FROM python:3.11-slim", dockerfile)
        self.assertIn("requirements-no-cuda.txt", dockerfile)
        self.assertFalse(any(line.startswith("onnxruntime") for line in base_requirements))
        self.assertIn("onnxruntime==1.27.0", no_cuda_requirements)
        self.assertFalse(any(line.startswith("onnxruntime-gpu") for line in no_cuda_requirements))
        self.assertFalse(any(line.startswith("ultralytics") for line in no_cuda_requirements))
        self.assertTrue(any(line.startswith("onnxruntime-gpu") for line in real_requirements))
        self.assertFalse(any(line.startswith("onnxruntime==") for line in real_requirements))
        self.assertNotIn("nvidia", dockerfile.lower())

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

    def test_compose_wires_local_cpu_applicability_gate_without_downloading_weights(self):
        payload = yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))
        ai_service = payload["services"]["ai-service"]
        environment = ai_service["environment"]
        self.assertIn("AI_APPLICABILITY_ENABLED", environment)
        self.assertEqual(
            environment["AI_APPLICABILITY_MODEL_PATH"],
            "/app/models/image-applicability/model.onnx",
        )
        self.assertEqual(
            environment["AI_APPLICABILITY_METADATA_PATH"],
            "/app/models/image-applicability/model.json",
        )
        self.assertIn("AI_APPLICABILITY_REJECT_THRESHOLD", environment)
        self.assertIn("AI_APPLICABILITY_APPLICABLE_THRESHOLD", environment)
        volumes = ai_service["volumes"]
        self.assertIn("../data/ai-service/no-cuda-models:/app/models:ro", volumes)

        dockerfile = DOCKERFILE.read_text(encoding="utf-8").lower()
        self.assertNotIn("wget ", dockerfile)
        self.assertNotIn("curl ", dockerfile)
        self.assertNotIn("huggingface", dockerfile)

    def test_launcher_uses_only_no_cuda_compose_entrypoint(self):
        content = LAUNCHER.read_text(encoding="utf-8")
        self.assertIn("docker/docker-compose.no-cuda.yml", content)
        self.assertIn("compose up -d --build", content)
        self.assertIn("compose config", content)
        self.assertNotIn("docker run", content)
        self.assertNotIn("gpus", content)


if __name__ == "__main__":
    unittest.main()
