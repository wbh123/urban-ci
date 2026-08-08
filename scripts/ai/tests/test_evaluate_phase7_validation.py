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
            self.assertEqual(metrics["structuredRequiredRows"], 3)
            self.assertEqual(metrics["structuredValidRows"], 3)
            self.assertEqual(metrics["structuredValidRate"], 1.0)
            self.assertEqual(metrics["confusionMatrix"]["truePositive"], 2)
            self.assertEqual(metrics["confusionMatrix"]["trueNegative"], 1)
            self.assertEqual(metrics["repeatLabelJaccard"], 1.0)
            self.assertEqual(errors, [])

    def test_expected_low_quality_rejection_is_valid_terminal_negative(self):
        manifest = {
            "low-quality": {
                "sample_id": "low-quality",
                "primary_category": "low_quality",
                "secondary_label": "BLUR",
                "needs_manual_review": "false",
            }
        }
        metrics, errors = MODULE.evaluate(
            manifest,
            [{
                "sampleId": "low-quality",
                "providerCode": "DIFY",
                "status": "REJECTED",
                "errorCode": "AI_IMAGE_LOW_QUALITY",
                "durationMs": 8,
                "difyActuallyCalled": False,
                "precheckCalled": True,
            }],
            "DIFY",
        )

        self.assertEqual(metrics["structuredRequiredRows"], 0)
        self.assertEqual(metrics["structuredValidRows"], 0)
        self.assertEqual(metrics["structuredValidRate"], 1.0)
        self.assertEqual(metrics["expectedPrecheckRows"], 1)
        self.assertEqual(metrics["correctPrecheckRejections"], 1)
        self.assertEqual(metrics["precheckRejectionRate"], 1.0)
        self.assertEqual(metrics["confusionMatrix"]["trueNegative"], 1)
        self.assertEqual(metrics["labeledRows"], 1)
        self.assertEqual(errors, [])

    def test_expected_not_applicable_rejection_from_dify_requires_structure(self):
        manifest = {
            "non-building": {
                "sample_id": "non-building",
                "primary_category": "not_applicable",
                "secondary_label": "NOT_APPLICABLE",
                "needs_manual_review": "false",
                "expected_applicability": "NOT_APPLICABLE",
            }
        }
        payload = {
            "summary": "图片不适用于建筑表观病害分析",
            "detections": [],
            "riskSignals": [],
            "recommendations": ["重新上传建筑或建筑构件图片"],
            "warnings": [],
            "confidence": 0.95,
        }
        metrics, errors = MODULE.evaluate(
            manifest,
            [{
                "sampleId": "non-building",
                "providerCode": "DIFY",
                "status": "REJECTED",
                "errorCode": "AI_IMAGE_NOT_APPLICABLE",
                "durationMs": 1200,
                "structuredResult": payload,
                "difyActuallyCalled": True,
                "rawResponseReference": "dify:workflow-1",
            }],
            "DIFY",
        )

        self.assertEqual(metrics["structuredRequiredRows"], 1)
        self.assertEqual(metrics["structuredValidRows"], 1)
        self.assertEqual(metrics["structuredValidRate"], 1.0)
        self.assertEqual(metrics["confusionMatrix"]["trueNegative"], 1)
        self.assertEqual(metrics["labeledRows"], 1)
        self.assertEqual(metrics["terminalAcceptedRows"], 1)
        self.assertEqual(metrics["terminalAcceptedRate"], 1.0)
        self.assertEqual(metrics["expectedSemanticPrecheckRows"], 1)
        self.assertEqual(metrics["correctSemanticPrecheckRejections"], 0)
        self.assertEqual(metrics["expectedSemanticRowsEnteredDify"], 1)
        self.assertEqual(errors, [])

    def test_local_semantic_rejection_does_not_require_dify_structure(self):
        manifest = {
            "non-building": {
                "sample_id": "non-building",
                "primary_category": "not_applicable",
                "secondary_label": "NOT_APPLICABLE",
                "needs_manual_review": "false",
                "expected_applicability": "NOT_APPLICABLE",
            }
        }
        metrics, errors = MODULE.evaluate(
            manifest,
            [{
                "sampleId": "non-building",
                "providerCode": "DIFY",
                "status": "REJECTED",
                "errorCode": "AI_IMAGE_NOT_APPLICABLE",
                "durationMs": 15,
                "difyActuallyCalled": False,
                "rawResponseReference": "",
                "precheckCalled": True,
            }],
            "DIFY",
        )

        self.assertEqual(metrics["structuredRequiredRows"], 0)
        self.assertEqual(metrics["structuredValidRows"], 0)
        self.assertEqual(metrics["structuredValidRate"], 1.0)
        self.assertEqual(metrics["semanticPrecheckRejections"], 1)
        self.assertEqual(metrics["expectedSemanticPrecheckRows"], 1)
        self.assertEqual(metrics["correctSemanticPrecheckRejections"], 1)
        self.assertEqual(metrics["unexpectedSemanticPrecheckRejections"], 0)
        self.assertEqual(metrics["semanticPrecheckRejectionRate"], 1.0)
        self.assertEqual(metrics["expectedSemanticRowsEnteredDify"], 0)
        self.assertEqual(metrics["confusionMatrix"]["trueNegative"], 1)
        self.assertEqual(errors, [])

    def test_local_semantic_rejection_of_applicable_image_is_reported_as_error(self):
        manifest = {
            "crack": {
                "sample_id": "crack",
                "primary_category": "obvious_defect",
                "secondary_label": "CRACK",
                "needs_manual_review": "false",
                "expected_applicability": "APPLICABLE",
            }
        }
        metrics, errors = MODULE.evaluate(
            manifest,
            [{
                "sampleId": "crack",
                "providerCode": "DIFY",
                "status": "REJECTED",
                "errorCode": "AI_IMAGE_NOT_APPLICABLE",
                "durationMs": 15,
                "difyActuallyCalled": False,
                "precheckCalled": True,
            }],
            "DIFY",
        )

        self.assertEqual(metrics["unexpectedSemanticPrecheckRejections"], 1)
        self.assertEqual(metrics["confusionMatrix"]["falseNegative"], 1)
        self.assertGreaterEqual(len(errors), 1)
        self.assertIn("unexpected local semantic", errors[0]["error"])

    def test_unknown_not_applicable_sample_is_not_forced_into_semantic_ground_truth(self):
        manifest = {
            "unknown": {
                "sample_id": "unknown",
                "primary_category": "not_applicable",
                "secondary_label": "UNKNOWN",
                "needs_manual_review": "true",
            }
        }
        metrics, errors = MODULE.evaluate(
            manifest,
            [{
                "sampleId": "unknown",
                "providerCode": "DIFY",
                "status": "REJECTED",
                "errorCode": "AI_IMAGE_NOT_APPLICABLE",
                "difyActuallyCalled": False,
                "precheckCalled": True,
            }],
            "DIFY",
        )

        self.assertEqual(metrics["expectedSemanticPrecheckRows"], 0)
        self.assertEqual(metrics["semanticPrecheckRejections"], 1)
        self.assertEqual(metrics["correctSemanticPrecheckRejections"], 0)
        self.assertEqual(metrics["unexpectedSemanticPrecheckRejections"], 0)
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
        self.assertEqual(metrics["structuredRequiredRows"], 1)
        self.assertEqual(metrics["structuredValidRows"], 0)
        self.assertGreater(len(errors), 0)


if __name__ == "__main__":
    unittest.main()
