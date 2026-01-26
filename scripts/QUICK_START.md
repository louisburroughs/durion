# Quick Start: Domain Issues Label Update

## One-Minute Setup

### 1. Install PyGithub
```bash
pip install PyGithub
```

### 2. Get GitHub Token
Go to https://github.com/settings/tokens/new?scopes=repo and create a token.

### 3. Preview Changes (Recommended)
```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxx
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py --dry-run
```

### 4. Apply Changes
```bash
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py
```

## Common Tasks

### Update Shop Management Labels Only
```bash
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py \
  --domain shopmgmt --dry-run
```

### Update One Domain at a Time (Safest)
```bash
# Shop management (Phase 1-3 complete, ready for implementation)
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py --domain shopmgmt

# Location (Phase 1 complete)
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py --domain location

# People (Phases 1-2 complete)
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py --domain people

# Security (Phases 1-2 complete)
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py --domain security

# Pricing (Phases 1-2 complete)
python3 /home/louisb/Projects/durion/scripts/update-domain-issue-labels.py --domain pricing
```

## What Gets Updated

| Domain | Issues | Changes |
|--------|--------|---------|
| **Shop Management** | #76 | Remove `blocked:clarification`, Add `analysis:complete`, `ready:backend-implementation`, `ready:frontend-implementation` |
| **Location** | #141, #139 | Remove `blocked:clarification`, Add `analysis:in-progress`, `phase:contract-discovery` |
| **People** | #130 | Remove `blocked:clarification`, Add `analysis:in-progress`, `phase:data-contracts` |
| **Security** | #66, #65 | Remove `blocked:clarification`, Add `analysis:in-progress`, `phase:data-contracts` |
| **Pricing** | #236, #161, #84 | Remove `blocked:clarification`, Add `analysis:in-progress`, `phase:data-contracts` |

## Troubleshooting

**Token Error?**
```bash
# Verify token works
curl -H "Authorization: token ghp_xxxxx" https://api.github.com/user
```

**PyGithub Not Found?**
```bash
pip install --upgrade PyGithub
```

**Script Not Found?**
```bash
# Navigate to durion repo root
cd /home/louisb/Projects/durion
# Then run
python3 scripts/update-domain-issue-labels.py --help
```

## Next Steps

1. Run with `--dry-run` first to preview changes
2. Review the output to confirm expected labels
3. Run without `--dry-run` to apply changes
4. Verify in GitHub that labels are updated
5. Continue with Phase 5 implementation

## Full Documentation

See [UPDATE_DOMAIN_LABELS_README.md](UPDATE_DOMAIN_LABELS_README.md) for complete documentation including:
- Detailed issue list with all updates
- Environment variable setup
- Error handling
- Troubleshooting guide
- Workflow recommendations
