"""Export rewritten stories to markdown files grouped by capability."""

from __future__ import annotations

import logging
import sqlite3
from pathlib import Path
import re
from collections import defaultdict

log = logging.getLogger(__name__)

def export_stories(con: sqlite3.Connection, output_dir: Path) -> int:
    """Export consolidated stories from the database to one markdown file per capability."""
    output_dir.mkdir(parents=True, exist_ok=True)
    
    cur = con.cursor()
    # Fetch issues and their consolidated bodies where available
    cur.execute('''
        SELECT 
            i.number, 
            i.title, 
            coalesce(c.consolidated_body, i.body),
            i.labels
        FROM issues i
        LEFT JOIN consolidated_issues c ON i.repo = c.repo AND i.number = c.number
        WHERE UPPER(i.state) = 'OPEN'
    ''')
    
    # Dictionary to group stories by capability: { 'CAP-123': [(number, title, body), ...] }
    cap_groups = defaultdict(list)
    
    count = 0
    for number, title, body, labels in cur.fetchall():
        if not body:
            continue
            
        capability = "Uncategorized"
        
        # Check if the issue ITSELF is a capability
        is_capability_issue = False
        if labels and "type:capability" in labels:
            is_capability_issue = True
            capability = f"CAP-{number}"
        
        # Otherwise, look for a CAP:xxx label
        if not is_capability_issue and labels:
            match = re.search(r'CAP:\s*(\d+)', labels)
            if match:
                capability = f"CAP-{match.group(1)}"
            else:
                # also check if the title starts with [CAP-xxx] or [CAP xxx]
                title_match = re.search(r'^\[CAP[- ]?(\d+)\]', title, re.IGNORECASE)
                if title_match:
                    capability = f"CAP-{title_match.group(1)}"

        cap_groups[capability].append((number, title, body, is_capability_issue))
        count += 1

    # Write out one markdown file per capability
    # Clean up previous generated markdown files in the folder (optional but safe)
    # for existing_file in output_dir.glob("*.md"):
    #     existing_file.unlink()

    exported_files = 0
    for cap_name, stories in cap_groups.items():
        # Clean capability name for filename
        safe_cap = re.sub(r'[^a-zA-Z0-9_-]', '', cap_name)
        filepath = output_dir / f"{safe_cap}.md"
        
        content_lines = [f"# {cap_name} Stories\n"]
        
        # Sort so that if the capability issue itself is in here, it's at the top
        stories.sort(key=lambda s: (not s[3], s[0]))
        
        for number, title, body, is_cap in stories:
            issue_type = "Capability" if is_cap else "Story"
            content_lines.append(f"## [{issue_type}] #{number}: {title}\n")
            content_lines.append(f"{body}\n")
            content_lines.append("\n---\n")
            
        filepath.write_text("\n".join(content_lines), encoding='utf-8')
        exported_files += 1
        
    return exported_files
