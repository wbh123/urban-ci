"""Dify Review Assist v1.1 静态契约测试。"""

from __future__ import annotations

import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]
DSL = ROOT / "docs/10_开发阶段/07_第七阶段/resources/UrbanSafe_Review_Assist_v1.1.0_Dify_DSL.yml"


class ReviewAssistV11DslTest(unittest.TestCase):

    def setUp(self) -> None:
        self.assertTrue(DSL.is_file(), f"缺少 Dify DSL：{DSL}")
        self.payload = yaml.safe_load(DSL.read_text(encoding="utf-8"))
        self.nodes = self.payload["workflow"]["graph"]["nodes"]

    def test_accepts_java_runtime_and_governance_inputs(self):
        start = next(node for node in self.nodes if node["data"]["type"] == "start")
        variables = {item["variable"]: item for item in start["data"]["variables"]}
        for name in (
            "analysisJson",
            "inspectionRecordJson",
            "localModelJson",
            "buildingContextJson",
            "workflowCode",
            "workflowVersion",
            "inputSchemaVersion",
        ):
            self.assertIn(name, variables)
        self.assertTrue(variables["analysisJson"]["required"])
        self.assertTrue(variables["inspectionRecordJson"]["required"])

    def test_has_input_normalization_validation_and_single_repair_path(self):
        types = [node["data"]["type"] for node in self.nodes]
        self.assertGreaterEqual(types.count("code"), 3)
        self.assertEqual(types.count("if-else"), 1)
        self.assertEqual(types.count("variable-aggregator"), 1)
        self.assertEqual(types.count("llm"), 2)

        content = DSL.read_text(encoding="utf-8")
        self.assertIn("INPUT_TRUNCATED", content)
        self.assertIn("INPUT_INVALID_JSON", content)
        self.assertIn('"schemaVersion": "1.1"', content)
        self.assertIn('"workflowCode": "DIFY-REVIEW-ASSIST-001"', content)
        self.assertIn('"workflowVersion": "review-assist-v1.1.0"', content)

    def test_remains_review_only_and_has_no_business_write_node(self):
        content = DSL.read_text(encoding="utf-8")
        for marker in (
            "evidenceAgreements",
            "evidenceConflicts",
            "missingFields",
            "reshootRequests",
            "reviewQuestions",
            "needsHumanReview",
            "不能代替专家",
            "不能修改业务数据",
        ):
            self.assertIn(marker, content)
        self.assertNotIn("/api/v1/", content)
        self.assertNotIn("http-request", [node["data"]["type"] for node in self.nodes])

    def test_end_node_only_returns_stable_result_string(self):
        end = next(node for node in self.nodes if node["data"]["type"] == "end")
        outputs = {item["variable"]: item for item in end["data"]["outputs"]}
        self.assertEqual(set(outputs), {"result"})
        self.assertEqual(outputs["result"]["value_type"], "string")

    def test_graph_references_and_embedded_python_are_valid(self):
        node_ids = {node["id"] for node in self.nodes}
        for edge in self.payload["workflow"]["graph"]["edges"]:
            self.assertIn(edge["source"], node_ids)
            self.assertIn(edge["target"], node_ids)
        for node in self.nodes:
            if node["data"]["type"] == "code":
                compile(node["data"]["code"], node["id"], "exec")


if __name__ == "__main__":
    unittest.main()
