#!/usr/bin/env python3
import re
import sys
from pathlib import Path
import yaml
import json

REPO_ROOT = Path(__file__).resolve().parents[2]
POLICY = REPO_ROOT / '.github' / 'agents' / 'api-naming-policy.yaml'
SPEC = REPO_ROOT / 'scripts' / 'story-work' / 'backend' / 'ENDPOINT_API_SPEC.md'

def load_policy(path):
    return yaml.safe_load(path.read_text())

def parse_spec(path):
    text = path.read_text()
    entries = []
    # naive parser: split by '## ' domain headers
    parts = re.split(r"^##\s+", text, flags=re.M)
    for part in parts[1:]:
        lines = part.splitlines()
        domain = lines[0].strip()
        body = '\n'.join(lines[1:])
        # find blocks starting with '- Method:'
        blocks = re.split(r"\n- Method:\s+", body)
        for b in blocks[1:]:
            method_line, *rest = b.splitlines()
            method = method_line.strip().strip('`')
            block_text = '\n'.join(rest)
            path_m = re.search(r"Path:\s*`?([^`\n]+)`?", block_text)
            version_m = re.search(r"Version:\s*([^\n]+)", block_text)
            opid_m = re.search(r"operationId:\s*`?([^`\n]+)`?", block_text)
            deprecated_m = re.search(r"Deprecated:\s*true", block_text, flags=re.I)
            canonical_m = re.search(r"canonicalPath:\s*`?([^`\n]+)`?", block_text)
            entries.append({
                'domain': domain,
                'method': method,
                'path': path_m.group(1).strip() if path_m else None,
                'version': version_m.group(1).strip() if version_m else None,
                'operationId': opid_m.group(1).strip() if opid_m else None,
                'deprecated': bool(deprecated_m),
                'canonicalPath': canonical_m.group(1).strip() if canonical_m else None,
                'raw': block_text,
            })
    return entries

def check_rules(entries, policy):
    issues = []
    for e in entries:
        p = e['path'] or ''
        ver = e['version'] or ''
        # path-versioning: must start with /v{n}/ for canonical (non-legacy)
        if ver and ver.strip().lower() == 'legacy':
            if not e['deprecated']:
                issues.append((e, 'legacy-not-marked-deprecated', 'Legacy entry missing Deprecated: true'))
        else:
            # allow internal-versioned endpoints (internal services)
            if not (ver and ver.strip().lower() == 'internal'):
                if not re.match(r'^/v[0-9]+/', p):
                    issues.append((e, 'path-versioning', f'Canonical path should start with /v{{major}}/: {p}'))
        # no-api-prefix: warn if starts with /api/
        if p.startswith('/api/') and not e['deprecated']:
            issues.append((e, 'no-api-prefix', f'Path contains /api/: {p}'))
        # operationId format
        op = e.get('operationId') or ''
        if op and not re.match(r'^[a-z][A-Za-z0-9]+$', op):
            issues.append((e, 'operationId-format', f'operationId not camelCase verb-first: {op}'))
        # path format lowercase (ignore path parameter names inside braces)
        if p:
            path_no_params = re.sub(r"\{[^}]*\}", "{param}", p)
            if re.search(r'[A-Z]|_', path_no_params):
                issues.append((e, 'path-format', f'Path contains uppercase or underscore outside params: {p}'))
        # pluralization: check primary resource segment (first segment after /v{n})
        if p:
            m = re.match(r'^/v[0-9]+/([^/]+)', p)
            if m:
                primary = m.group(1)
                segs = [s for s in p.split('/') if s]
                # only enforce pluralization for shallow paths like /v1/{resource} or /v1/{resource}/{id}
                if len(segs) <= 2:
                    if not primary.endswith('s'):
                        issues.append((e, 'pluralization', f'Primary resource segment not plural: {p}'))
    return issues

def main():
    policy = load_policy(POLICY)
    entries = parse_spec(SPEC)
    issues = check_rules(entries, policy)
    out = {
        'summary': {
            'entries': len(entries),
            'issues': len(issues)
        },
        'issues': []
    }
    for e, code, msg in issues:
        out['issues'].append({
            'domain': e['domain'],
            'path': e['path'],
            'method': e['method'],
            'operationId': e.get('operationId'),
            'code': code,
            'message': msg
        })
    print(json.dumps(out, indent=2))

if __name__ == '__main__':
    main()
