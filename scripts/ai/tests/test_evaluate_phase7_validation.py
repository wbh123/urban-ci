import csv
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "evaluate_phase7_validation.py"
SPEC = importlib.util.spec_from_file_location("phase7_eval", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class EvaluatePhase7ValidationTest(unittest.TestCase):

    def test_metrics_cover_success_structure_quality_and_repeatability(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = root / "manifest.csv"
            with manifest_path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(
                    handle,
                    fieldnames=[
                        "sample_id", "primary_category", "secondary_label",
                        "needs_manual_review",
                    ],
                )
                writer.writeheader()
                writer.writerow({
                    "sample_id": "positive",
                    "primary_category": "obvious_defect",
                    "secondary_label": "CRACK",
                    "needs_manual_review": "false",
                })
                writer.writerow({
                    "sample_id": "negative",
                    "primary_category": "hard_negative",
                    "secondary_label": "SHADOW",
                    "needs_manual_review": "false",
                })

            results_path = root / "results.jsonl"
            payload = {
                "summary": "完成",
                "detections": [{"classCode": "CRACK"}],
                "riskSignals": [],
                "recommendations": [],
                "warnings": [],
                "confidence": 0.8,
            }
            rows = [
                {"sampleId": "positive", "providerCode": "DIFY", "status": "SUCCEEDED",
                 "durationMs": 100, "estimatedCost": 0.01, "structuredResult": payload},
                {"sampleId": "positive", "providerCode": "DIFY", "status": "SUCCEEDED",
                 "durationMs": 120, "estimatedCost": 0.01, "structuredResult": payload},
                {"sampleId": "negative", "providerCode": "DIFY", "status": "SUCCEEDED",
                 "durationMs": 90, "estimatedCost": 0.01,
                 "structuredResult": {**payload, "detections": []}},
            ]
            results_path.write_text(
                "\n".join(json.dumps(row) for row in rows) + "\n",
                encoding="utf-8",
            )

            manifest = MODULE.read_manifest(manifest_path)
            results = MODULE.read_results(results_path)
            metrics, errors = MODULE.evaluate(manifest, results, "DIFY")

            self.assertEqual(metrics["resultRows"], 3)
            self.assertEqual(metrics["structuredValidRows"], 3)
            self.assertEqual(metrics["confusionMatrix"]["truePositive"], 2)
            self.assertEqual(metrics["confusionMatrix"]["trueNegative"], 1)
            self.assertEqual(metrics["repeatLabelJaccard"], 1.0)
            self.assertEqual(errors, [])

    def test_invalid_structure_is_reported(self):
        manifest = {
            "sample": {
                "sample_id": "sample",
                "primary_category": "obvious_defect",
                "secondary_label": "CRACK",
                "needs_manual_review": "false",
            }
        }
        metrics, errors = MODULE.evaluate(
            manifest,
            [{"sampleId": "sample", "status": "SUCCEEDED", "structuredResult": {"summary": ""}}],
        )
        self.assertEqual(metrics["structuredValidRows"], 0)
        self.assertGreater(len(errors), 0)


if __name__ == "__main__":
    unittest.main()
