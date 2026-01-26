#!/usr/bin/env python3
"""Main CLI for extracting issues and generating wireframes."""
import os
import sys
import argparse
import yaml
from datetime import datetime
from typing import Dict, List
from tqdm import tqdm

from github_client import GitHubClient
from summarizer import TextSummarizer
from prompt_builder import PromptBuilder
from openai_client import OpenAIClient
from writer import WireframeWriter
from utils import slugify_name, map_labels_to_domain


class WireframeExtractor:
    """Main orchestrator for wireframe generation."""
    
    def __init__(self, config_path: str = 'config.yml'):
        """
        Initialize extractor.
        
        Args:
            config_path: Path to config file
        """
        # Load config
        with open(config_path, 'r') as f:
            self.config = yaml.safe_load(f)
            
        # Initialize components
        self.github = GitHubClient()
        self.summarizer = TextSummarizer(**self.config['summarizer'])
        self.prompt_builder = PromptBuilder(self.summarizer)
        self.openai = OpenAIClient(**self.config['openai'])
        self.writer = WireframeWriter(self.config['output']['base_dir'])
        self.processed_path = self.config.get('processed_list', 'processed_issues.json')
        self.processed_issues = self._load_processed_issues()
        
        # Stats
        self.stats = {
            'total': 0,
            'success': 0,
            'failed': 0,
            'skipped': 0
        }
        
    def process_issues(
        self,
        repo: str = None,
        since: str = None,
        max_issues: int = None,
        batch_size: int = 10,
        staging: bool = True,
        auto_approve: bool = False,
        local_dump: str = None
    ):
        """
        Process issues from GitHub.
        
        Args:
            repo: Repository in format 'owner/repo'
            since: ISO timestamp to filter issues
            max_issues: Maximum number of issues to process
            batch_size: Batch size for progress reporting
            staging: Write to staging directory
            local_dump: Path to local JSON dump
        """
        repo = repo or self.config['repo']
        
        # Fetch issues
        if local_dump:
            print(f"Loading issues from {local_dump}...")
            issues = self.github.load_local_dump(local_dump)
        else:
            print(f"Fetching issues from {repo}...")
            issues = self.github.fetch_issues(repo, since=since)
            
        # Limit if requested
        if max_issues:
            issues = issues[:max_issues]
            
        print(f"Processing {len(issues)} issues...")
        self.stats['total'] = len(issues)
        
        # Process sequentially with progress bar
        for issue in tqdm(issues, desc="Generating wireframes"):
            if issue['number'] in self.processed_issues:
                self.stats['skipped'] += 1
                continue
            try:
                filepath = self._process_single_issue(issue, repo, staging)
                if auto_approve and staging:
                    self._promote_from_staging(filepath)
                self.stats['success'] += 1
                self._record_processed_issue(issue['number'])
            except Exception as e:
                print(f"\nError processing issue #{issue['number']}: {e}")
                self.stats['failed'] += 1
                self._log_failure(issue, str(e), staging)
                
        # Print summary
        self._print_summary()
        
    def _process_single_issue(
        self,
        issue: Dict,
        repo: str,
        staging: bool
    ):
        """
        Process a single issue.
        
        Args:
            issue: Issue dictionary
            repo: Repository name
            staging: Write to staging directory
        """
        issue_number = issue['number']
        issue_title = issue['title']
        
        # Extract labels
        labels = [label['name'] for label in issue.get('labels', [])]
        
        # Map to domain
        domain = map_labels_to_domain(
            labels,
            self.config['label_domain_mapping']
        )
        
        # Generate short name
        shortname = slugify_name(issue_title)
        
        # Fetch comments
        comments = self.github.fetch_comments(repo, issue_number)
        
        # Build prompt
        prompt_data = self.prompt_builder.build_prompt(issue, comments, labels)
        
        # Call OpenAI with validation and limited retries for format compliance
        max_attempts = max(1, self.config['openai'].get('max_attempts', 2))
        wireframe = None
        for attempt in range(1, max_attempts + 1):
            wireframe = self.openai.call_openai(prompt_data['messages'])
            if self.openai.validate_wireframe(wireframe):
                break
            if attempt == max_attempts:
                raise ValueError(
                    f"Invalid wireframe output for issue #{issue_number} after {max_attempts} attempt(s)"
                )
            
        # Write wireframe
        filepath = self.writer.write_wireframe(
            domain,
            shortname,
            issue_number,
            wireframe,
            staging=staging
        )
        
        # Write metadata
        metadata = {
            'issue_number': issue_number,
            'issue_title': issue_title,
            'domain': domain,
            'shortname': shortname,
            'model': self.config['openai']['model'],
            'temperature': self.config['openai']['temperature'],
            'max_completion_tokens': self.config['openai'].get('max_completion_tokens'),
            'timestamp': datetime.utcnow().isoformat() + 'Z',
            'original_issue_url': issue['html_url'],
            'labels': labels,
            'summarized': len(comments) > 5  # Heuristic
        }
        
        self.writer.write_metadata(filepath, metadata)
        
        return filepath
        
        # Add to review CSV if staging
        if staging:
            csv_path = os.path.join(
                self.config['output']['base_dir'],
                domain,
                '.ui',
                'staging',
                'staging-review.csv'
            )
            self.writer.append_to_review_csv(
                csv_path,
                issue_number,
                domain,
                shortname,
                filepath
            )
            
    def _log_failure(self, issue: Dict, error: str, staging: bool):
        """Log failure to CSV."""
        domain = 'general'  # Default for failures
        
        csv_path = os.path.join(
            self.config['output']['base_dir'],
            domain,
            '.ui',
            'staging' if staging else '',
            'failures.csv'
        )
        
        # Create CSV if needed
        if not os.path.exists(csv_path):
            os.makedirs(os.path.dirname(csv_path), exist_ok=True)
            with open(csv_path, 'w', encoding='utf-8') as f:
                f.write('issue_number,title,error,timestamp\n')
                
        # Append failure
        with open(csv_path, 'a', encoding='utf-8') as f:
            timestamp = datetime.utcnow().isoformat()
            title = issue.get('title', '').replace(',', ';')
            error = error.replace(',', ';').replace('\n', ' ')
            f.write(f"{issue['number']},{title},{error},{timestamp}\n")
            
    def _print_summary(self):
        """Print processing summary."""
        print("\n" + "="*60)
        print("SUMMARY")
        print("="*60)
        print(f"Total issues: {self.stats['total']}")
        print(f"Success: {self.stats['success']}")
        print(f"Failed: {self.stats['failed']}")
        print(f"Skipped: {self.stats['skipped']}")
        print("="*60)

    def _load_processed_issues(self) -> set:
        """Load processed issue numbers from local list."""
        if not os.path.exists(self.processed_path):
            return set()
        try:
            with open(self.processed_path, 'r', encoding='utf-8') as f:
                data = f.read().strip()
                if not data:
                    return set()
                return set(int(num) for num in data.split('\n') if num.strip())
        except Exception:
            return set()

    def _record_processed_issue(self, issue_number: int):
        """Persist processed issue number to the local list."""
        if issue_number in self.processed_issues:
            return
        self.processed_issues.add(issue_number)
        os.makedirs(os.path.dirname(self.processed_path) or '.', exist_ok=True)
        with open(self.processed_path, 'a', encoding='utf-8') as f:
            f.write(f"{issue_number}\n")
    
    def _promote_from_staging(self, staging_filepath: str):
        """Move wireframe and metadata from staging to parent .ui directory."""
        import shutil
        
        # Compute parent path (remove /staging/ from path)
        parent_path = staging_filepath.replace('/staging/', '/')
        parent_dir = os.path.dirname(parent_path)
        
        # Ensure parent directory exists
        os.makedirs(parent_dir, exist_ok=True)
        
        # Move wireframe
        if os.path.exists(staging_filepath):
            shutil.move(staging_filepath, parent_path)
        
        # Move metadata sidecar
        meta_staging = staging_filepath.replace('.wf.md', '.wf.meta.json')
        meta_parent = parent_path.replace('.wf.md', '.wf.meta.json')
        if os.path.exists(meta_staging):
            shutil.move(meta_staging, meta_parent)


def main():
    """Main CLI entry point."""
    parser = argparse.ArgumentParser(
        description='Extract GitHub issues and generate wireframes'
    )
    
    parser.add_argument(
        '--repo',
        help='Repository in format owner/repo'
    )
    parser.add_argument(
        '--since',
        help='ISO timestamp to filter issues (e.g., 2025-01-01T00:00:00Z)'
    )
    parser.add_argument(
        '--max-issues',
        type=int,
        help='Maximum number of issues to process'
    )
    parser.add_argument(
        '--batch-size',
        type=int,
        default=10,
        help='Batch size for progress reporting'
    )
    parser.add_argument(
        '--staging',
        action='store_true',
        default=True,
        help='Write to staging directory for review'
    )
    parser.add_argument(
        '--auto-approve',
        action='store_true',
        help='Automatically promote wireframes from staging to parent .ui directory'
    )
    parser.add_argument(
        '--local-dump',
        help='Path to local JSON dump of issues'
    )
    parser.add_argument(
        '--config',
        default='config.yml',
        help='Path to config file'
    )
    parser.add_argument(
        '--verbose',
        action='store_true',
        help='Verbose output'
    )
    
    args = parser.parse_args()
    
    # Check environment variables
    if not os.getenv('GITHUB_TOKEN'):
        print("ERROR: GITHUB_TOKEN environment variable not set")
        sys.exit(1)
        
    if not os.getenv('OPENAI_API_KEY'):
        print("ERROR: OPENAI_API_KEY environment variable not set")
        sys.exit(1)
        
    # Initialize extractor
    extractor = WireframeExtractor(args.config)
    
    # Process issues
    extractor.process_issues(
        repo=args.repo,
        since=args.since,
        max_issues=args.max_issues,
        batch_size=args.batch_size,
        staging=args.staging,
        auto_approve=args.auto_approve,
        local_dump=args.local_dump
    )


if __name__ == '__main__':
    main()
