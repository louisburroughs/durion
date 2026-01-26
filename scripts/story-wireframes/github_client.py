"""GitHub API client for fetching issues and comments."""
import os
import json
from typing import List, Dict, Optional
import requests


class GitHubClient:
    """Client for interacting with GitHub API."""
    
    def __init__(self, token: Optional[str] = None):
        """
        Initialize GitHub client.
        
        Args:
            token: GitHub API token (defaults to GITHUB_TOKEN env var)
        """
        self.token = token or os.getenv('GITHUB_TOKEN')
        if not self.token:
            raise ValueError("GitHub token required. Set GITHUB_TOKEN environment variable.")
            
        self.base_url = "https://api.github.com"
        self.headers = {
            'Authorization': f'token {self.token}',
            'Accept': 'application/vnd.github.v3+json'
        }
        
    def fetch_issues(
        self,
        repo: str,
        since: Optional[str] = None,
        state: str = 'open',
        labels: Optional[str] = 'type:story'
    ) -> List[Dict]:
        """
        Fetch issues from repository.
        
        Args:
            repo: Repository in format 'owner/repo'
            since: ISO 8601 timestamp to filter issues
            state: Issue state ('open', 'closed', 'all')
            labels: Comma-separated list of labels to filter (default: 'type:story')
            
        Returns:
            List of issue dictionaries
        """
        url = f"{self.base_url}/repos/{repo}/issues"
        params = {
            'state': state,
            'labels': labels,
            'per_page': 100
        }
        
        if since:
            params['since'] = since
            
        issues = []
        page = 1
        
        while True:
            params['page'] = page
            response = requests.get(url, headers=self.headers, params=params)
            response.raise_for_status()
            
            page_issues = response.json()
            if not page_issues:
                break
                
            # Filter out pull requests (they appear in issues endpoint)
            page_issues = [issue for issue in page_issues if 'pull_request' not in issue]
            issues.extend(page_issues)
            
            # Check if there are more pages
            if 'next' not in response.links:
                break
                
            page += 1
            
        print(f"Fetched {len(issues)} issues from {repo}")
        return issues
        
    def fetch_comments(self, repo: str, issue_number: int) -> List[Dict]:
        """
        Fetch comments for an issue.
        
        Args:
            repo: Repository in format 'owner/repo'
            issue_number: Issue number
            
        Returns:
            List of comment dictionaries
        """
        url = f"{self.base_url}/repos/{repo}/issues/{issue_number}/comments"
        params = {'per_page': 100}
        
        comments = []
        page = 1
        
        while True:
            params['page'] = page
            response = requests.get(url, headers=self.headers, params=params)
            response.raise_for_status()
            
            page_comments = response.json()
            if not page_comments:
                break
                
            comments.extend(page_comments)
            
            if 'next' not in response.links:
                break
                
            page += 1
            
        return comments
        
    def load_local_dump(self, path: str) -> List[Dict]:
        """
        Load issues from local JSON dump.
        
        Args:
            path: Path to JSON file
            
        Returns:
            List of issue dictionaries
        """
        with open(path, 'r', encoding='utf-8') as f:
            issues = json.load(f)
            
        print(f"Loaded {len(issues)} issues from {path}")
        return issues
