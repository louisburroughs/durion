# Domain Automation Scripts - Complete Package

## 📦 What You Have

A complete Python automation script package for managing GitHub issue labels across all Durion platform domains.

### Files Delivered

```
/home/louisb/Projects/durion/scripts/
├── update-domain-issue-labels.py       ← MAIN SCRIPT (executable Python)
├── QUICK_START.md                      ← Start here (1 minute)
├── UPDATE_DOMAIN_LABELS_README.md      ← Full documentation
├── DELIVERY_SUMMARY.md                 ← What was delivered
└── README.md                           ← Scripts directory overview
```

## 🚀 Quick Start (Copy & Paste)

### Step 1: Install Dependencies
```bash
pip install PyGithub
```

### Step 2: Get GitHub Token
1. Visit https://github.com/settings/tokens/new?scopes=repo
2. Click "Generate Token"
3. Copy the token (you won't see it again)

### Step 3: Preview Changes
```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxx
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py --dry-run
```

### Step 4: Apply Changes
```bash
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py
```

Done! All 9 issues across 5 domains will have their labels updated.

## 📊 What Gets Updated

### By Domain

| Domain | Issues | From | To |
|--------|--------|------|-----|
| **Shop Mgmt** | #76 | `blocked:clarification` | `analysis:complete`, `ready:backend-implementation`, `ready:frontend-implementation` |
| **Location** | #141, #139 | `blocked:clarification` | `analysis:in-progress`, `phase:contract-discovery` |
| **People** | #130 | `blocked:clarification` | `analysis:in-progress`, `phase:data-contracts` |
| **Security** | #66, #65 | `blocked:clarification` | `analysis:in-progress`, `phase:data-contracts` |
| **Pricing** | #236, #161, #84 | `blocked:clarification` | `analysis:in-progress`, `phase:data-contracts` |

**Total:** 9 issues, 5 domains, ~45 label changes

## 💡 Common Tasks

### Update One Domain Only
```bash
# Shop management (ready for implementation)
python3 scripts/update-domain-issue-labels.py --domain shopmgmt

# Location (phase 1 complete)
python3 scripts/update-domain-issue-labels.py --domain location

# People (phases 1-2 complete)
python3 scripts/update-domain-issue-labels.py --domain people

# Security (phases 1-2 complete)
python3 scripts/update-domain-issue-labels.py --domain security

# Pricing (phases 1-2 complete)
python3 scripts/update-domain-issue-labels.py --domain pricing
```

### Get Help
```bash
python3 scripts/update-domain-issue-labels.py --help
```

### Use Token as Argument
```bash
python3 scripts/update-domain-issue-labels.py --token ghp_xxxxx --dry-run
```

### Update Without Dry Run
```bash
# Requires GITHUB_TOKEN or --token
python3 scripts/update-domain-issue-labels.py
```

## 📖 Documentation Files

### QUICK_START.md
- 1-minute setup
- Common commands
- Troubleshooting
- **START HERE if you want the fastest path**

### UPDATE_DOMAIN_LABELS_README.md
- Complete issue reference
- All label changes documented
- Environment setup detailed
- Workflow recommendations
- Error handling guide
- **USE THIS for detailed information**

### README.md (in scripts directory)
- Overview of all scripts
- Integration with development
- File structure
- Contributing guidelines
- **USE THIS to understand the scripts directory**

### DELIVERY_SUMMARY.md
- What was delivered
- Setup & usage
- File structure
- Next steps
- Troubleshooting
- **USE THIS as a reference after setup**

## ✅ Features

The script supports:

- ✅ **Dry run mode** — Preview before applying (`--dry-run`)
- ✅ **Domain filtering** — Update specific domains (`--domain shopmgmt`)
- ✅ **Colored output** — Easy-to-read progress reporting
- ✅ **Batch updates** — Update all 9 issues in one run
- ✅ **Error handling** — Graceful failure with helpful messages
- ✅ **Token authentication** — Via env var or `--token` argument
- ✅ **Progress tracking** — See what's being updated in real-time

## 🔧 Requirements

| Item | Requirement |
|------|-------------|
| **Python** | 3.6+ |
| **Library** | PyGithub (`pip install PyGithub`) |
| **GitHub Token** | Personal access token with `repo` scope |
| **Permissions** | Write access to `durion-moqui-frontend` repo |
| **Network** | Internet connection to GitHub API |

## 📝 Usage Examples

### Example 1: Safe First Run
```bash
# Step 1: Preview all changes
python3 scripts/update-domain-issue-labels.py --dry-run

# Step 2: Review output
# Step 3: If it looks correct, apply
python3 scripts/update-domain-issue-labels.py
```

### Example 2: Update One Domain
```bash
# Shop management only (ready for implementation)
python3 scripts/update-domain-issue-labels.py --domain shopmgmt --dry-run

# Review output, then apply
python3 scripts/update-domain-issue-labels.py --domain shopmgmt
```

### Example 3: Staged Rollout
```bash
# Update each domain one at a time
for domain in shopmgmt location people security pricing; do
  echo "Updating $domain..."
  python3 scripts/update-domain-issue-labels.py --domain $domain
done
```

## 🐛 Troubleshooting

### PyGithub Not Installed
```bash
pip install PyGithub
```

### Token Not Working
```bash
# Verify token has repo scope
curl -H "Authorization: token ghp_xxxxx" https://api.github.com/user

# Create new token at https://github.com/settings/tokens/new?scopes=repo
```

### Permission Denied
- Ensure you have write access to `durion-moqui-frontend`
- User must be owner or collaborator
- Token must have `repo` scope

### Issues Not Found
- Check issue numbers are correct (see table above)
- Repository should be `louisburroughs/durion-moqui-frontend`
- Script configuration should match your environment

## 📚 Next Steps

After labels are updated:

1. **Backend Implementation** (Shop Management)
   - Use `AppointmentsService` as starting point
   - Reference DECISION documents in code
   - Add correlation ID propagation

2. **Frontend Implementation** (Shop Management)
   - Create appointment creation form
   - Implement conflict handling UI
   - Add error message display

3. **Continue Phase 5 for Other Domains**
   - Location: Bay creation and constraints
   - People: Time approval workflow
   - Security: Role matrix definition
   - Pricing: Tax calculation and overrides

4. **Reference Documentation**
   - `Durion-Processing-ShopMgmt-Phase*.md` files
   - Domain business rules in `durion/domains/*/`
   - Architecture docs in `durion/docs/`

## 📞 Support

### Documentation
- [QUICK_START.md](QUICK_START.md) — Quick setup and examples
- [UPDATE_DOMAIN_LABELS_README.md](UPDATE_DOMAIN_LABELS_README.md) — Complete reference
- [README.md](README.md) — Scripts directory overview
- [DELIVERY_SUMMARY.md](DELIVERY_SUMMARY.md) — What was delivered

### Script Help
```bash
python3 update-domain-issue-labels.py --help
```

### GitHub API Documentation
- https://docs.github.com/en/rest
- https://github.com/PyGithub/PyGithub

## ✨ Summary

| Aspect | Status |
|--------|--------|
| **Script Ready** | ✅ Yes |
| **Documentation Complete** | ✅ Yes |
| **All 9 Issues Configured** | ✅ Yes |
| **All 5 Domains Supported** | ✅ Yes |
| **Ready to Use** | ✅ Yes |

**Next Action:** Run with `--dry-run`, verify output, then apply changes.

---

**Location:** `/home/louisb/Projects/durion/scripts/`  
**Created:** 2025-01-25  
**Status:** ✅ READY FOR USE
