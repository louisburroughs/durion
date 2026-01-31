## Capability Label Synchronization Scripts

Two scripts for managing capability labels across the Durion repository ecosystem.

### Quick Start

#### Hierarchical Script (Recommended)
Applies `CAP:{###}` labels to capabilities and **all descendants** (children, grandchildren, etc.):

```bash
# Dry run (preview changes)
GITHUB_TOKEN=ghp_xxxxx python3 sync_capability_labels_hierarchical.py --dry-run

# Apply changes
GITHUB_TOKEN=ghp_xxxxx python3 sync_capability_labels_hierarchical.py
```

#### Simple Script
Applies `CAP:{###}` labels to capability issues only:

```bash
GITHUB_TOKEN=ghp_xxxxx python3 sync_capability_labels.py --dry-run
GITHUB_TOKEN=ghp_xxxxx python3 sync_capability_labels.py
```

---

## Hierarchical Script: `sync_capability_labels_hierarchical.py`

### What It Does

For each issue labeled `type:capability` in durion:

1. **Create label** `CAP:{###}` (color `#0052cc`) with description from issue title
2. **Apply label** to the capability issue
3. **Find child issues** by parsing issue body and comments for issue references
4. **Apply label recursively** to all descendants in the hierarchy

### Issue Discovery

Child issues are identified by:
- `#123` references in issue body or comments (relative to current repo)
- `owner/repo#123` format (cross-repo references)
- `github.com/owner/repo/issues/123` URLs in issue body

### Repository Scope

Labels are created in and applied to issues in:
- `durion` (coordination, capabilities, parent stories, child stories)
- `durion-positivity-backend` (backend child issues)
- `durion-moqui-frontend` (frontend child issues)

### Usage

```bash
# Preview all changes (safe to run anytime)
python3 sync_capability_labels_hierarchical.py --dry-run

# Apply labels to capability and all descendants
python3 sync_capability_labels_hierarchical.py

# With explicit token
python3 sync_capability_labels_hierarchical.py --token ghp_xxxxx
```

### Output Example

```
================================================================================
CAPABILITY LABEL SYNCHRONIZATION
================================================================================
Found 2 capability issues
Mode: DRY RUN (no changes will be made)
================================================================================

CAPABILITY: CAP:001 — Order Capture Capability
────────────────────────────────────────────────────────────────────────────────
Label creation across repositories:
  → durion                           CREATE
  → durion-positivity-backend        CREATE
  → durion-moqui-frontend            CREATE

Issues that will be labeled with CAP:001:
  durion#1 (Order Capture Capability...)        → LABEL
    durion#45 (Backend: Order Capture...)       → LABEL
      durion-positivity-backend#123 (Implement...) → LABEL
      durion-positivity-backend#124 (Add...)    → LABEL
    durion#46 (Frontend: Order Form...)         → LABEL
      durion-moqui-frontend#789 (Build order...) → LABEL


CAPABILITY: CAP:002 — Invoice Processing Capability
────────────────────────────────────────────────────────────────────────────────
Label creation across repositories:
  → durion                           CREATE
  → durion-positivity-backend        CREATE
  → durion-moqui-frontend            CREATE

Issues that will be labeled with CAP:002:
  durion#2 (Invoice Processing...)            → LABEL
    durion-positivity-backend#234 (Create...) → LABEL
    durion-moqui-frontend#890 (Invoice UI...) → LABEL


================================================================================
SUMMARY
================================================================================
Capabilities to process: 2
Labels to create: 6
Issues to label: 10

✓ Dry run complete. Run without --dry-run to apply these changes.
================================================================================
```

### Environment Variables

- `GITHUB_TOKEN` — GitHub personal access token with `repo` scope (required)

### Requirements

```bash
pip install PyGithub
```

---

## Simple Script: `sync_capability_labels.py`

### What It Does

For each issue labeled `type:capability` in durion:

1. **Create label** `CAP:{###}` (color `#0052cc`) with description from issue title
2. **Apply label** to the capability issue only

This script does **not** traverse children; use for flat labeling of capability issues only.

### Usage

```bash
python3 sync_capability_labels.py --dry-run
python3 sync_capability_labels.py --token ghp_xxxxx
```

---

## GitHub Token Setup

### Create a Token

1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Select scope: `repo` (full control of private repositories)
4. Copy the token

### Use the Token

**Option 1: Environment Variable (Recommended)**
```bash
export GITHUB_TOKEN=ghp_xxxxx
python3 sync_capability_labels_hierarchical.py
```

**Option 2: Command-line Argument**
```bash
python3 sync_capability_labels_hierarchical.py --token ghp_xxxxx
```

---

## Troubleshooting

### "GitHub token not provided"
Set `GITHUB_TOKEN` environment variable or pass `--token ghp_xxxxx`

### "PyGithub not installed"
Install with: `pip install PyGithub`

### "Could not access repo"
- Verify token has `repo` scope
- Confirm repo owner is `louisburroughs`
- Check token is not expired

### Label not applied to expected issues
- Ensure issue references use correct format: `#123`, `owner/repo#123`, or URL
- Check that referenced issues exist in target repositories
- Run with `--dry-run` to preview discovery

---

## Integration with Capability Execution Workflow

Once labels are synchronized:

1. **Capability kickoff** — Use `CAP:{###}` labels to filter issues across repos
2. **Contract PR** — Backend child issue must reference parent and link to durion contract PR
3. **Implementation** — Backend/frontend PRs inherit parent capability label
4. **Completion** — Parent STORY checklist validates all child PRs merged

Example issue reference in PR body:
```markdown
Parent capability: #1
Backend child: durion-positivity-backend#123
Frontend child: durion-moqui-frontend#456
Contract PR: durion#2
```

---

## Related Documentation

- `durion/scratchpad.md` — Capability execution workflow overview
- `durion/docs/capabilities/` — Capability manifests and planning
- `.github/PULL_REQUEST_TEMPLATE.md` — PR template with required fields

