#!/usr/bin/env python3
"""
fix_issue_openai.py – OpenAI-based story editor

Usage:
    python3 fix_issue_openai.py <domain> <path/to/after.md> --update-github --owner <owner> --repo <repo>
    # Or process issues by id(s):
    python3 fix_issue_openai.py <domain> --issue 123 --issue-dir path/to/issues --update-github --owner <owner> --repo <repo>

Reads business rules from `durion/domains/{domain}/.business-rules/`, reads the issue file `after.md`,
calls OpenAI API to resolve open questions using the business rules, writes `fixed.md`, and optionally
updates the GitHub issue with new content and labels.

Environment:
    OPENAI_API_KEY  - Required for OpenAI API calls
    GITHUB_TOKEN    - Required for GitHub updates (--update-github)

Exit codes: 0 OK, 10 validation error (missing files), 20 unresolved questions remain.
"""

import argparse
import json
import logging
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

# Import reusable functions from publish_stories
try:
    import sys as _sys_mod
    _sys_mod.path.insert(0, str(Path(__file__).parent.parent))
    from publish_stories import (
        GitHubClient, parse_labels_from_after_md,
        extract_body_without_labels, apply_labels
    )
    GITHUB_AVAILABLE = True
except ImportError:
    GITHUB_AVAILABLE = False
    GitHubClient = None


def _http_post_json(url: str, headers: dict, payload: dict, timeout_s: int = 60) -> dict:
    """Make HTTP POST to OpenAI API (reference: story_update.py)."""
    data = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers=headers, method='POST')
    
    try:
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            body = resp.read().decode('utf-8')
            return json.loads(body)
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            err_data = json.loads(body)
        except:
            err_data = {'error': {'message': body}}
        err_msg = err_data.get('error', {}).get('message', str(e))
        raise RuntimeError(f"OpenAI API error ({e.code}): {err_msg}")
    except Exception as e:
        raise RuntimeError(f"HTTP error: {e}")


def call_openai(prompt: str, model: str = "gpt-5.2") -> str:
    """Call OpenAI API to process prompt. Returns model response text."""
    api_key = os.getenv('OPENAI_API_KEY')
    if not api_key:
        raise ValueError("OPENAI_API_KEY environment variable not set")
    
    url = "https://api.openai.com/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    
    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": (
                    "You are a story editor specializing in product management and technical documentation. "
                    "Your task is to review stories/issues, resolve ambiguities using provided business rules, "
                    "and output complete, implementation-ready content. "
                    "Preserve all YAML frontmatter and section structure. "
                    "Output only the updated story body; no explanations or meta-commentary."
                )
            },
            {
                "role": "user",
                "content": prompt
            }
        ],
        "temperature": 0.3,
        "max_completion_tokens": 12000,
    }
    
    logging.info(f"Calling OpenAI API with model {model}")
    response = _http_post_json(url, headers, payload, timeout_s=120)
    
    if 'choices' in response and len(response['choices']) > 0:
        return response['choices'][0]['message']['content']
    else:
        raise RuntimeError(f"Unexpected OpenAI response: {response}")


def parse_issue_list_arg(arg: str):
    """Parse comma-separated issue list."""
    items = []
    for part in (arg or '').split(','):
        part = part.strip()
        if part:
            items.append(part)
    return items


def parse_issue_range_arg(arg: str):
    """Parse issue range like '100-110' (inclusive)."""
    if not arg or '-' not in arg:
        return []
    try:
        start_str, end_str = arg.split('-', 1)
        start = int(start_str.strip())
        end = int(end_str.strip())
        if start > end:
            start, end = end, start
        return [str(n) for n in range(start, end + 1)]
    except Exception:
        return []


def resolve_issue_after_paths(issue_ids, issue_dir: Path):
    """Resolve list of issue IDs to after.md file paths."""
    paths = []
    for iid in issue_ids:
        p = issue_dir / str(iid) / 'after.md'
        if p.exists():
            paths.append(p)
    return paths


def extract_domain_from_labels(labels):
    """Extract domain from labels matching pattern 'domain:*'."""
    for label in labels:
        if label.startswith('domain:'):
            return label.split(':', 1)[1]
    return None


def extract_domain_from_required_section(body: str):
    """Extract domain from 'domain:*' label in ### Required section of markdown."""
    match = re.search(r'###\s+Required\s*\n(.*?)(?=\n###|\n##\s|\Z)', body, re.DOTALL)
    if match:
        required_section = match.group(1)
        domain_match = re.search(r'-\s*domain:(\S+)', required_section)
        if domain_match:
            return domain_match.group(1).strip()
    return None


def get_workspace_root() -> Path:
    """Get workspace root directory relative to HOME.
    
    Returns Path to durion workspace root (typically ~/Projects/durion).
    Can be overridden with DURION_WORKSPACE_ROOT environment variable.
    """
    env_root = os.getenv('DURION_WORKSPACE_ROOT')
    if env_root:
        return Path(env_root).resolve()
    
    # Default: ~/Projects/durion
    return Path.home() / 'Projects' / 'durion'


def parse_frontmatter_labels(text: str):
    """Parse YAML frontmatter and extract labels."""
    m = re.match(r'^---\s*\n(.*?)\n---\s*\n', text, flags=re.S)
    if not m:
        return None, text
    
    fm = m.group(1)
    labels = []
    lm = re.search(r'labels:\s*\n([\s\S]+?)(?=\n[^\s-]|\Z)', fm)
    if lm:
        block = lm.group(1)
        for line in block.splitlines():
            line = line.strip().lstrip('-').strip()
            if line:
                labels.append(line)
    
    body = text[m.end():]
    return labels, body


def parse_markdown_labels(text: str):
    """Parse labels from markdown '## 🏷️ Labels (Proposed)' section.
    
    Returns list of labels found in Required, Recommended, and Blocking/Risk sections.
    Returns empty list if no labels section found.
    """
    # Find the Labels section
    labels_section = re.search(
        r'##\s*🏷️\s*Labels\s*\(Proposed\)\s*\n(.*?)(?=\n##\s|\Z)',
        text,
        re.DOTALL | re.IGNORECASE
    )
    
    if not labels_section:
        return []
    
    section_content = labels_section.group(1)
    labels = []
    
    # Extract labels from list items (lines starting with - after ###)
    for line in section_content.splitlines():
        line = line.strip()
        # Skip section headers and empty lines
        if line.startswith('###') or line.startswith('**') or not line:
            continue
        # Extract label from list item
        if line.startswith('-'):
            label = line.lstrip('-').strip()
            # Skip non-label lines (e.g., "none")
            if label and ':' in label and label.lower() != 'none':
                labels.append(label)
    
    return labels


def find_open_questions(body: str):
    """Find open questions in body using various markers."""
    patterns = [
        r'(?m)^###\s*Q:\s*(.+?)(?=\n###|\n##\s|\n$)',
        r'(?m)^Q:\s*(.+?)(?=\nQ:|\n##\s|\n$)',
        r'(?m)^Open Question:.*$\n(?:[\s\S]*?)(?=\n\s*\n|\n##|$)',
        r'(?m)^TODO:\s*(.+)$',
        r'(?m)^FIXME:\s*(.+)$'
    ]
    qs = []
    for p in patterns:
        for m in re.finditer(p, body):
            q = m.group(0).strip()
            if q not in qs:
                qs.append(q)
    return qs


def load_text_file(path: Path) -> str:
    """Load text file content."""
    return path.read_text(encoding='utf-8')


def _load_business_rules_files(root_repo: Path, domain: str) -> list:
    """Load business rules files for a domain."""
    br_dir = root_repo / 'domains' / domain / '.business-rules'
    if not br_dir.exists() or not br_dir.is_dir():
        logging.warning(f'Business rules dir not found: {br_dir}')
        return []
    
    priority_files = ['AGENT_GUIDE.md', 'DOMAIN_NOTES.md', 'STORY_VALIDATION_CHECKLIST.md']
    files = []
    
    for fname in priority_files:
        fpath = br_dir / fname
        if fpath.exists():
            files.append(fpath)
    
    for fpath in sorted(br_dir.glob('*.md')):
        if fpath.name not in priority_files and fpath not in files:
            files.append(fpath)
    
    return files


def build_full_prompt(root_repo: Path, prompt_file: Path, domain: str, 
                     issue_body: str, issue_title: str = None) -> str:
    """Build comprehensive prompt with business rules and agent guidance.
    
    This uses the full prompt file and includes all business rules, similar to
    how story_update.py builds comprehensive agent prompts.
    """
    parts = []
    
    # 1. Main Prompt Instructions
    parts.append("=" * 100)
    parts.append("STORY EDITING INSTRUCTIONS")
    parts.append("=" * 100)
    if prompt_file.exists():
        parts.append(load_text_file(prompt_file))
    else:
        parts.append(
            "Review and rewrite the issue body resolving open questions using the business rules provided below."
        )
    parts.append("")
    
    # 2. Business Rules for Domain
    br_files = _load_business_rules_files(root_repo, domain)
    if br_files:
        parts.append("=" * 100)
        parts.append(f"BUSINESS RULES FOR DOMAIN: {domain.upper()}")
        parts.append("=" * 100)
        for br_file in br_files:
            parts.append(f"\n--- {br_file.name} ---\n")
            try:
                content = load_text_file(br_file)
                parts.append(content)
            except Exception as e:
                parts.append(f"[Error loading {br_file.name}: {e}]")
        parts.append("")
    
    # 3. Issue Content
    parts.append("=" * 100)
    parts.append("ISSUE TO BE UPDATED")
    parts.append("=" * 100)
    if issue_title:
        parts.append(f"Title: {issue_title}")
        parts.append("")
    parts.append(issue_body)
    parts.append("=" * 100)
    parts.append("")
    
    # 3.5. Story Authoring Agent Guidance
    story_authoring_agent_path = root_repo / '.github' / 'agents' / 'story-authoring.agent.md'
    if story_authoring_agent_path.exists():
        parts.append("=" * 100)
        parts.append("STORY AUTHORING GUIDANCE")
        parts.append("=" * 100)
        try:
            story_authoring_content = load_text_file(story_authoring_agent_path)
            parts.append(story_authoring_content)
        except Exception as e:
            parts.append(f"[Error loading story-authoring.agent.md: {e}]")
        parts.append("")
    
    # 4. Final Instructions
    parts.append("-" * 100)
    parts.append("TASK:")
    parts.append("-" * 100)
    parts.append(
        f"Review and update the included issue for the '{domain}' domain. "
        "Resolve any open questions, ambiguities, and clarifications using the business rules and guidance provided. "
        "Produce an implementation-ready rewrite following all guidance. "
        "Ensure output contains required section headers and labels as specified."
    )
    parts.append("")
    parts.append("OUTPUT REQUIREMENTS:")
    parts.append("-" * 100)
    parts.append("Output ONLY the complete, updated story/issue body:")
    parts.append("- Include complete YAML frontmatter with all labels")
    parts.append("- Preserve all section structure from original")
    parts.append("- Resolve open questions using business rules provided")
    parts.append("- Include decision rationale for each resolution")
    parts.append("- Use proper labels and markdown formatting")
    parts.append("- No meta-commentary; only the final story content")
    parts.append("")
    
    return "\n".join(parts)


def ensure_labels_exist(client: GitHubClient, owner: str, repo: str, labels: list) -> dict:
    """Ensure repository labels exist, creating missing ones with default colors.
    
    Returns dict mapping label names to their creation status (True=created, False=existed, None=error).
    """
    if not labels:
        return {}
    
    # Default label colors by prefix
    label_colors = {
        'type:': '0075ca',      # blue
        'domain:': '7057ff',    # purple
        'status:': 'fbca04',    # yellow
        'agent:': 'bfdadc',     # light blue
        'blocked:': 'd73a4a',   # red
        'risk:': 'e99695',      # light red
        'priority:': 'ff6347',  # tomato
    }
    
    results = {}
    for label in labels:
        try:
            # Try to get existing label
            url = f"https://api.github.com/repos/{owner}/{repo}/labels/{label}"
            resp = client._request("GET", url)
            if resp.status_code == 200:
                results[label] = False  # Already exists
                continue
        except Exception:
            pass  # Label doesn't exist, will create it
        
        # Determine color based on prefix
        color = 'cccccc'  # default gray
        for prefix, prefix_color in label_colors.items():
            if label.startswith(prefix):
                color = prefix_color
                break
        
        # Create label
        try:
            url = f"https://api.github.com/repos/{owner}/{repo}/labels"
            client._request("POST", url, json={"name": label, "color": color})
            logging.info(f"Created repository label: {label}")
            results[label] = True
        except Exception as e:
            logging.warning(f"Failed to create label '{label}': {e}")
            results[label] = None
    
    return results


def update_github_issue(owner: str, repo: str, issue_number: int, 
                       new_body: str, labels: list = None, model: str = None) -> bool:
    """Update GitHub issue with new body and labels, and add a summary comment."""
    if not GITHUB_AVAILABLE or not GitHubClient:
        logging.warning('GitHub client not available; skipping GitHub update')
        return False
    
    token = os.getenv('GITHUB_TOKEN')
    if not token:
        logging.warning('GITHUB_TOKEN not set; skipping GitHub update')
        return False
    
    try:
        client = GitHubClient(token)
        
        # Extract body without labels frontmatter
        body_only = extract_body_without_labels(new_body)
        
        # Update issue body
        client.update_issue_body(owner, repo, issue_number, body_only)
        logging.info(f'Updated GitHub issue {owner}/{repo}#{issue_number}')
        
        # Update labels if provided
        label_summary = ""
        if labels is not None:  # Allow empty list to clear all labels
            # Ensure all labels exist in repository first
            label_creation_results = ensure_labels_exist(client, owner, repo, labels)
            created_labels = [l for l, created in label_creation_results.items() if created]
            if created_labels:
                logging.info(f"Created {len(created_labels)} new repository labels: {created_labels}")
            
            # Get current labels to track what's being removed
            current_labels = client.get_issue_labels(owner, repo, issue_number)
            labels_to_remove = [l for l in current_labels if l not in labels]
            labels_to_add = [l for l in labels if l not in current_labels]
            
            # Replace all labels (GitHub API supports setting labels directly)
            try:
                url = f"https://api.github.com/repos/{owner}/{repo}/issues/{issue_number}/labels"
                client._request("PUT", url, json={"labels": labels})
                
                logging.info(f'Updated labels for {owner}/{repo}#{issue_number}')
                if labels_to_add:
                    logging.info(f'  Added: {labels_to_add}')
                if labels_to_remove:
                    logging.info(f'  Removed: {labels_to_remove}')
                
                label_summary = f"\n- **Labels Updated:** {', '.join(f'`{l}`' for l in sorted(labels)) if labels else 'all cleared'}"
                if created_labels:
                    label_summary += f" (created {len(created_labels)} new labels)"
                if labels_to_remove:
                    label_summary += f"\n- **Labels Removed:** {', '.join(f'`{l}`' for l in sorted(labels_to_remove))}"
            except Exception as e:
                logging.warning(f'Failed to update labels: {e}')
                label_summary = "\n- **Labels:** Update failed"
        
        # Add summary comment
        model_info = f" using {model}" if model else ""
        comment_body = f"""## 🤖 Story Updated by AI Agent

This issue has been automatically processed and updated{model_info}.

**Updates Applied:**
- ✅ Issue body refined and updated{label_summary}
- 📝 Open questions resolved using domain business rules
- 🎯 Story brought to implementation-ready state

_Generated on {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S')} UTC_"""
        
        try:
            client.create_issue_comment(owner, repo, issue_number, comment_body)
            logging.info(f'Added update comment to {owner}/{repo}#{issue_number}')
        except Exception as e:
            logging.warning(f'Failed to add comment: {e}')
        
        return True
    except Exception as e:
        logging.error(f'Failed to update GitHub issue: {e}')
        return False


def process_one(after: Path, domain_arg: str, args, issue_number: int = None) -> int:
    """Process a single issue file with OpenAI."""
    log_file = after.parent / 'fix_issue_debug.log'
    logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s [%(levelname)s] %(message)s',
        handlers=[
            logging.FileHandler(log_file, mode='a'),
            logging.StreamHandler(sys.stderr)
        ],
        force=True
    )
    logger = logging.getLogger(__name__)
    
    logger.info(f'=== Processing issue: {after} ===')
    logger.debug(f'Domain arg: {domain_arg}')
    logger.debug(f'Issue number: {issue_number}')
    logger.debug(f'Args: {vars(args)}')
    
    if not after.exists():
        logger.error(f'File not found: {after}')
        return 10
    
    text = after.read_text(encoding='utf-8')
    labels, body = parse_frontmatter_labels(text)
    if labels is None:
        # Try parsing markdown labels section as fallback
        labels = parse_markdown_labels(text)
        body = text
    logger.debug(f'Parsed {len(labels)} labels from after.md: {labels}')
    
    # Determine domain: CLI arg takes precedence, then ### Required section, then labels
    domain = domain_arg
    if not domain:
        domain = extract_domain_from_required_section(body)
    if not domain:
        domain = extract_domain_from_labels(labels)
    if not domain:
        logger.error('Domain not specified and no domain label found')
        return 10
    
    logger.info(f'Domain: {domain}')
    
    # Build prompt
    workspace_root = get_workspace_root()
    logger.debug(f'Workspace root: {workspace_root}')
    
    prompt_path = Path(args.prompt_file).resolve() if args.prompt_file else None
    
    if not prompt_path or not prompt_path.exists():
        # Default prompt location relative to workspace root
        default_prompt = workspace_root / '.github' / 'prompts' / 'story-frontend-rewrite.prompt.md'
        logger.debug(f'Trying default prompt: {default_prompt}')
        if default_prompt.exists():
            prompt_path = default_prompt
        else:
            logger.warning(f'Default prompt file not found: {default_prompt}')
            prompt_path = None
    
    if prompt_path and not prompt_path.exists():
        logger.warning(f'Prompt file does not exist: {prompt_path}')
        prompt_path = None
    
    full_prompt_text = build_full_prompt(workspace_root, prompt_path, domain, body, after.stem)
    logger.debug(f'Full prompt length: {len(full_prompt_text)} characters')
    
    # Call OpenAI
    try:
        logger.info(f'Calling OpenAI API with model: {args.model}')
        response_text = call_openai(full_prompt_text, args.model)
        logger.info(f'OpenAI response received: {len(response_text)} characters')
    except Exception as e:
        logger.error(f'OpenAI API failed: {e}')
        print(f'ERROR: OpenAI API failed: {e}', file=sys.stderr)
        return 10
    
    # Parse response - OpenAI returns the full updated story body
    response_body = response_text.strip()
    
    # Write fixed.md
    fixed_path = after.parent / 'fixed.md'
    try:
        fixed_path.write_text(response_body, encoding='utf-8')
        logger.info(f'Wrote {fixed_path}')
        print(f'✓ Wrote {fixed_path}')
    except Exception as e:
        logger.error(f'Failed to write {fixed_path}: {e}')
        return 10
    
    # Update GitHub if requested
    if args.update_github and not args.dry_run:
        if not issue_number:
            logger.warning('No issue number; skipping GitHub update')
        elif not args.owner or not args.repo:
            logger.error('--owner and --repo required for --update-github')
            return 10
        else:
            # Parse labels from response - try YAML frontmatter first, then markdown section
            resp_labels, _ = parse_frontmatter_labels(response_body)
            
            if resp_labels is None:
                # Try markdown labels section
                resp_labels = parse_markdown_labels(response_body)
                logger.debug(f'Parsed {len(resp_labels)} labels from fixed.md markdown section: {resp_labels}')
            else:
                logger.debug(f'Parsed {len(resp_labels)} labels from fixed.md YAML frontmatter: {resp_labels}')
            
            # Use response labels if found (even if empty list)
            # Fall back to original labels only if no labels found in response
            if resp_labels:
                labels_out = list(sorted(set(resp_labels)))
                labels_source = "response (fixed.md)"
            elif labels:
                labels_out = list(sorted(set(labels)))
                labels_source = "original (after.md)"
            else:
                labels_out = []
                labels_source = "none found"
            
            logger.info(f'Updating GitHub issue #{issue_number} with {len(labels_out)} labels')
            logger.debug(f'Labels source: {labels_source} - Labels: {labels_out}')
            update_github_issue(args.owner, args.repo, issue_number, response_body, labels_out, args.model)
    
    return 0


def main():
    parser = argparse.ArgumentParser(description='Update issues using OpenAI API')
    parser.add_argument('domain', nargs='?', default=None, 
                       help='Domain name (optional if domain label exists in after.md)')
    parser.add_argument('after_md', nargs='?', default=None, help='Path to after.md file')
    parser.add_argument('--issue', help='Single issue id to process')
    parser.add_argument('--issues', help='Comma-separated list of issue ids')
    parser.add_argument('--issue-range', help='Issue id range, e.g. 100-110 (inclusive)')
    parser.add_argument('--issue-dir', help='Directory containing issue folders (each with after.md)')
    parser.add_argument('--dry-run', action='store_true', 
                       help='Do not write files or call external APIs')
    parser.add_argument('--prompt-file', help='Path to prompt file')
    parser.add_argument('--model', default='gpt-5.2', help='OpenAI model to use')
    parser.add_argument('--update-github', action='store_true', 
                       help='Update issue body and labels in GitHub')
    parser.add_argument('--owner', help='GitHub repo owner')
    parser.add_argument('--repo', help='GitHub repo name')
    args = parser.parse_args()
    
    # Determine list of after.md paths
    after_paths = []
    domain_arg = args.domain
    
    if args.issue or args.issues or args.issue_range:
        if not args.issue_dir:
            print('Error: --issue-dir required with --issue/--issues/--issue-range', file=sys.stderr)
            return 1
        
        issue_dir = Path(args.issue_dir)
        issue_ids = []
        
        if args.issue:
            issue_ids.append(args.issue)
        if args.issues:
            issue_ids.extend(parse_issue_list_arg(args.issues))
        if args.issue_range:
            issue_ids.extend(parse_issue_range_arg(args.issue_range))
        
        # De-dup while preserving order
        seen = set()
        unique_ids = []
        for iid in issue_ids:
            if iid not in seen:
                seen.add(iid)
                unique_ids.append(iid)
        
        after_paths = resolve_issue_after_paths(unique_ids, issue_dir)
        domain_arg = None  # Extract from issue metadata
    elif args.after_md:
        after_paths = [Path(args.after_md)]
    else:
        print('Error: provide either <after_md> or --issue/--issues/--issue-range', file=sys.stderr)
        return 1
    
    if args.update_github and not args.owner:
        print('Error: --owner required with --update-github', file=sys.stderr)
        return 1
    
    if args.update_github and not args.repo:
        print('Error: --repo required with --update-github', file=sys.stderr)
        return 1
    
    final_exit = 0
    for ap in after_paths:
        logger = logging.getLogger(__name__)
        print(f'\nProcessing: {ap}')
        
        # Extract issue number from path if available
        issue_num = None
        try:
            issue_num = int(ap.parent.name)
        except (ValueError, AttributeError):
            pass
        
        exit_code = process_one(ap, domain_arg, args, issue_num)
        if exit_code != 0:
            final_exit = exit_code
    
    return final_exit


if __name__ == '__main__':
    sys.exit(main())
