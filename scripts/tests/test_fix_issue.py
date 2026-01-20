import unittest
import tempfile
from pathlib import Path
import shutil
import subprocess


class FixIssueTest(unittest.TestCase):
    def test_basic_resolution(self):
        tmp = Path(tempfile.mkdtemp())
        try:
            # prepare domain business rules
            domain_dir = tmp / 'domains' / 'demo'
            br_dir = domain_dir / '.business-rules'
            br_dir.mkdir(parents=True)
            br_file = br_dir / 'DEMO_NOTES.md'
            br_file.write_text('DECISION-INVENTORY-001\nSubstitutes ownership: inventory domain.\n')

            # after.md with a simple question
            after_dir = tmp / 'work'
            after_dir.mkdir(parents=True)
            after_md = after_dir / 'after.md'
            content = '---\nlabels:\n  - blocked:clarification\n---\n\nQ: Who owns substitutes?\n'
            after_md.write_text(content)

            # copy script into tmp workspace
            script_src = Path(__file__).resolve().parents[1] / 'fix_issue.py'
            script_dst = tmp / 'fix_issue.py'
            shutil.copy(script_src, script_dst)

            # run script
            proc = subprocess.run(['python3', str(script_dst), 'demo', str(after_md)], cwd=str(tmp))
            self.assertIn(proc.returncode, (0,20))

            fixed = after_dir / 'fixed.md'
            self.assertTrue(fixed.exists())
            txt = fixed.read_text()
            # should contain the business rule filename or decision id
            self.assertTrue('DEMO_NOTES.md' in txt or 'DECISION-INVENTORY-001' in txt)
        finally:
            shutil.rmtree(str(tmp))


if __name__ == '__main__':
    unittest.main()
