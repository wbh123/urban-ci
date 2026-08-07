from __future__ import annotations

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
BACKEND_ROOT = REPOSITORY_ROOT / "backend-java"
MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


class BackendLayoutTest(unittest.TestCase):

    def test_backend_contains_only_four_module_directories(self) -> None:
        directories = {
            path.name
            for path in BACKEND_ROOT.iterdir()
            if path.is_dir() and not path.name.startswith(".")
        }
        self.assertEqual({"client", "model", "server", "starter"}, directories)

    def test_parent_pom_declares_only_four_modules(self) -> None:
        root = ET.parse(BACKEND_ROOT / "pom.xml").getroot()
        modules = [
            node.text
            for node in root.findall("m:modules/m:module", MAVEN_NAMESPACE)
        ]
        self.assertEqual(["client", "model", "server", "starter"], modules)

    def test_removed_backend_tool_directories_do_not_return(self) -> None:
        for name in ("persistence", "persistence-codegen", "http", "scripts", "docs"):
            self.assertFalse((BACKEND_ROOT / name).exists(), name)

    def test_persistence_sources_live_in_server(self) -> None:
        expected = [
            BACKEND_ROOT
            / "server/src/main/java/org/urbansafe/priority/persistence/typehandler/UuidTypeHandler.java",
            BACKEND_ROOT
            / "server/src/generated/java/org/urbansafe/priority/persistence/entity/CommunityEntity.java",
            BACKEND_ROOT
            / "server/src/main/resources/mappers/community/CommunityMapper.xml",
        ]
        for path in expected:
            self.assertTrue(path.is_file(), path)


if __name__ == "__main__":
    unittest.main()
