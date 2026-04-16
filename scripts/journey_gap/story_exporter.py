"""Export rewritten stories to markdown files."""

from __future__ import annotations

import logging
import sqlite3
from pathlib import Path
import re

log = logging.getLogger(__name__)

def export_stories(con: sqlite3.Connection, output_dir: Path) -> int:
    """Export consolidated stories from the database to markdown files under capability folders."""
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
        WHERE i.state = 'OPEN'
    ''')
    
    count = 0
    for number, title, body, labels in cur.fetchall():
        if not body:
            continue
            
        # Try to find a capability label (starts with capability:)
        capability = "Uncategorized"
        if labels:
            for label in labels.split(','):
                label = label.strip()
                if label.startswith('capability:'):
                    capability = label.replace('capability:', '').strip()
                    break
        
        # Create safe directory and file names
        safe_cap = re.sub(r'[^a-zA-Z0-9]+', '-', capability).strip('-').title()
        safe_title = re.sub(r'[^a-zA-Z0-9]+', '-', title).strip('-').lower()
        
        cap_dir = output_dir / safe_cap
        cap_dir.mkdir(parents=True, exist_ok=True)
        
        filename = f"{number}-{safe_title}.md"
        filepath = cap_dir / filename
        
        content = f"# Issue #{number}: {title}\n\n{body}\n"
        filepath.write_text(content, encoding='utf-8')
        count += 1
        
    return count
