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
import logging
from datetime import datetime

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


def extract_domain_from_required_section(body: str):
    """Extract domain from 'domain:*' label in ### Required section of markdown."""
    # Look for ### Required section and extract domain from it
    match = re.search(r'###\s+Required\s*\n(.*?)(?=\n###|\n##\s|\Z)', body, re.DOTALL)
    if match:
        required_section = match.group(1)
        # Look for domain: label in the required section
        domain_match = re.search(r'-\s*domain:(\S+)', required_section)
        if domain_match:
            return domain_match.group(1).strip()
    return None


def _strip_leading_stop(text: str) -> str:
    # Remove a single leading STOP line + following blank line.
    lines = (text or "").splitlines()
    if not lines:
        return text
    if lines[0].startswith("STOP:"):
        lines = lines[1:]
        if lines and lines[0].strip() == "":
            lines = lines[1:]
        return "\n".join(lines).lstrip("\n")
    return text


def _has_resolution_comments(comments: list) -> bool:
    # Heuristic: any comment indicating a decision/resolution.
    for c in comments or []:
        body = (c or {}).get("body") or ""
        if re.search(r"\b(decision|resolved|resolution|approved|confirmed)\b", body, re.IGNORECASE):
            return True
    return False


def _extract_resolution_snippets(comments: list, max_lines: int = 18) -> list:
    snippets = []
    for c in comments or []:
        body = (c or {}).get("body") or ""
        if not re.search(r"\b(decision|resolved|resolution|approved|confirmed)\b", body, re.IGNORECASE):
            continue
        keep = []
        for line in body.splitlines():
            s = line.strip()
            if not s:
                continue
            if (
                re.search(r"\bdecision\b", s, re.IGNORECASE)
                or s.startswith(("- ", "* ", "###", "## ", "# ", "✅"))
            ):
                keep.append(s)
            if len(keep) >= max_lines:
                break
        if keep:
            snippets.append((c.get("html_url") or c.get("url") or "", c.get("user", {}).get("login") or "", keep))
    return snippets


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

        # Resolution Comment Override: clear STOP when authoritative resolution comments exist.
        try:
            current_issue = client.get_issue(owner, repo, issue_number)
            current_body = (current_issue or {}).get('body') or ''
            comments = client.get_issue_comments(owner, repo, issue_number)
        except Exception:
            current_body = ''
            comments = []

        has_stop = bool(body.splitlines() and body.splitlines()[0].startswith('STOP:'))
        resolution_comments = _has_resolution_comments(comments)

        stop_cleared = False
        if has_stop and resolution_comments:
            body = _strip_leading_stop(body)
            stop_cleared = True

            # Update labels to reflect resolution
            if 'blocked:clarification' in blocking:
                blocking = [l for l in blocking if l != 'blocked:clarification']
            # Promote status away from draft when STOP cleared
            required = [l for l in required if not l.startswith('status:')]
            required.append('status:ready-for-review')

            # Apply resolution snippets into body (non-destructive; appended)
            snippets = _extract_resolution_snippets(comments)
            if snippets:
                body += "\n\n---\n\n## Resolution Notes (from issue comments)\n"
                for url, author, lines in snippets:
                    cite = f"[{author}]({url})" if (author and url) else (author or url or "comment")
                    body += f"\n- Source: {cite}\n"
                    for line in lines:
                        body += f"  - {line}\n"

        # Update issue body
        client.update_issue_body(owner, repo, issue_number, body)
        print(f'Updated body for GitHub issue #{issue_number}')

        # Apply labels
        try:
            apply_labels(client, owner, repo, issue_number, required, recommended, blocking, include_recommended=True)
            print(f'Applied labels to GitHub issue #{issue_number}')
        except Exception as e:
            print(f'Warning: failed to apply labels: {e}', file=sys.stderr)

        # Post closing comment confirming STOP was cleared
        if stop_cleared and resolution_comments:
            try:
                client.create_issue_comment(
                    owner,
                    repo,
                    issue_number,
                    "✅ Cleared leading `STOP:` from story body based on resolution/decision comment(s). "
                    "Updated labels accordingly; story is no longer blocked by STOP."
                )
                print(f'Posted STOP-cleared comment on GitHub issue #{issue_number}')
            except Exception as e:
                print(f'Warning: failed to post STOP-cleared comment: {e}', file=sys.stderr)

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


def process_one(after: Path, domain_arg: str, args, issue_number: int = None) -> int:
    # Set up logging
    log_file = after.parent / 'fix_issue_debug.log'
    logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s [%(levelname)s] %(message)s',
        handlers=[
            logging.FileHandler(log_file, mode='a'),
            logging.StreamHandler(sys.stderr)
        ]
    )
    logger = logging.getLogger(__name__)
    
    logger.info(f'=== Processing issue: {after} ===')
    logger.debug(f'Domain arg: {domain_arg}')
    logger.debug(f'Issue number: {issue_number}')
    logger.debug(f'Args: {args}')
    
    if not after.exists():
        logger.error(f'after.md not found: {after}')
        print('after.md not found:', after, file=sys.stderr)
        return 10

    text = after.read_text(encoding='utf-8')
    labels, body = parse_frontmatter_labels(text)
    if labels is None:
        labels = []
    
    # Determine domain: CLI arg takes precedence, then ### Required section, then fallback to labels
    domain = domain_arg
    if not domain:
        domain = extract_domain_from_required_section(text)
    if not domain:
        domain = extract_domain_from_labels(labels)
    if not domain:
        print(f'Domain not specified and no domain label found in {after}', file=sys.stderr)
        print('Either provide domain as CLI argument or add a "domain:*" label in the ### Required section of after.md', file=sys.stderr)
        return 10
    
    # Business rules will be resolved by the agent
    br_index = {}
    
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

    # Only write fixed.md if NOT rebuilding with copilot
    # (copilot will write it if rebuild is requested)
    if not args.rebuild_with_copilot:
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
        # Build prompt and print it
        prompt_path = Path(args.prompt_file).resolve() if args.prompt_file else None
        script_dir = Path(__file__).parent
        workspace_root = script_dir.parent.parent
        if prompt_path and prompt_path.exists():
            full_prompt = build_copilot_prompt(workspace_root, prompt_path, domain, body, after.stem)
            print("="*100)
            print("FULL COPILOT PROMPT")
            print("="*100)
            print(full_prompt)
        else:
            print("Error: Prompt file not found", file=sys.stderr)

    if args.rebuild_with_copilot:
        # Read prompt from file or use default
        prompt_path = Path(args.prompt_file).resolve() if args.prompt_file else None
        # Construct workspace root
        script_dir = Path(__file__).parent
        workspace_root = script_dir.parent.parent  # ../.. from fix_frontend_issues/
        
        # Use agent common name instead of file path (e.g., "Story Authoring Agent")
        agent_name = args.agent_ref if args.agent_ref else "story-authoring"
        
        # Build the full prompt with business rules
        if not prompt_path or not prompt_path.exists():
            logger.error(f'Prompt file not found or not specified: {prompt_path}')
            print('Error: Prompt file required for --rebuild-with-copilot', file=sys.stderr)
            return 10
        
        output_file = 'fixed.md'
        full_prompt_text = build_copilot_prompt(workspace_root, prompt_path, domain, body, after.stem, output_file)

        # Write prompt to a temp file to avoid command-line length limits
        import tempfile
        prompt_temp_file = after.parent / '.copilot_prompt_temp.txt'
        prompt_temp_file.write_text(full_prompt_text, encoding='utf-8')
        prompt_arg = f"@{prompt_temp_file}"
        
        # Set working directory to issue directory so copilot writes there
        issue_dir = after.parent

        def call_copilot_cli(prompt_file_ref: str, agent_name: str, model: str, work_dir: Path) -> bool:
            logger = logging.getLogger(__name__)
            copilot_exe = shutil.which('copilot')
            if not copilot_exe:
                logger.error('copilot CLI not found in PATH')
                print('copilot CLI not found in PATH; skipping copilot run', file=sys.stderr)
                return False

            # Build command for agent run with agent common name
            # Use --log-level all to capture thinking process
            # Restrict to only file operations and safe read-only shell commands
            # Set working directory to issue directory so output files go there
            cmd = [
                copilot_exe,
                '--prompt', prompt_file_ref,
                '--agent', agent_name,
                '--model', model,
                '--add-dir', str(work_dir),  # Issue directory where output should be written
                '--add-dir', str(workspace_root),  # Workspace root for reading business rules
                '--available-tools', 'read_file', 'create_file', 'replace_string_in_file', 
                'multi_replace_string_in_file', 'list_dir', 'grep_search', 'file_search', 'semantic_search',
                # Allow safe read-only shell commands
                '--allow-tool', 'shell(grep:*)',
                '--allow-tool', 'shell(find:*)',
                '--allow-tool', 'shell(cat:*)',
                '--allow-tool', 'shell(head:*)',
                '--allow-tool', 'shell(tail:*)',
                '--allow-tool', 'shell(wc:*)',
                '--allow-tool', 'shell(ls:*)',
                '--allow-tool', 'shell(pwd:*)',
                '--allow-tool', 'shell(git log:*)',
                '--allow-tool', 'shell(git show:*)',
                '--allow-tool', 'shell(git diff:*)',
                '--allow-tool', 'shell(git status:*)',
                '--log-level', 'all',
            ]
            
            logger.info(f'Copilot executable: {copilot_exe}')
            logger.info(f'Agent name: {agent_name}')
            logger.info(f'Model: {model}')
            logger.info(f'Working directory: {work_dir}')
            logger.info(f'Workspace root: {workspace_root}')
            logger.info(f'Prompt file: {prompt_file_ref}')
            logger.info(f'Expected output file: {work_dir / output_file}')
            logger.info(f'Full command: {" ".join(cmd)}')
            logger.debug(f'Prompt length: {len(full_prompt_text)} characters')
            
            # Create a thinking log file
            thinking_log = after.parent / 'copilot_thinking.log'
            
            try:
                logger.info('Executing copilot CLI with thinking output...')
                proc = subprocess.run(cmd, text=True, capture_output=True, timeout=1200)
                logger.info(f'Copilot CLI exit code: {proc.returncode}')
                
                print('Copilot CLI exit code:', proc.returncode)
                
                # Process and log stdout (contains thinking + response)
                if proc.stdout:
                    # Write full stdout to thinking log
                    thinking_log.write_text(proc.stdout, encoding='utf-8')
                    logger.info(f'Full output (including thinking) written to: {thinking_log}')
                    print(f'Full output written to: {thinking_log}')
                    
                    # Parse thinking sections for the main log
                    thinking_sections = []
                    response_sections = []
                    current_section = None
                    
                    for line in proc.stdout.split('\n'):
                        if '<thinking>' in line.lower() or line.strip().startswith('Thinking:'):
                            current_section = 'thinking'
                        elif '</thinking>' in line.lower():
                            current_section = None
                        elif current_section == 'thinking':
                            thinking_sections.append(line)
                        else:
                            response_sections.append(line)
                    
                    # Log thinking separately
                    if thinking_sections:
                        thinking_text = '\n'.join(thinking_sections)
                        logger.info(f'=== COPILOT THINKING PROCESS ===\n{thinking_text}\n=== END THINKING ===')
                        print('\n=== Copilot Thinking Process ===')
                        print(thinking_text)
                        print('=== End Thinking ===\n')
                    
                    # Log response
                    response_text = '\n'.join(response_sections)
                    logger.info(f'Copilot response:\n{response_text}')
                    print('Copilot response:\n', response_text)
                
                if proc.stderr:
                    logger.warning(f'Copilot stderr:\n{proc.stderr}')
                    print('Copilot stderr:\n', proc.stderr, file=sys.stderr)
                    
                logger.info(f'Copilot CLI completed with exit code {proc.returncode}')
                
                # Check for output file and handle alternate locations
                expected_output = work_dir / output_file
                alternate_locations = [
                    work_dir / 'FINAL_STORY.md',
                    workspace_root / '.story-work' / work_dir.name / 'FINAL_STORY.md',
                ]
                
                if expected_output.exists():
                    logger.info(f'Output file found at expected location: {expected_output}')
                    print(f'Output written to: {expected_output}')
                else:
                    # Check alternate locations
                    for alt_path in alternate_locations:
                        if alt_path.exists():
                            logger.info(f'Output found at alternate location: {alt_path}, copying to {expected_output}')
                            print(f'Output found at {alt_path}, copying to {expected_output}')
                            try:
                                shutil.copy2(alt_path, expected_output)
                                logger.info(f'Successfully copied output to {expected_output}')
                                break
                            except Exception as e:
                                logger.error(f'Failed to copy output file: {e}')
                    else:
                        logger.warning(f'Output file not found at expected location or alternates')
                        print(f'Warning: Output file not found. Expected: {expected_output}', file=sys.stderr)
                
                # Clean up temp file
                try:
                    prompt_temp_file.unlink()
                except Exception:
                    pass
                    
                return proc.returncode == 0
            except subprocess.TimeoutExpired as e:
                logger.error(f'Copilot CLI timed out after 1200 seconds: {e}')
                print('Error: copilot CLI timed out after 1200 seconds', file=sys.stderr)
                return False
            except Exception as e:
                logger.error(f'Error running copilot CLI: {e}', exc_info=True)
                print('Error running copilot CLI:', e, file=sys.stderr)
                return False

        if args.dry_run:
            logger.info('DRY RUN: would run copilot CLI')
            logger.debug(f'DRY RUN prompt length: {len(full_prompt_text)} characters')
            logger.debug(f'DRY RUN agent name: {agent_name}')
            logger.debug(f'DRY RUN model: {args.model}')
            logger.debug(f'DRY RUN prompt file: {prompt_temp_file}')
            print('DRY RUN: would run copilot CLI')
            print(f'DRY RUN: Prompt written to {prompt_temp_file} ({len(full_prompt_text)} chars)')
            print('DRY RUN: would use agent:', agent_name)
            print('DRY RUN: would use model:', args.model)
            print('\nDRY RUN: First 500 chars of prompt:')
            print(full_prompt_text[:500])
            print('\n... [truncated] ...')
        else:
            logger.info(f'Calling copilot CLI with prompt file: {prompt_arg} and model: {args.model}')
            copilot_success = call_copilot_cli(prompt_arg, agent_name, args.model, issue_dir)
            
            if not copilot_success:
                logger.error('Copilot CLI failed or did not complete successfully')
                print('ERROR: Copilot CLI failed. No fixed.md written.', file=sys.stderr)
                return 10
            
            # Verify output file exists after copilot completes
            fixed_path = after.parent / 'fixed.md'
            if not fixed_path.exists():
                logger.error(f'Copilot completed but output file not found: {fixed_path}')
                print(f'ERROR: Copilot completed but output file not found: {fixed_path}', file=sys.stderr)
                return 10
            
            print('WROTE', fixed_path, '(via copilot)')

    return exit_code


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


def load_text_file(path: Path) -> str:
    """Load text file content."""
    return path.read_text(encoding='utf-8')


def _load_business_rules_files(root_repo: Path, domain: str) -> list:
    """Load business rules files for a domain: AGENT_GUIDE.md, DOMAIN_NOTES.md, STORY_VALIDATION_CHECKLIST.md"""
    br_dir = root_repo / 'domains' / domain / '.business-rules'
    if not br_dir.exists() or not br_dir.is_dir():
        print(f'Warning: Business rules dir not found: {br_dir}', file=sys.stderr)
        return []
    
    # Priority order: AGENT_GUIDE.md, DOMAIN_NOTES.md, STORY_VALIDATION_CHECKLIST.md, then any other .md files
    priority_files = ['AGENT_GUIDE.md', 'DOMAIN_NOTES.md', 'STORY_VALIDATION_CHECKLIST.md']
    files = []
    
    for fname in priority_files:
        fpath = br_dir / fname
        if fpath.exists():
            files.append(fpath)
    
    # Add any other .md files not in priority list
    for fpath in sorted(br_dir.glob('*.md')):
        if fpath.name not in priority_files and fpath not in files:
            files.append(fpath)
    
    return files


def build_copilot_prompt(root_repo: Path, prompt_file: Path, domain: str, issue_body: str, issue_title: str = None, output_file: str = 'fixed.md') -> str:
    """Build a comprehensive prompt string matching story_update.py structure.
    
    Args:
        root_repo: Path to workspace root (durion/)
        prompt_file: Path to the main prompt instructions file
        domain: Domain name (e.g., 'inventory', 'order')
        issue_body: The full body of the issue to be updated
        issue_title: Optional issue title
        output_file: Name of output file (default: 'fixed.md')
    
    Returns:
        Formatted prompt string with all sections clearly marked
    """
    parts = []
    
    # 1. Main Prompt Instructions
    parts.append("="*100)
    parts.append("MAIN PROMPT INSTRUCTIONS")
    parts.append("="*100)
    if prompt_file.exists():
        parts.append(load_text_file(prompt_file))
    else:
        parts.append("Review and rewrite the issue body resolving open questions using the canonical business rules.")
    parts.append("")
    
    # 2. Business Rules for Domain
    br_files = _load_business_rules_files(root_repo, domain)
    if br_files:
        parts.append("="*100)
        parts.append(f"BUSINESS RULES FOR DOMAIN: {domain}")
        parts.append("="*100)
        for br_file in br_files:
            parts.append(f"\n--- {br_file.name} ---")
            try:
                parts.append(load_text_file(br_file))
            except Exception as e:
                parts.append(f"[Error loading {br_file.name}: {e}]")
        parts.append("")
    
    # 3. Issue Content
    parts.append("="*100)
    parts.append("ISSUE TO BE UPDATED")
    parts.append("="*100)
    if issue_title:
        parts.append(f"Title: {issue_title}")
        parts.append("")
    parts.append(issue_body)
    parts.append("="*100)
    parts.append("")
    
    # 4. Final Instructions
    parts.append("-"*100)
    parts.append("TASK:")
    parts.append("-"*100)
    parts.append(
        f"Review and update the included issue and only this issue for the '{domain}' domain. Resolve any open questions in this issue using "
        "the business rules provided. Produce an implementation-ready rewrite following all guidance. "
        "Ensure output contains required section headers and labels as specified in the prompt instructions."
    )
    parts.append("")
    parts.append("-"*100)
    parts.append("OUTPUT REQUIREMENTS (CRITICAL):")
    parts.append("-"*100)
    parts.append("DO NOT create Durion-Processing.md or follow the thought logging workflow.")
    parts.append("DO NOT create any planning or tracking files.")
    parts.append("")
    parts.append("Use create_file tool to write the output.")
    parts.append("")
    parts.append(f"YOU MUST:")
    parts.append(f"1. Use read_file tool to read: after.md")
    parts.append(f"2. Process and update the issue according to the instructions above")
    parts.append(f"3. Use create_file tool to write ONLY the final updated story content to: {output_file}")
    parts.append("")
    parts.append(f"The output file {output_file} should contain:")
    parts.append("- The complete, updated issue body with all sections")
    parts.append("- Resolved open questions (using business rules)")
    parts.append("- Proper labels and structure as specified in the prompt")
    parts.append("")
    parts.append("WRITE THE FILE NOW. Do not defer to another workflow.")
    parts.append("")
    
    return "\n".join(parts)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('domain', nargs='?', default=None, help='Domain name (optional if domain label exists in after.md)')
    parser.add_argument('after_md', nargs='?', default=None, help='path to after.md')
    parser.add_argument('--issue', help='Single issue id to process')
    parser.add_argument('--issues', help='Comma-separated list of issue ids to process')
    parser.add_argument('--issue-range', help='Issue id range, e.g. 100-110 (inclusive)')
    parser.add_argument('--issue-dir', help='Directory containing issue folders (each with after.md)')
    parser.add_argument('--dry-run', action='store_true', help='Do not write files or call external CLIs; print intended actions')
    parser.add_argument('--print-copilot', action='store_true')
    parser.add_argument('--agent-ref', default='agent://copilot', help='Agent reference for Copilot')
    parser.add_argument('--rebuild-with-copilot', action='store_true', help='Run the Copilot CLI to rebuild the issue using the Copilot agent')
    parser.add_argument('--prompt-file', help='Path to prompt file for Copilot CLI (used with --rebuild-with-copilot)')
    parser.add_argument('--model', default='gpt-5.2', help='Model to use for Copilot CLI (default: gpt-5.2)')
    parser.add_argument('--update-github', action='store_true', help='Update issue body and labels in GitHub (requires GITHUB_TOKEN env var)')
    parser.add_argument('--owner', help='GitHub repo owner (required with --update-github)')
    parser.add_argument('--repo', help='GitHub repo name (required with --update-github)')
    args = parser.parse_args()

    # When using --issue/--issues/--issue-range, domain and after_md should not be positional
    # If they were provided as positional, warn the user
    if (args.issue or args.issues or args.issue_range) and args.domain:
        # domain was provided as positional but we're in issue-mode
        # In this case, domain_arg should be None (extract from issue metadata)
        # and warn if positional args were used
        if args.after_md:
            print('Warning: positional arguments ignored when using --issue/--issues/--issue-range', file=sys.stderr)
        domain_arg = None
    else:
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
