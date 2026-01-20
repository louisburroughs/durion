#!/usr/bin/env python3
"""
fix_issue.py

Usage:
  python3 fix_issue.py <domain> <path/to/after.md>

Reads business rules from `durion/domains/{domain}/.business-rules/`, reads the issue file `after.md`,
attempts to resolve open questions using the business rules, and writes `fixed.md` next to `after.md`.

This script also prints a Copilot prompt JSON to stdout when asked via --print-copilot, but it does not call any network service.

Exit codes: 0 OK, 10 validation error (missing files), 20 unresolved questions remain (but fixed.md still written).
"""
import sys
import re
from pathlib import Path
import json
import argparse


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


def write_fixed(path_after: Path, labels_out, body_out, label_changes):
    fixed_path = path_after.parent / 'fixed.md'
    fm_lines = ['---', 'labels:']
    for l in labels_out:
        fm_lines.append(f'  - {l}')
    fm_lines.append('---')
    content = '\n'.join(fm_lines) + '\n\n' + body_out + '\n\n' + '---\nLabels changed:\n'
    for change in label_changes:
        content += f"- {change}\n"
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
    parser.add_argument('domain')
    parser.add_argument('after_md', help='path to after.md')
    parser.add_argument('--print-copilot', action='store_true')
    parser.add_argument('--agent-ref', default='agent://copilot')
    args = parser.parse_args()

    domain = args.domain
    after = Path(args.after_md)
    if not after.exists():
        print('after.md not found', file=sys.stderr)
        sys.exit(10)

    # prefer workspace-relative domains directory from current working directory
    domain_dir = Path.cwd() / 'domains' / domain
    br_index = read_business_rules(domain_dir)
    if not br_index:
        print('Business rules missing or empty for domain', domain, file=sys.stderr)

    text = after.read_text(encoding='utf-8')
    labels, body = parse_frontmatter_labels(text)
    if labels is None:
        labels = []
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

    fixed_path = write_fixed(after, labels_out, new_body, label_changes)
    print('WROTE', fixed_path)

    if args.print_copilot:
        # provide a small business_rules_snippet for copilot
        snippets = {k: v for k, v in list(br_index.items())[:10]}
        payload = build_copilot_prompt(after.stem, body, snippets, domain, args.agent_ref)
        print(json.dumps(payload, indent=2))

    sys.exit(exit_code)


if __name__ == '__main__':
    main()
