# WorkExec Issue Label Update - Execution Guide

This guide explains how to use the label update scripts to organize all 28 WorkExec issues.

## Overview

After Phase 4 analysis, we need to:
1. **Update all 28 issues** with new labels
2. **Reassign 9 issues** to their correct domains (people, shopmgmt, order)
3. **Mark all issues** as ready for review

## Files Created

- `scripts/update-workexec-issue-labels.py` – Python script (recommended)
- `scripts/update-workexec-issue-labels.sh` – Bash script alternative
- `scripts/README-label-update.md` – Detailed documentation

## Quick Start

### Step 1: Get a GitHub Token

1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Select scope: `repo` (full control)
4. Copy the token

### Step 2: Run the Script (Python - Recommended)

**Dry Run (Preview Changes):**
```bash
cd /home/louisb/Projects/durion
GITHUB_TOKEN=ghp_xxxxx python3 scripts/update-workexec-issue-labels.py --dry-run
```

**Apply Changes:**
```bash
cd /home/louisb/Projects/durion
GITHUB_TOKEN=ghp_xxxxx python3 scripts/update-workexec-issue-labels.py
```

### Alternative: Use Bash Script

```bash
cd /home/louisb/Projects/durion
./scripts/update-workexec-issue-labels.sh
```

(Requires `gh` CLI installed and authenticated)

## What Gets Updated

### All 28 Issues
**Remove labels:**
- `blocked:clarification`
- `status:draft`

**Add label:**
- `status:needs-review`

### 9 Issues Moving to New Domains

**Remove `domain:workexec` and add new domain:**

| Domain | Issues | Count |
|--------|--------|-------|
| `domain:people` | #149, #146, #145, #132, #131 | 5 |
| `domain:shopmgmt` | #138, #137, #133, #127 | 4 |
| `domain:order` | #85 | 1 |

### 8 Issues Staying in domain:workexec

Keep `domain:workexec` label:
- #222, #216, #162 (Tier 1 Critical)
- #219, #157, #79 (Tier 2 High)
- #134, #129 (Tier 4 Low)

## Expected Output

### Dry Run Output
```
============================================================
WorkExec Issue Label Update
============================================================
Repository: louisburroughs/durion-moqui-frontend
Dry run: True

Processing 28 issues...

--- domain:workexec (8 issues) ---
✓ Issue #222:
  Current labels: domain:workexec, blocked:clarification, status:draft
  Remove: blocked:clarification, status:draft
  Add: status:needs-review
  Final labels: domain:workexec, status:needs-review

✓ Issue #216:
  ...

--- domain:people (5 issues) ---
✓ Issue #149:
  Current labels: domain:workexec, blocked:clarification, status:draft
  Remove: blocked:clarification, status:draft, domain:workexec
  Add: status:needs-review, domain:people
  Final labels: domain:people, status:needs-review

...

============================================================
Summary
============================================================
Total issues processed: 28
Successful: 28
Failed: 0

✓ Dry run complete. No changes were made.

Changes made:
  Removed: blocked:clarification, status:draft (all issues)
  Added: status:needs-review (all issues)

Domain assignments:
  domain:order: #85
  domain:people: #131, #132, #145, #146, #149
  domain:shopmgmt: #127, #133, #137, #138
  domain:workexec: #79, #129, #134, #157, #162, #216, #219, #222

Note: Issue #133 is marked as duplicate of #137
```

## Verification After Running

1. **Check label summary:**
   ```
   https://github.com/louisburroughs/durion-moqui-frontend/issues?q=is:issue+label:status:needs-review
   ```

2. **Verify domain labels:**
   - domain:workexec: 8 issues
   - domain:people: 5 issues
   - domain:shopmgmt: 4 issues
   - domain:order: 1 issue

3. **Confirm removed labels:**
   - No issues should have `blocked:clarification`
   - No issues should have `status:draft`

## Script Comparison

| Feature | Python | Bash |
|---------|--------|------|
| Dependencies | PyGithub | gh CLI |
| Installation | `pip install PyGithub` | May already be installed |
| Dry run support | ✅ Yes | ❌ No |
| Error handling | ✅ Excellent | ⚠️ Basic |
| Setup time | 2 minutes | 1 minute |
| Reliability | ✅ High | ⚠️ Medium |
| **Recommended** | **✅ YES** | No |

## Troubleshooting

### "PyGithub not installed"
```bash
pip install PyGithub
```

### "GitHub token not valid"
- Token may have expired
- Token may not have `repo` scope
- Create a new token from https://github.com/settings/tokens

### "Could not access repository"
- Verify `GITHUB_TOKEN` is set: `echo $GITHUB_TOKEN`
- Verify token is valid: paste it in a test request
- Check repo owner/name in script (should be `louisburroughs/durion-moqui-frontend`)

### Some labels won't update
- Labels are auto-created on first use
- Script will log which labels failed
- Retry after a few seconds

## Next Steps After Label Update

1. **GitHub Issue Triage:**
   - Review domain assignments in GitHub
   - Consolidate duplicate #133 into #137
   - Update issue assignments to domain teams

2. **Backend Planning:**
   - Backend teams start sprint planning using Phase 4 acceptance criteria
   - Reference `domains/workexec/workexec-questions.md` for detailed requirements
   - Reference `BACKEND_CONTRACT_GUIDE.md` for API contracts

3. **Frontend Planning:**
   - Frontend team reviews UI requirements in acceptance criteria
   - Create component specifications
   - Plan state management (Composition API, stores)

## Documentation Reference

- **Phase 4 Summary:** `PHASE_4_COMPLETION_SUMMARY.md`
- **Domain Ownership ADR:** `docs/adr/DECISION-WORKEXEC-001-domain-ownership-boundaries.md`
- **Backend Contracts:** `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- **Integration Contracts:** `domains/workexec/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- **Acceptance Criteria:** `domains/workexec/workexec-questions.md`

