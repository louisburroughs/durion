#!/usr/bin/env python3
"""
fix_issue.py

Usage:
    python3 fix_issue.py <domain> <path/to/after.md>
    # Or process issues by id(s):
    python3 fix_issue.py <domain> --issue 123 --issue-dir path/to/issues
    python3 fix_issue.py <domain> --issues 101,103,109 --issue-dir path/to/issues
    python3 fix_issue.py <domain> --issue-range 200-210 --issue-dir path/to/issues

Reads business rules from `durion/domains/{domain}/.business-rules/`, reads the issue file `after.md`,
attempts to resolve open questions using the business rules, and writes `fixed.md` next to `after.md`.

This script also prints a Copilot prompt JSON to stdout when asked via --print-copilot, but it does not call any network service.

Exit codes: 0 OK, 10 validation error (missing files), 20 unresolved questions remain (but fixed.md still written).
"""
import sys
import re
from pathlib import Path
import json
import subprocess
import shutil
import argparse
import os

# Import reusable functions from publish_stories
try:
    import sys as _sys_mod
    _sys_mod.path.insert(0, str(Path(__file__).parent.parent))
    from publish_stories import GitHubClient, parse_labels_from_after_md, extract_body_without_labels, apply_labels
    GITHUB_AVAILABLE = True
except ImportError:
    GITHUB_AVAILABLE = False
    GitHubClient = None


def parse_issue_list_arg(arg: str):
    # comma-separated values, trim spaces
    items = []
    for part in (arg or '').split(','):
        part = part.strip()
        if part:
            items.append(part)
    return items


def parse_issue_range_arg(arg: str):
    # format: start-end (inclusive)
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
    paths = []
    for iid in issue_ids:
        p = issue_dir / str(iid) / 'after.md'
        paths.append(p)
    return paths


def extract_domain_from_labels(labels):
    """Extract domain from labels matching pattern 'domain:*'."""
    for label in labels:
        if label.startswith('domain:'):
            return label.split(':', 1)[1]
    return None


def update_github_issue(after: Path, fixed_path: Path, owner: str, repo: str, issue_number: int) -> bool:
    """Update GitHub issue body and labels. Returns True on success."""
    if not GITHUB_AVAILABLE or not GitHubClient:
        print('GitHub integration not available; skipping update', file=sys.stderr)
        return False
    
    token = os.getenv('GITHUB_TOKEN')
    if not token:
        print('GITHUB_TOKEN env var required for GitHub updates', file=sys.stderr)
        return False
    
    try:
        client = GitHubClient(token, verbose=False)
        
        # Read fixed.md for labels and body
        fixed_text = fixed_path.read_text(encoding='utf-8')
        
        # Parse labels from fixed.md (if it has labels section)
        try:
            required, recommended, blocking = parse_labels_from_after_md(fixed_text)
        except Exception:
            # If parsing fails, try to extract from after.md
            try:
                after_text = after.read_text(encoding='utf-8')
                required, recommended, blocking = parse_labels_from_after_md(after_text)
            except Exception:
                required, recommended, blocking = [], [], []
        
        # Extract body (without labels section)
        try:
            body = extract_body_without_labels(fixed_text)
        except Exception:
            body = fixed_text
        
        if not body.strip():
            print(f'No body content to update for #{issue_number}', file=sys.stderr)
            return False
        
        # Update issue body
        client.update_issue_body(owner, repo, issue_number, body)
        print(f'Updated body for GitHub issue #{issue_number}')
        
        # Apply labels
        try:
            apply_labels(client, owner, repo, issue_number, required, recommended, blocking, include_recommended=True)
            print(f'Applied labels to GitHub issue #{issue_number}')
        except Exception as e:
            print(f'Warning: failed to apply labels: {e}', file=sys.stderr)
        
        return True
    except Exception as e:
        print(f'Error updating GitHub: {e}', file=sys.stderr)
        return False


def read_prompt_file(prompt_file: Path) -> str:
    """Read prompt instructions from file. Returns default if not found or unreadable."""
    default_prompt = "Resolve open questions in the issue using canonical business rules. For each resolution, include the decision ID or source and one-line rationale."
    if not prompt_file:
        return default_prompt
    try:
        if prompt_file.exists():
            return prompt_file.read_text(encoding='utf-8').strip()
    except Exception as e:
        print(f'Warning: failed to read prompt file: {e}', file=sys.stderr)
    return default_prompt


def extract_domain_from_labels(labels):
    """Extract domain from labels matching pattern 'domain:*'."""
    for label in labels:
        if label.startswith('domain:'):
            return label.split(':', 1)[1]
    return None


def process_one(after: Path, domain_arg: str, args, issue_number: int = None) -> int:
    if not after.exists():
        print('after.md not found:', after, file=sys.stderr)
        return 10

    text = after.read_text(encoding='utf-8')
    labels, body = parse_frontmatter_labels(text)
    if labels is None:
        labels = []
    
    # Determine domain: CLI arg takes precedence, then labels
    domain = domain_arg
    if not domain:
        domain = extract_domain_from_labels(labels)
    if not domain:
        print(f'Domain not specified and no domain label found in {after}', file=sys.stderr)
        print('Either provide domain as CLI argument or add a "domain:*" label to after.md', file=sys.stderr)
        return 10
    
    # Load business rules for this domain
    domain_dir = Path.cwd() / 'domains' / domain
    br_index = read_business_rules(domain_dir)
    if not br_index:
        print(f'Business rules missing or empty for domain "{domain}"', file=sys.stderr)
    
    questions = find_open_questions(body)

    new_body, unresolved = update_body(body, questions, br_index)

    label_changes = []
    labels_out = list(sorted(set(labels)))
    if unresolved:
        if 'blocked:clarification' not in labels_out:
            labels_out.append('blocked:clarification')
            label_changes.append('added: blocked:clarification')
        if 'needs:domain-owner' not in labels_out:
            labels_out.append('needs:domain-owner')
            label_changes.append('added: needs:domain-owner')
        exit_code = 20
    else:
        # remove blocked if present
        if 'blocked:clarification' in labels_out:
            labels_out.remove('blocked:clarification')
            label_changes.append('removed: blocked:clarification')
        if 'status:ready-for-review' not in labels_out:
            labels_out.append('status:ready-for-review')
            label_changes.append('added: status:ready-for-review')
        exit_code = 0

    fixed_path = write_fixed(after, labels_out, new_body, label_changes, dry_run=args.dry_run)
    if args.dry_run:
        print('DRY RUN: would have written', fixed_path)
    else:
        print('WROTE', fixed_path)
    
    # Update GitHub if requested
    if args.update_github and not args.dry_run:
        if not args.owner or not args.repo:
            print('--owner and --repo required for --update-github', file=sys.stderr)
            return 10
        # Use issue_number if provided, otherwise try to extract from after.md path
        if not issue_number:
            try:
                issue_number = int(after.parent.name)
            except (ValueError, AttributeError):
                print('Cannot determine issue number from path; use --issue flag', file=sys.stderr)
                return 10
        update_github_issue(after, fixed_path, args.owner, args.repo, issue_number)

    if args.print_copilot:
        # provide a small business_rules_snippet for copilot
        snippets = {k: v for k, v in list(br_index.items())[:10]}
        payload = build_copilot_prompt(after.stem, body, snippets, domain, args.agent_ref)
        print(json.dumps(payload, indent=2))

    if args.rebuild_with_copilot:
        # Read prompt from file or use default
        prompt_instructions = read_prompt_file(Path(args.prompt_file) if args.prompt_file else None)
        agent_path = Path('/home/louisb/Projects/durion/.github/agents/story-authoring.agent.md')
        snippets = {k: v for k, v in list(br_index.items())[:20]}
        payload = build_copilot_prompt(after.stem, body, snippets, domain, args.agent_ref)

        def call_copilot_cli(prompt_text: str, agent_file: Path, payload: dict) -> bool:
            copilot_exe = shutil.which('copilot')
            if not copilot_exe:
                print('copilot CLI not found in PATH; skipping copilot run', file=sys.stderr)
                return False
            if not agent_file.exists():
                print(f'Agent file not found: {agent_file}', file=sys.stderr)
                return False

            cmd = [copilot_exe, 'agent', 'run', '--prompt', prompt_text, '--agent-file', str(agent_file)]
            try:
                proc = subprocess.run(cmd, input=json.dumps(payload), text=True, capture_output=True)
                print('Copilot CLI exit code:', proc.returncode)
                if proc.stdout:
                    print('Copilot stdout:\n', proc.stdout)
                if proc.stderr:
                    print('Copilot stderr:\n', proc.stderr, file=sys.stderr)
                return proc.returncode == 0
            except Exception as e:
                print('Error running copilot CLI:', e, file=sys.stderr)
                return False

        if args.dry_run:
            print('DRY RUN: would run copilot CLI with prompt:')
            print(f'---\n{prompt_instructions}\n---')
            print('DRY RUN: would use agent file:', agent_path)
            print('DRY RUN: payload:')
            print(json.dumps(payload, indent=2))
        else:
            call_copilot_cli(prompt_instructions, agent_path, payload)

    return exit_code


def read_business_rules(domain_dir: Path):
    br_dir = domain_dir / '.business-rules'
    if not br_dir.exists() or not br_dir.is_dir():
        return {}
    files = sorted(br_dir.glob('*.md'))
    data = {}
    for f in files:
        txt = f.read_text(encoding='utf-8')
        # index DECISION-INVENTORY-### occurrences
        for m in re.finditer(r'(DECISION-INVENTORY-\d{3,})', txt):
            key = m.group(1)
            # capture a short paragraph around the decision
            start = max(0, m.start() - 200)
            snippet = txt[start:m.end()+200]
            data[key] = {'file': f.name, 'snippet': snippet.strip()}
        # also index headings
        for m in re.finditer(r'^###?\s*(.+)$', txt, flags=re.MULTILINE):
            heading = m.group(1).strip()
            data.setdefault(heading.lower(), {'file': f.name, 'snippet': ''})
    return data


def parse_frontmatter_labels(text: str):
    # Supports simple YAML frontmatter with `labels:` list
    m = re.match(r'^---\s*\n(.*?)\n---\s*\n', text, flags=re.S)
    if not m:
        return None, text
    fm = m.group(1)
    labels = []
    lm = re.search(r'labels:\s*\n([\s\S]+)$', fm)
    if lm:
        block = lm.group(1)
        for line in block.splitlines():
            line = line.strip().lstrip('-').strip()
            if line:
                labels.append(line)
    body = text[m.end():]
    return labels, body


def find_open_questions(body: str):
    # heuristics: lines starting with Q:, ### Q:, Open Question:, TODO:, FIXME:
    patterns = [r'(?m)^###\s*Q:\s*(.+?)(?=\n###|\n##\s|\n$)',
                r'(?m)^Q:\s*(.+?)(?=\nQ:|\n##\s|\n$)',
                r'(?m)^Open Question:.*$\n(?:[\s\S]*?)(?=\n\s*\n|\n##|$)',
                r'(?m)^TODO:\s*(.+)$', r'(?m)^FIXME:\s*(.+)$']
    qs = []
    for p in patterns:
        for m in re.finditer(p, body):
            q = m.group(0).strip()
            if q not in qs:
                qs.append(q)
    return qs


def simple_match(question: str, br_index: dict):
    qwords = re.findall(r"[a-zA-Z0-9_]{3,}", question.lower())
    # try to match decision ids first
    for key in br_index.keys():
        if key.lower() in question.lower():
            entry = br_index[key]
            return key, entry
    # otherwise keyword overlap
    best = (None, 0)
    for key, entry in br_index.items():
        score = 0
        text = (entry.get('snippet') or '')
        for w in qwords:
            if w in text.lower() or w in str(key).lower():
                score += 1
        if score > best[1]:
            best = (key, score)
    if best[0] and best[1] > 0:
        return best[0], br_index[best[0]]
    return None, None


def update_body(body: str, questions: list, br_index: dict):
    unresolved = []
    new_body = body
    for q in questions:
        key, entry = simple_match(q, br_index)
        if entry:
            summary = entry.get('snippet', '').split('\n')[0]
            replacement = f"**Resolved:** {summary}  \n\n*Resolved by: [{entry['file']}](domains/{entry['file']})"
            # deterministic: include decision id if key matches pattern
            if re.match(r'DECISION-INVENTORY-\d{3,}', str(key or '')):
                replacement += f" (Decision `{key}`)"
            new_body = new_body.replace(q, replacement)
        else:
            # leave placeholder
            placeholder = f"**ACTION REQUIRED:** Clarification needed for the following: {q}"
            new_body = new_body.replace(q, placeholder)
            unresolved.append(q)
    return new_body, unresolved


def write_fixed(path_after: Path, labels_out, body_out, label_changes, dry_run: bool = False):
    fixed_path = path_after.parent / 'fixed.md'
    fm_lines = ['---', 'labels:']
    for l in labels_out:
        fm_lines.append(f'  - {l}')
    fm_lines.append('---')
    content = '\n'.join(fm_lines) + '\n\n' + body_out + '\n\n' + '---\nLabels changed:\n'
    for change in label_changes:
        content += f"- {change}\n"
    if dry_run:
        print('DRY RUN: would write the following content to', fixed_path)
        print('--- BEGIN DRY RUN CONTENT ---')
        print(content)
        print('--- END DRY RUN CONTENT ---')
        return fixed_path
    fixed_path.write_text(content, encoding='utf-8')
    return fixed_path


def build_copilot_prompt(issue_title, original_body, business_snippets, domain, agent_reference):
    payload = {
        'issue_title': issue_title,
        'original_body': original_body,
        'business_rules_snippet': business_snippets,
        'domain': domain,
        'agent_reference': agent_reference,
        'instruction': 'Rewrite the issue body resolving open questions using the canonical answers. For each replacement include the decision ID or filename and one-line rationale. Output only the Markdown body.'
    }
    return payload


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('domain', nargs='?', help='Domain name (optional if domain label exists in after.md)')
    parser.add_argument('after_md', nargs='?', help='path to after.md')
    parser.add_argument('--issue', help='Single issue id to process')
    parser.add_argument('--issues', help='Comma-separated list of issue ids to process')
    parser.add_argument('--issue-range', help='Issue id range, e.g. 100-110 (inclusive)')
    parser.add_argument('--issue-dir', help='Directory containing issue folders (each with after.md)')
    parser.add_argument('--dry-run', action='store_true', help='Do not write files or call external CLIs; print intended actions')
    parser.add_argument('--print-copilot', action='store_true')
    parser.add_argument('--agent-ref', default='agent://copilot')
    parser.add_argument('--rebuild-with-copilot', action='store_true', help='Run the Copilot CLI to rebuild the issue using the Copilot agent')
    parser.add_argument('--prompt-file', help='Path to prompt file for Copilot CLI (used with --rebuild-with-copilot)')
    parser.add_argument('--update-github', action='store_true', help='Update issue body and labels in GitHub (requires GITHUB_TOKEN env var)')
    parser.add_argument('--owner', help='GitHub repo owner (required with --update-github)')
    parser.add_argument('--repo', help='GitHub repo name (required with --update-github)')
    args = parser.parse_args()

    domain_arg = args.domain

    # Determine list of after.md paths to process
    after_paths = []
    if args.issue or args.issues or args.issue_range:
        # Resolve issue_dir: use provided value or auto-detect common locations
        if args.issue_dir:
            issue_dir = Path(args.issue_dir)
        else:
            candidates = [
                Path.cwd() / '.story-work',
                Path.cwd().parent / 'durion-moqui-frontend' / '.story-work',
            ]
            issue_dir = next((c for c in candidates if c.exists() and c.is_dir()), None)
            if not issue_dir:
                print('Could not auto-detect issue directory; please provide --issue-dir', file=sys.stderr)
                sys.exit(10)
        issue_ids = []
        if args.issue:
            issue_ids.append(args.issue)
        if args.issues:
            issue_ids.extend(parse_issue_list_arg(args.issues))
        if args.issue_range:
            issue_ids.extend(parse_issue_range_arg(args.issue_range))
        # de-dup while preserving order
        seen = set()
        unique_ids = []
        for iid in issue_ids:
            if iid not in seen:
                seen.add(iid)
                unique_ids.append(iid)
        after_paths = resolve_issue_after_paths(unique_ids, issue_dir)
    elif args.after_md:
        after_paths = [Path(args.after_md)]
    else:
        print('Either provide <after_md> or use --issue/--issues/--issue-range with --issue-dir', file=sys.stderr)
        sys.exit(10)

    final_exit = 0
    for ap in after_paths:
        print('Processing:', ap)
        # Extract issue number if available
        iid = None
        try:
            iid = int(ap.parent.name)
        except (ValueError, AttributeError):
            pass
        code = process_one(ap, domain_arg, args, issue_number=iid)
        # choose the most severe exit code: 10 > 20 > 0
        if code == 10:
            final_exit = 10
        elif code == 20 and final_exit == 0:
            final_exit = 20

    sys.exit(final_exit)


if __name__ == '__main__':
    main()
