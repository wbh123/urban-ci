"""第七阶段第二轮 Dify DSL 静态契约测试。"""

from __future__ import annotations

import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[3]
RESOURCE_DIR = ROOT / "docs/10_开发阶段/07_第七阶段/resources"

WORKFLOWS = {
    "image": RESOURCE_DIR / "UrbanSafe_Image_Analysis_v1.1.0_Dify_DSL.yml",
    "review": RESOURCE_DIR / "UrbanSafe_Review_Assist_v1.0.0_Dify_DSL.yml",
    "report": RESOURCE_DIR / "UrbanSafe_Report_Draft_v1.0.0_Dify_DSL.yml",
    "qa": RESOURCE_DIR / "UrbanSafe_Knowledge_QA_v1.0.0_Dify_DSL.yml",
}


class Phase7WorkflowDslTest(unittest.TestCase):

    def _load(self, key: str) -> dict:
        path = WORKFLOWS[key]
        self.assertTrue(path.is_file(), f"缺少 Dify DSL：{path}")
        payload = yaml.safe_load(path.read_text(encoding="utf-8"))
        self.assertIsInstance(payload, dict)
        self.assertEqual(payload.get("kind"), "app")
        self.assertEqual(payload.get("app", {}).get("mode"), "workflow")
        return payload

    def _nodes(self, payload: dict) -> list[dict]:
        return payload["workflow"]["graph"]["nodes"]

    def test_image_workflow_has_vision_normalization_repair_and_image_echo(self):
        payload = self._load("image")
        nodes = self._nodes(payload)
        node_types = [node["data"]["type"] for node in nodes]

        self.assertIn("llm", node_types)
        self.assertGreaterEqual(node_types.count("code"), 2)
        self.assertIn("if-else", node_types)
        self.assertIn("variable-aggregator", node_types)

        vision_nodes = [node for node in nodes if node["data"]["type"] == "llm"
                        and node["data"].get("vision", {}).get("enabled")]
        self.assertEqual(len(vision_nodes), 1)

        end_node = next(node for node in nodes if node["data"]["type"] == "end")
        outputs = {item["variable"]: item for item in end_node["data"]["outputs"]}
        self.assertEqual(outputs["inputImage"]["value_type"], "file")
        self.assertEqual(outputs["result"]["value_type"], "string")

        prompt_text = "\n".join(
            item.get("text", "")
            for vision_node in vision_nodes
            for item in vision_node["data"].get("prompt_template", [])
        )
        for required in (
            '"schemaVersion": "1.1"',
            '"workflowCode": "DIFY-IMAGE-ANALYSIS-001"',
            '"needsHumanReview"',
            '"applicable"',
        ):
            self.assertIn(required, prompt_text)

    def test_image_workflow_rejects_non_building_scene_before_defect_analysis(self):
        payload = self._load("image")
        vision_node = next(
            node for node in self._nodes(payload)
            if node["data"]["type"] == "llm"
            and node["data"].get("vision", {}).get("enabled")
        )
        prompt_text = "\n".join(
            item.get("text", "")
            for item in vision_node["data"].get("prompt_template", [])
        )
        for required in (
            "先判断图片主体是否属于建筑",
            "非建筑",
            "人物、宠物、车辆、桌面、普通生活用品",
            "applicable=false",
            "detections=[]",
            "riskSignals=[]",
            "不得臆造裂缝、剥落、渗水、露筋",
        ):
            self.assertIn(required, prompt_text)

    def test_review_workflow_only_produces_review_assistance(self):
        payload = self._load("review")
        content = WORKFLOWS["review"].read_text(encoding="utf-8")
        self.assertIn("evidenceAgreements", content)
        self.assertIn("evidenceConflicts", content)
        self.assertIn("reshootRequests", content)
        self.assertNotIn("/api/v1/", content)
        self.assertNotIn("http-request", [node["data"]["type"] for node in self._nodes(payload)])

    def test_report_workflow_cannot_calculate_or_publish_scores(self):
        payload = self._load("report")
        content = WORKFLOWS["report"].read_text(encoding="utf-8")
        self.assertIn("draftSections", content)
        self.assertIn("disclaimer", content)
        self.assertIn("不得重新计算风险分", content)
        self.assertNotIn("http-request", [node["data"]["type"] for node in self._nodes(payload)])

    def test_knowledge_qa_consumes_authorized_context_and_supports_refusal(self):
        payload = self._load("qa")
        content = WORKFLOWS["qa"].read_text(encoding="utf-8")
        self.assertIn("authorizedContextJson", content)
        self.assertIn("evidenceSufficient", content)
        self.assertIn("当前知识库中没有足够依据回答该问题", content)
        self.assertNotIn("knowledge-retrieval", [node["data"]["type"] for node in self._nodes(payload)])

    def test_all_graph_references_and_embedded_python_are_valid(self):
        for key in WORKFLOWS:
            payload = self._load(key)
            nodes = self._nodes(payload)
            node_ids = {node["id"] for node in nodes}
            for edge in payload["workflow"]["graph"]["edges"]:
                self.assertIn(edge["source"], node_ids)
                self.assertIn(edge["target"], node_ids)
            for node in nodes:
                if node["data"]["type"] == "code":
                    compile(node["data"]["code"], f"{key}:{node['id']}", "exec")

    def test_workflows_do_not_embed_secrets_or_business_write_nodes(self):
        forbidden = ("sk-", "api_key:", "authorization:", "database_url", "jdbc:")
        for path in WORKFLOWS.values():
            content = path.read_text(encoding="utf-8").lower()
            for marker in forbidden:
                self.assertNotIn(marker, content, f"{path.name} 包含禁用内容 {marker}")


if __name__ == "__main__":
    unittest.main()
