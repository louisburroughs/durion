"""Canonical documentation remains discoverable after catalog regeneration."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "knowledge_catalog", Path(__file__).resolve().parents[1] / "generate-knowledge-catalog.py"
)
catalog = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(catalog)


class DomainDocumentationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.domain = self.repo / "domains/accounting"
        self.domain.mkdir(parents=True)
        self.patch = patch.object(catalog, "REPO", self.repo)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def write(self, name, body):
        path = self.domain / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")

    def test_typed_index_exposes_documents_with_status_and_preserves_rules(self):
        self.write("index.md", "---\ntype: Domain Guide\n---\n# Accounting\n")
        self.write("archive/old.md", "---\ntitle: Old posting report\nstatus: historical\n---\n# Old report\n")
        self.write("reference/plain.md", "# Plain reference\n")
        self.write(".business-rules/RULE.md", "---\ntype: Business Rule\n---\n# Rule\n")
        self.write(".ui/screen.md", "# Screen\n")
        self.write("archive/payload.json", "{}")
        record = catalog.domain_records({"accounting": ["pos-accounting"]})[0]
        note = catalog.domain_note(record)
        self.assertIn("/domains/accounting/index.md)", note)
        self.assertIn("[Old posting report]", note)
        self.assertIn("/domains/accounting/archive/old.md)", note)
        self.assertIn("historical", note)
        self.assertIn("/domains/accounting/reference/plain.md)", note)
        self.assertEqual(note.count("/domains/accounting/.business-rules/RULE.md)"), 1)
        self.assertNotIn("screen.md", note)
        self.assertNotIn("payload.json", note)
        self.assertEqual(note, catalog.domain_note(catalog.domain_records({"accounting": ["pos-accounting"]})[0]))

    def test_untyped_index_keeps_legacy_navigation(self):
        self.write("index.md", "# Accounting\n")
        self.write("old.md", "# Old\n")
        note = catalog.domain_note(catalog.domain_records({})[0])
        self.assertNotIn("**Documentation:**", note)
        self.assertNotIn("/domains/accounting/old.md)", note)

    def test_long_document_links_keep_full_titles_without_overlong_list_items(self):
        self.write("index.md", "---\ntype: Domain Guide\n---\n# Accounting\n")
        title = "Vendor Bill GL Posting Event Implementation"
        self.write(
            "archive/implementation/GL_POSTING_EVENT_IMPLEMENTATION.md",
            f"---\ntitle: {title}\nstatus: historical\n---\n# Report\n",
        )
        note = catalog.domain_note(catalog.domain_records({})[0])
        self.assertIn(title, note)
        self.assertIn("historical", note)
        self.assertIn("archive/implementation/GL_POSTING_EVENT_IMPLEMENTATION.md", note)
        self.assertTrue(all(len(line) <= catalog.MAX_LINE for line in note.splitlines() if line.startswith("* ")))

    def test_module_links_to_opted_in_canonical_domain_index(self):
        self.write("index.md", "---\ntype: Domain Guide\n---\n# Accounting\n")
        note = catalog.module_note({"name": "pos-accounting", "kind": "Service", "openapi": True}, "accounting")
        self.assertIn("/domains/accounting/index.md)", note)
        self.assertIn("/pos-accounting/openapi.yaml)", note)


if __name__ == "__main__":
    unittest.main()
