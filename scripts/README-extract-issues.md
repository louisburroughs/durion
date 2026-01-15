# Extract Issues by Domain Script

## Overview

The `extract-issues-by-domain.zsh` script extracts all open GitHub issues from the `durion-positivity-backend` repository, groups them by their `domain:*` labels, and outputs one text file per domain.

## Features

- ✅ **GitHub Authentication Check**: Verifies `gh` CLI is authenticated before running
- ✅ **Domain-First Approach**: Fetches domain labels first, then queries issues by domain (very efficient)
- ✅ **No Comments**: Outputs only issue body content, not comments
- ✅ **Includes Labels**: Each issue includes all its labels
- ✅ **Text Format**: Outputs plain text files (not JSON)
- ✅ **Summary Report**: Creates a `DOMAINS_SUMMARY.txt` with issue counts per domain

## Prerequisites

1. **GitHub CLI (`gh`)**: Install from https://cli.github.com/
2. **Authentication**: Run `gh auth login` and authenticate with your GitHub account
3. **zsh shell**: The script is written for zsh (default on modern macOS and Linux)

## Usage

```bash
# Basic usage (outputs to current directory)
./extract-issues-by-domain.zsh

# Specify output directory
./extract-issues-by-domain.zsh /path/to/output

# Example
./extract-issues-by-domain.zsh ~/issue-exports
```

## Output Format

### Domain Files

Each domain gets its own file: `domain-<name>.txt`

Example `domain-accounting.txt`:
```
---
ISSUE #148: [BACKEND] [STORY] Accounting: Create Invoice from Order
LABELS: type:story,domain:accounting,status:ready-for-dev
BODY:
# Story: Create Invoice from Order
...

---
ISSUE #146: [BACKEND] [STORY] Accounting: Post GL Entries
LABELS: type:story,domain:accounting,agent:accounting
BODY:
# Story: Post GL Entries
...
```

### Summary File

`DOMAINS_SUMMARY.txt` contains:
```
Domain Issue Summary
====================
Repository: louisburroughs/durion-positivity-backend
Generated: Thu Jan 15 03:35:14 PM EST 2026

domain:accounting      149 issues
domain:audit            10 issues
domain:billing         111 issues
...

Total: 760 issues across 14 domains
```

## Performance

- Processes all 14 domains with ~160 issues each in under 60 seconds
- Efficient API usage (one query per domain instead of per issue)
- No rate limiting issues with GitHub API

## Customization

### Change Repository

Set the `REPO` environment variable:

```bash
REPO="your-org/your-repo" ./extract-issues-by-domain.zsh /output
```

### Modify Issue Limit

By default, fetches up to 1000 issues per domain. To change:

Edit line 49:
```bash
--limit 1000 \
```

## Troubleshooting

### "ERROR: GitHub CLI not authenticated"

Run `gh auth login` and follow the prompts to authenticate.

### "No domain labels found"

Verify your repository has labels starting with `domain:` (e.g., `domain:accounting`, `domain:inventory`).

### Empty output files

Check that you have open issues with the corresponding domain labels.

## Example Run

```bash
$ ./extract-issues-by-domain.zsh /tmp/issues
✓ Authenticated to GitHub
Fetching domain labels from louisburroughs/durion-positivity-backend...
Found 14 domain labels

[1/14] Processing domain:accounting...
  ✓ Wrote 149 issues to /tmp/issues/domain-accounting.txt
[2/14] Processing domain:audit...
  ✓ Wrote 10 issues to /tmp/issues/domain-audit.txt
...
[14/14] Processing domain:workexec...
  ✓ Wrote 159 issues to /tmp/issues/domain-workexec.txt

✓ Complete! Files created in: /tmp/issues
  Summary: /tmp/issues/DOMAINS_SUMMARY.txt
```
