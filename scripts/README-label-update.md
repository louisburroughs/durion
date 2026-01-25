# WorkExec Issue Label Update Scripts

Two scripts to update labels on all 28 WorkExec issues:

## Quick Start

### Python Script (Recommended)

```bash
# Dry run (preview changes)
GITHUB_TOKEN=ghp_xxxxx python3 update-workexec-issue-labels.py --dry-run

# Apply changes
GITHUB_TOKEN=ghp_xxxxx python3 update-workexec-issue-labels.py
```

### Bash Script (using GitHub CLI)

```bash
# Requires GitHub CLI (gh) installed and authenticated
./update-workexec-issue-labels.sh
```

## What These Scripts Do

### Label Changes (All 28 Issues)
- **Remove:** `blocked:clarification`, `status:draft`
- **Add:** `status:needs-review`

### Domain Reassignments (9 Issues)
- **To `domain:people` (5 issues):** #149, #146, #145, #132, #131 (timekeeping features)
- **To `domain:shopmgmt` (4 issues):** #138, #137, #133, #127 (scheduling/dispatch)
- **To `domain:order` (1 issue):** #85 (sales order creation)
- **Stay in `domain:workexec` (8 issues):** #222, #216, #162, #219, #157, #79, #134, #129

## Prerequisites

### Python Script
- Python 3.6+
- PyGithub: `pip install PyGithub`
- GitHub personal access token with `repo` scope

### Bash Script
- GitHub CLI: `gh auth login` (configure first)
- GitHub personal access token with `repo` scope

## GitHub Token Setup

### Create a Token
1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Select scopes: `repo` (full control of private repositories)
4. Copy the token

### Use the Token

**Option 1: Environment Variable**
```bash
export GITHUB_TOKEN=ghp_xxxxx
python3 update-workexec-issue-labels.py
```

**Option 2: Command-line Argument**
```bash
python3 update-workexec-issue-labels.py --token ghp_xxxxx
```

**Option 3: GitHub CLI**
```bash
gh auth login
# Follow prompts to authenticate
./update-workexec-issue-labels.sh
```

## Script Details

### Python Script (`update-workexec-issue-labels.py`)

**Advantages:**
- More reliable label operations
- Better error handling
- Clear dry-run output
- Works independently (no CLI tool required)

**Usage:**
```bash
python3 update-workexec-issue-labels.py --help

# Dry run
python3 update-workexec-issue-labels.py --dry-run

# Apply changes
python3 update-workexec-issue-labels.py
```

**Output Example (Dry Run):**
```
Issue #222:
  Current labels: domain:workexec, blocked:clarification, status:draft
  Remove: blocked:clarification, status:draft
  Add: status:needs-review
  Final labels: domain:workexec, status:needs-review
```

### Bash Script (`update-workexec-issue-labels.sh`)

**Advantages:**
- Uses GitHub CLI (may already be installed)
- Simple shell script
- No external Python dependencies

**Requirements:**
- `gh` CLI installed and configured
- GitHub authentication: `gh auth login`

**Usage:**
```bash
./update-workexec-issue-labels.sh
```

## Issue Assignments After Label Update

### domain:workexec (8 issues)
Core WorkExec features remaining in the domain

```
#222 - Parts Usage Recording (Tier 1)
#216 - Finalize Billable Work Order (Tier 1)
#162 - Require Customer PO# (Tier 1)
#219 - Role-Based Visibility (Tier 2)
#157 - Display CRM References (Tier 2)
#79 - Display Estimates with Filtering (Tier 2)
#134 - Assign Mechanic to Work Order (Tier 4)
#129 - Create Estimate from Appointment (Tier 4)
```

### domain:people (5 issues)
Timekeeping features (owned by People domain)

```
#149 - Clock In/Out at Shop Location
#146 - Start/Stop Timer for Task
#145 - Submit Job Time Entry
#132 - Track Work Session Start/End
#131 - Capture Mobile Travel Time
```

### domain:shopmgmt (4 issues)
Scheduling/dispatch features (owned by ShopMgmt domain)

```
#138 - View Assigned Schedule/Dispatch Board
#137 - Reschedule Work Order/Appointment
#133 - Override Schedule Conflict (DUPLICATE of #137)
#127 - Update Appointment Status from Events
```

### domain:order (1 issue)
Sales order creation (owned by Order domain)

```
#85 - Create Sales Order from Work Order Cart
```

## Verification

After running the script, verify the changes in GitHub:

1. Go to https://github.com/louisburroughs/durion-moqui-frontend/issues
2. Filter by `label:status:needs-review` to see all updated issues
3. Check individual issues for correct domain labels

## Troubleshooting

### "GitHub token not provided"
Set `GITHUB_TOKEN` environment variable or use `--token` argument

### "PyGithub not installed"
Install with: `pip install PyGithub`

### "Could not access repository"
- Verify token has `repo` scope
- Check repo owner and name in script
- Confirm token is not expired

### "Label does not exist"
Labels are auto-created on first use. If a label fails to apply:
- Create the label manually in GitHub
- Run the script again

## Related Documentation

- [PHASE_4_COMPLETION_SUMMARY.md](../PHASE_4_COMPLETION_SUMMARY.md) – Phase 4 deliverables and findings
- [DECISION-WORKEXEC-001.md](../docs/adr/DECISION-WORKEXEC-001-domain-ownership-boundaries.md) – Domain ownership rationale
- [workexec-questions.md](../domains/workexec/workexec-questions.md) – Acceptance criteria for all 28 issues

