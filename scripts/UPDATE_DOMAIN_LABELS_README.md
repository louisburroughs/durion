# Domain Issues Label Update Script

## Overview

The `update-domain-issue-labels.py` script automates label updates for all resolved domain issues from Phase 1-3 analysis. It handles 9 issues across 5 domains (Shop Management, Location, People, Security, Pricing).

## Issues Covered

### Shop Management (Phase 1-3 Complete)
- **#76** - Appointment: Create Appointment from Estimate or Order
  - **Remove:** `blocked:clarification`
  - **Add:** `analysis:complete`, `ready:backend-implementation`, `ready:frontend-implementation`

### Location (Phase 1 Complete)
- **#141** - Locations: Create Bays with Constraints and Capacity
- **#139** - Scheduling: Create Appointment with CRM Customer and Vehicle
  - **Remove:** `blocked:clarification`
  - **Add:** `analysis:in-progress`, `phase:contract-discovery`

### People (Phase 1-2 Complete)
- **#130** - Timekeeping: Approve Submitted Time for a Day/Workorder
  - **Remove:** `blocked:clarification`
  - **Add:** `analysis:in-progress`, `phase:data-contracts`

### Security (Phase 1-2 Complete)
- **#66** - Define POS Roles and Permission Matrix
- **#65** - Financial Exception Audit Trail (Query/Export)
  - **Remove:** `blocked:clarification`
  - **Add:** `analysis:in-progress`, `phase:data-contracts`

### Pricing (Phase 1-2 Complete)
- **#236** - Calculate Taxes and Totals on Estimate
- **#161** - Create Promotion Offer (Basic)
- **#84** - Apply Line Price Override with Permission and Reason
  - **Remove:** `blocked:clarification`
  - **Add:** `analysis:in-progress`, `phase:data-contracts`

## Installation

### Prerequisites
```bash
pip install PyGithub
```

### Setup
1. Generate a GitHub personal access token at https://github.com/settings/tokens with `repo` scope
2. Save token to environment variable or use `--token` argument

## Usage

### Basic Usage (All Domains)

**Dry run (preview changes):**
```bash
python3 update-domain-issue-labels.py --dry-run
```

**Apply changes:**
```bash
GITHUB_TOKEN=ghp_xxxxx python3 update-domain-issue-labels.py
```

### Filter by Domain

**Shop Management only:**
```bash
python3 update-domain-issue-labels.py --domain shopmgmt --dry-run
```

**Location domain:**
```bash
python3 update-domain-issue-labels.py --domain location --dry-run
```

**People domain:**
```bash
python3 update-domain-issue-labels.py --domain people --dry-run
```

**Security domain:**
```bash
python3 update-domain-issue-labels.py --domain security --dry-run
```

**Pricing domain:**
```bash
python3 update-domain-issue-labels.py --domain pricing --dry-run
```

### Command-Line Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--domain` | `all` | Filter by domain: `all`, `shopmgmt`, `location`, `people`, `security`, `pricing` |
| `--dry-run` | - | Show changes without applying (recommended first run) |
| `--token TOKEN` | - | GitHub API token (or use `GITHUB_TOKEN` env var) |
| `-h, --help` | - | Show help message |

## Environment Variables

| Variable | Description |
|----------|-------------|
| `GITHUB_TOKEN` | GitHub API token for authentication |

## Examples

### 1. Preview all changes (recommended first step)
```bash
export GITHUB_TOKEN=ghp_xxxxx
python3 update-domain-issue-labels.py --dry-run
```

### 2. Apply changes for shop management only
```bash
python3 update-domain-issue-labels.py --domain shopmgmt --token ghp_xxxxx
```

### 3. Apply all changes
```bash
GITHUB_TOKEN=ghp_xxxxx python3 update-domain-issue-labels.py
```

### 4. Check pricing domain changes in detail
```bash
python3 update-domain-issue-labels.py --domain pricing --dry-run
```

## Script Output

The script provides colored output showing:
- Repository and issue count
- Progress (✓ success, ✗ failure)
- Summary of changes
- Issues processed by domain
- Next steps for implementation

### Example Output
```
======================================================================
All Domains Issue Label Update Script
======================================================================
Repository: louisburroughs/durion-moqui-frontend
Issues to update: 9 (all domains)

DRY RUN MODE - No changes will be made

Processing issues:

  SHOPMGMT Domain:
    ✓ #76: remove 1, add 3

  LOCATION Domain:
    ✓ #141: remove 1, add 2
    ✓ #139: remove 1, add 2

  ...

======================================================================
Summary
======================================================================
Total issues processed: 9
✓ Successful: 9
Changes NOT applied (dry run mode). Run without --dry-run to apply.
```

## Workflow

### Recommended Usage Workflow

1. **Preview changes**
   ```bash
   python3 update-domain-issue-labels.py --dry-run
   ```

2. **Review output** and ensure expected changes

3. **Apply changes for one domain at a time**
   ```bash
   # Shop management (ready for implementation)
   python3 update-domain-issue-labels.py --domain shopmgmt
   
   # Location (phase 1 complete)
   python3 update-domain-issue-labels.py --domain location
   
   # People (phases 1-2 complete)
   python3 update-domain-issue-labels.py --domain people
   
   # Security (phases 1-2 complete)
   python3 update-domain-issue-labels.py --domain security
   
   # Pricing (phases 1-2 complete)
   python3 update-domain-issue-labels.py --domain pricing
   ```

4. **Verify in GitHub** that labels were updated correctly

## Error Handling

The script handles common errors gracefully:
- Missing GitHub token → Clear error message with instructions
- Authentication failure → Displays GitHub API error
- Issue not found → Reports issue number with error
- Network issues → Catches and reports exceptions

## Troubleshooting

### Token Issues
- Ensure token has `repo` scope
- Token must not be expired
- Verify token with: `curl -H "Authorization: token ghp_xxxxx" https://api.github.com/user`

### Permission Errors
- Token must have write access to `durion-moqui-frontend` repository
- User must be owner or have appropriate permissions

### Rate Limiting
- GitHub API has rate limits (60 requests/hour for unauthenticated, 5000 for authenticated)
- Script makes ~1 request per issue plus 1 for auth
- Should not hit limits for 9 issues

## Next Steps After Label Update

1. Review Phase documentation:
   - `Durion-Processing-ShopMgmt-Phase1.md` (shop management)
   - `domains/location/location-questions.md`
   - `domains/people/people-questions.md`
   - `domains/security/security-questions.md`
   - `domains/pricing/pricing-questions.md`

2. Begin implementation phase:
   - Backend service implementation
   - Frontend form creation
   - Validation and error handling
   - Testing and accessibility

3. Use issue comments as detailed acceptance criteria for development

## Files

- `scripts/update-domain-issue-labels.py` — Main script
- `scripts/update-domain-issue-labels-README.md` — This documentation

## See Also

- `update-accounting-issue-labels.py` — Similar script for accounting domain
- `durion/domains/` — Domain-specific business rules and notes
- `Durion-Processing-ShopMgmt-Phase*.md` — Shop management phase documentation
