# Durion Domain Issue Automation Scripts

## Overview

This directory contains scripts for automating GitHub issue label updates across the Durion platform domains. All scripts follow the same pattern established by [update-accounting-issue-labels.py](#).

## Available Scripts

### Domain Issues Label Update Script

**File:** `update-domain-issue-labels.py`

Updates labels for all resolved domain analysis issues (9 issues across 5 domains):
- Shop Management (Phase 1-3 complete) — Ready for implementation
- Location (Phase 1 complete) — In contract discovery
- People (Phases 1-2 complete) — In data contracts
- Security (Phases 1-2 complete) — In data contracts
- Pricing (Phases 1-2 complete) — In data contracts

**Quick Usage:**
```bash
# Preview changes
python3 update-domain-issue-labels.py --dry-run

# Apply changes
GITHUB_TOKEN=ghp_xxxxx python3 update-domain-issue-labels.py

# Update one domain
python3 update-domain-issue-labels.py --domain shopmgmt --dry-run
```

**Domains Supported:** `all`, `shopmgmt`, `location`, `people`, `security`, `pricing`

**Documentation:**
- [QUICK_START.md](QUICK_START.md) — One-minute setup and common tasks
- [UPDATE_DOMAIN_LABELS_README.md](UPDATE_DOMAIN_LABELS_README.md) — Complete documentation

### Accounting Domain Labels Script

**File:** `update-accounting-issue-labels.py`

Updates labels for resolved accounting domain analysis issues.

## Quick Start

1. **Install dependencies:**
   ```bash
   pip install PyGithub
   ```

2. **Get GitHub token:** https://github.com/settings/tokens/new?scopes=repo

3. **Preview all changes:**
   ```bash
   export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxx
   python3 update-domain-issue-labels.py --dry-run
   ```

4. **Apply changes:**
   ```bash
   python3 update-domain-issue-labels.py
   ```

See [QUICK_START.md](QUICK_START.md) for more examples.

## Issues Updated

### By Domain

#### Shop Management (9 issues → Phase 1-3 complete)
- **#76** - Appointment: Create Appointment from Estimate or Order
  - Status: `analysis:complete`, `ready:backend-implementation`, `ready:frontend-implementation`

#### Location (2 issues → Phase 1 complete)
- **#141** - Locations: Create Bays with Constraints and Capacity
- **#139** - Scheduling: Create Appointment with CRM Customer and Vehicle
  - Status: `analysis:in-progress`, `phase:contract-discovery`

#### People (1 issue → Phases 1-2 complete)
- **#130** - Timekeeping: Approve Submitted Time for a Day/Workorder
  - Status: `analysis:in-progress`, `phase:data-contracts`

#### Security (2 issues → Phases 1-2 complete)
- **#66** - Define POS Roles and Permission Matrix
- **#65** - Financial Exception Audit Trail (Query/Export)
  - Status: `analysis:in-progress`, `phase:data-contracts`

#### Pricing (3 issues → Phases 1-2 complete)
- **#236** - Calculate Taxes and Totals on Estimate
- **#161** - Create Promotion Offer (Basic)
- **#84** - Apply Line Price Override with Permission and Reason
  - Status: `analysis:in-progress`, `phase:data-contracts`

**Total: 9 issues across 5 domains**

## Features

### All Domain Scripts Support

- ✅ **Dry run mode** - Preview changes without applying
- ✅ **Domain filtering** - Update specific domains only
- ✅ **Colored output** - Easy-to-read progress reporting
- ✅ **Error handling** - Graceful failure with helpful messages
- ✅ **Environment variables** - Set `GITHUB_TOKEN` or use `--token`
- ✅ **Batch updates** - Update multiple issues in one run
- ✅ **Progress tracking** - Shows what's being updated

### Command-Line Options

```bash
# Dry run (preview)
python3 update-domain-issue-labels.py --dry-run

# Specific domain
python3 update-domain-issue-labels.py --domain shopmgmt

# With token argument
python3 update-domain-issue-labels.py --token ghp_xxxxx

# Help
python3 update-domain-issue-labels.py --help
```

## Workflow Recommendations

### Single Domain Updates (Safest)
```bash
# Shop management first (ready for implementation)
python3 update-domain-issue-labels.py --domain shopmgmt --dry-run
python3 update-domain-issue-labels.py --domain shopmgmt

# Then location
python3 update-domain-issue-labels.py --domain location --dry-run
python3 update-domain-issue-labels.py --domain location

# Continue with others...
```

### Bulk Update (After Verification)
```bash
# Preview all
python3 update-domain-issue-labels.py --dry-run

# Apply all
python3 update-domain-issue-labels.py
```

## Integration with Development Workflow

After updating labels:

1. **Phase 5 Implementation** - Begin backend/frontend development using issue comments as acceptance criteria
2. **Code Reviews** - Reference DECISION documents in PRs
3. **Testing** - Use label changes to track implementation progress
4. **Tracking** - Monitor issue status as implementation progresses

## See Also

- [Durion Platform README](../README.md)
- [Shop Management Phase Documentation](../Durion-Processing-ShopMgmt-Phase*.md)
- [Domain Questions Documents](../domains/*/​*-questions.md)
- [Architecture Documentation](../docs/architecture/)

## Troubleshooting

### Common Issues

**PyGithub not installed**
```bash
pip install PyGithub
```

**Token authentication failed**
```bash
# Verify token scope includes 'repo'
curl -H "Authorization: token ghp_xxxxx" https://api.github.com/user
```

**Permission denied**
- Ensure token has write access to durion-moqui-frontend
- User must have collaborator status or higher

**Not found errors**
- Issue may have been deleted
- Repository name or number may be incorrect
- Check script configuration

See [UPDATE_DOMAIN_LABELS_README.md](UPDATE_DOMAIN_LABELS_README.md#troubleshooting) for detailed troubleshooting.

## File Structure

```
scripts/
  ├── update-domain-issue-labels.py      # Multi-domain label update script
  ├── update-accounting-issue-labels.py  # Accounting-specific script
  ├── QUICK_START.md                     # One-minute setup guide
  ├── UPDATE_DOMAIN_LABELS_README.md     # Complete documentation
  └── README.md                          # This file
```

## Contributing

When adding new domain automation scripts:

1. Follow the pattern established by `update-domain-issue-labels.py`
2. Support `--dry-run`, `--token`, and domain filtering
3. Use colored output for readability
4. Include comprehensive help text
5. Add documentation (QUICK_START + README)
6. Test with `--dry-run` before applying

## License

See [LICENSE.md](../LICENSE.md)
