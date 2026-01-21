#!/usr/bin/env python3
"""
test_fix_issue_openai.py – Unit tests for fix_issue_openai.py

Run:
    python3 test_fix_issue_openai.py

Tests cover:
- Argument parsing
- File discovery and validation
- Business rules loading
- Prompt building
- YAML parsing
- Error handling
"""

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent))

try:
    from fix_issue_openai import (
        parse_issue_list_arg,
        parse_issue_range_arg,
        parse_frontmatter_labels,
        find_open_questions,
        extract_domain_from_labels,
        extract_domain_from_required_section,
        build_full_prompt,
        _load_business_rules_files,
    )
except ImportError as e:
    print(f"Error importing fix_issue_openai: {e}")
    sys.exit(1)


class TestArgumentParsing(unittest.TestCase):
    """Test argument parsing functions."""
    
    def test_parse_issue_list_arg_single(self):
        result = parse_issue_list_arg("67")
        self.assertEqual(result, ["67"])
    
    def test_parse_issue_list_arg_multiple(self):
        result = parse_issue_list_arg("65, 67, 68")
        self.assertEqual(result, ["65", "67", "68"])
    
    def test_parse_issue_list_arg_empty(self):
        result = parse_issue_list_arg("")
        self.assertEqual(result, [])
    
    def test_parse_issue_range_single(self):
        result = parse_issue_range_arg("60-65")
        self.assertEqual(result, ["60", "61", "62", "63", "64", "65"])
    
    def test_parse_issue_range_reverse(self):
        result = parse_issue_range_arg("65-60")
        self.assertEqual(result, ["60", "61", "62", "63", "64", "65"])
    
    def test_parse_issue_range_invalid(self):
        result = parse_issue_range_arg("invalid")
        self.assertEqual(result, [])


class TestFrontmatterParsing(unittest.TestCase):
    """Test YAML frontmatter parsing."""
    
    def test_parse_with_labels(self):
        text = """---
labels:
  - domain:inventory
  - status:draft
---

# Story Title

This is the body."""
        labels, body = parse_frontmatter_labels(text)
        self.assertEqual(labels, ["domain:inventory", "status:draft"])
        self.assertIn("# Story Title", body)
    
    def test_parse_without_frontmatter(self):
        text = "# Story Title\n\nBody content"
        labels, body = parse_frontmatter_labels(text)
        self.assertIsNone(labels)
    
    def test_parse_empty_labels(self):
        text = """---
labels:
---

Body"""
        labels, body = parse_frontmatter_labels(text)
        self.assertEqual(labels, [])


class TestDomainExtraction(unittest.TestCase):
    """Test domain extraction logic."""
    
    def test_extract_from_labels(self):
        labels = ["domain:inventory", "status:draft"]
        domain = extract_domain_from_labels(labels)
        self.assertEqual(domain, "inventory")
    
    def test_extract_no_domain_label(self):
        labels = ["status:draft", "priority:high"]
        domain = extract_domain_from_labels(labels)
        self.assertIsNone(domain)
    
    def test_extract_from_required_section(self):
        body = """
### Required

- domain:order
- type:feature
"""
        domain = extract_domain_from_required_section(body)
        self.assertEqual(domain, "order")


class TestQuestionDetection(unittest.TestCase):
    """Test open question detection."""
    
    def test_find_q_markers(self):
        body = """
### Q: What is the expected behavior?

Some content

### Q: How should we handle edge cases?
"""
        questions = find_open_questions(body)
        self.assertTrue(any("expected behavior" in q.lower() for q in questions))
        self.assertTrue(any("edge cases" in q.lower() for q in questions))
    
    def test_find_todo_markers(self):
        body = """
TODO: Implement validation logic
FIXME: Fix the error handling
"""
        questions = find_open_questions(body)
        self.assertTrue(any("TODO" in q or "validation" in q.lower() for q in questions))
        self.assertTrue(any("FIXME" in q or "error" in q.lower() for q in questions))
    
    def test_no_questions(self):
        body = "This is a complete story with no open questions."
        questions = find_open_questions(body)
        self.assertEqual(len(questions), 0)


class TestPromptBuilding(unittest.TestCase):
    """Test prompt building."""
    
    def test_build_prompt_basic(self):
        """Test basic prompt building without business rules."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            prompt_file = root / "prompt.md"
            prompt_file.write_text("Review this story carefully.")
            
            body = "# Story\nThis is the story body."
            
            prompt = build_full_prompt(root, prompt_file, "test-domain", body, "Test Story")
            
            # Verify prompt structure
            self.assertIn("STORY EDITING INSTRUCTIONS", prompt)
            self.assertIn("ISSUE TO BE UPDATED", prompt)
            self.assertIn("TASK:", prompt)
            self.assertIn("OUTPUT REQUIREMENTS:", prompt)
            self.assertIn(body, prompt)
    
    def test_build_prompt_with_business_rules(self):
        """Test prompt building with business rules."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            
            # Create domain directory structure
            domain_dir = root / "domains" / "inventory" / ".business-rules"
            domain_dir.mkdir(parents=True)
            
            # Create business rules files
            (domain_dir / "AGENT_GUIDE.md").write_text("# Agent Guide\nFollow these rules.")
            (domain_dir / "DOMAIN_NOTES.md").write_text("# Domain Notes\nImportant notes.")
            
            prompt_file = root / "prompt.md"
            prompt_file.write_text("Review this story.")
            
            body = "# Story\nBody"
            prompt = build_full_prompt(root, prompt_file, "inventory", body)
            
            # Verify business rules are included
            self.assertIn("BUSINESS RULES FOR DOMAIN: INVENTORY", prompt)
            self.assertIn("Agent Guide", prompt)
            self.assertIn("Domain Notes", prompt)


class TestBusinessRulesLoading(unittest.TestCase):
    """Test business rules file loading."""
    
    def test_load_rules_files_missing_directory(self):
        """Test graceful handling of missing business rules directory."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            files = _load_business_rules_files(root, "nonexistent-domain")
            self.assertEqual(files, [])
    
    def test_load_rules_files_priority_order(self):
        """Test that business rules are loaded in priority order."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            domain_dir = root / "domains" / "test" / ".business-rules"
            domain_dir.mkdir(parents=True)
            
            # Create files in random order
            (domain_dir / "DOMAIN_NOTES.md").write_text("Domain notes")
            (domain_dir / "AGENT_GUIDE.md").write_text("Agent guide")
            (domain_dir / "other.md").write_text("Other")
            (domain_dir / "STORY_VALIDATION_CHECKLIST.md").write_text("Checklist")
            
            files = _load_business_rules_files(root, "test")
            
            # Verify priority order
            names = [f.name for f in files]
            expected_priority = ["AGENT_GUIDE.md", "DOMAIN_NOTES.md", "STORY_VALIDATION_CHECKLIST.md"]
            for expected_name in expected_priority:
                self.assertIn(expected_name, names)
                self.assertTrue(names.index(expected_name) < names.index("other.md"))


class TestMockAPICall(unittest.TestCase):
    """Test API call integration (mocked)."""
    
    @patch.dict(os.environ, {'OPENAI_API_KEY': 'test-key'})
    @patch('fix_issue_openai._http_post_json')
    def test_openai_api_response_parsing(self, mock_post):
        """Test parsing of OpenAI API response."""
        from fix_issue_openai import call_openai
        
        # Mock OpenAI API response
        mock_response = {
            "choices": [
                {
                    "message": {
                        "content": "Updated story content\n\nWith multiple sections"
                    }
                }
            ]
        }
        mock_post.return_value = mock_response
        
        result = call_openai("test prompt", "gpt-4o-mini")
        
        # Verify result
        self.assertEqual(result, "Updated story content\n\nWith multiple sections")
        mock_post.assert_called_once()


class TestIntegration(unittest.TestCase):
    """Integration tests for common workflows."""
    
    def test_workflow_with_temp_files(self):
        """Test a complete workflow with temporary files."""
        with tempfile.TemporaryDirectory() as tmpdir:
            tmpdir = Path(tmpdir)
            
            # Create issue directory
            issue_dir = tmpdir / "issues" / "67"
            issue_dir.mkdir(parents=True)
            
            # Create after.md
            after_md = issue_dir / "after.md"
            after_md.write_text("""---
labels:
  - domain:inventory
  - status:draft
---

# Story

### Q: How should inventory levels be tracked?

This is unclear.""")
            
            # Test parsing
            text = after_md.read_text()
            labels, body = parse_frontmatter_labels(text)
            
            self.assertEqual(labels, ["domain:inventory", "status:draft"])
            self.assertIn("How should inventory levels be tracked?", body)
            
            # Test domain extraction
            domain = extract_domain_from_labels(labels)
            self.assertEqual(domain, "inventory")
            
            # Test question detection
            questions = find_open_questions(body)
            self.assertTrue(len(questions) > 0)


if __name__ == '__main__':
    # Run tests
    unittest.main(verbosity=2)
