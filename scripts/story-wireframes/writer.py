"""Atomic file writing for wireframes and metadata."""
import os
import json
import tempfile
from typing import Dict
from utils import sanitize_markdown


class WireframeWriter:
    """Write wireframes and metadata to disk."""
    
    def __init__(self, base_dir: str = "../../domains"):
        """
        Initialize writer.
        
        Args:
            base_dir: Base directory for domains
        """
        self.base_dir = base_dir
        
    def write_wireframe(
        self,
        domain: str,
        shortname: str,
        issue_number: int,
        content: str,
        staging: bool = False
    ) -> str:
        """
        Write wireframe to disk atomically.
        
        Args:
            domain: Domain name
            shortname: Short name slug
            issue_number: Issue number
            content: Wireframe content
            staging: Write to staging directory
            
        Returns:
            Path to written file
        """
        # Build target directory
        ui_dir = os.path.join(self.base_dir, domain, '.ui')
        if staging:
            ui_dir = os.path.join(ui_dir, 'staging')
            
        # Create directory if needed
        os.makedirs(ui_dir, exist_ok=True)
        
        # Build filename
        filename = f"{shortname}-{issue_number}.wf.md"
        final_path = os.path.join(ui_dir, filename)
        
        # Sanitize content
        content = sanitize_markdown(content)
        
        # Atomic write: write to temp file then rename
        fd, temp_path = tempfile.mkstemp(
            dir=ui_dir,
            prefix=f"{shortname}-",
            suffix='.tmp'
        )
        
        try:
            with os.fdopen(fd, 'w', encoding='utf-8') as f:
                f.write(content)
                
            # Atomic rename
            os.replace(temp_path, final_path)
            
        except Exception:
            # Clean up temp file on error
            if os.path.exists(temp_path):
                os.unlink(temp_path)
            raise
            
        return final_path
        
    def write_metadata(
        self,
        filepath: str,
        metadata: Dict
    ) -> str:
        """
        Write metadata sidecar file.
        
        Args:
            filepath: Path to wireframe file
            metadata: Metadata dictionary
            
        Returns:
            Path to metadata file
        """
        # Build metadata path
        meta_path = filepath.replace('.wf.md', '.wf.meta.json')
        
        # Write metadata
        with open(meta_path, 'w', encoding='utf-8') as f:
            json.dump(metadata, f, indent=2)
            
        return meta_path
        
    def append_to_review_csv(
        self,
        csv_path: str,
        issue_number: int,
        domain: str,
        shortname: str,
        file_path: str,
        status: str = 'pending'
    ):
        """
        Append entry to review CSV.
        
        Args:
            csv_path: Path to CSV file
            issue_number: Issue number
            domain: Domain name
            shortname: Short name slug
            file_path: Path to wireframe file
            status: Review status
        """
        # Create CSV if it doesn't exist
        if not os.path.exists(csv_path):
            os.makedirs(os.path.dirname(csv_path), exist_ok=True)
            with open(csv_path, 'w', encoding='utf-8') as f:
                f.write('issue_number,domain,shortname,file_path,status,reviewer,notes\n')
                
        # Append entry
        with open(csv_path, 'a', encoding='utf-8') as f:
            f.write(f'{issue_number},{domain},{shortname},{file_path},{status},,\n')
