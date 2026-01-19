# Story Consolidation by Domain

## Purpose

Consolidates rewritten frontend stories from individual `after.md` files into domain-grouped text files for easier review and analysis.

## Usage

```bash
cd /home/louisb/Projects/durion

# Use defaults (story-work/frontend → story-work/output)
./scripts/consolidate-stories-by-domain.sh

# Or specify custom paths
./scripts/consolidate-stories-by-domain.sh /path/to/stories /path/to/output
```

## What It Does

1. **Scans** all `after.md` files in `story-work/frontend/{issue}/`
2. **Extracts** the `domain:` label from each story's label section
3. **Groups** stories by domain
4. **Writes** one consolidated file per domain: `domain-{name}.txt`
5. **Generates** a summary file: `DOMAINS_SUMMARY.txt`

## Output Structure

```
story-work/output/
├── domain-accounting.txt      (36 stories)
├── domain-audit.txt           (3 stories)
├── domain-billing.txt         (10 stories)
├── domain-crm.txt             (16 stories)
├── domain-inventory.txt       (31 stories)
├── domain-location.txt        (6 stories)
├── domain-order.txt           (2 stories)
├── domain-people.txt          (9 stories)
├── domain-positivity.txt      (1 stories)
├── domain-pricing.txt         (10 stories)
├── domain-security.txt        (6 stories)
├── domain-shopmgmt.txt        (3 stories)
├── domain-workexec.txt        (53 stories)
└── DOMAINS_SUMMARY.txt
```

## File Format

Each domain file contains:

```
════════════════════════════════════════════════════════════════
DOMAIN: {domain}
Total Stories: {count}
Generated: {timestamp}
════════════════════════════════════════════════════════════════

════════════════════════════════════════════════════════════════
ISSUE #{number}: {title}
File: {path}
════════════════════════════════════════════════════════════════

{full after.md content}

{... more stories ...}
```

## Requirements

- Bash 4+ (for associative arrays)
- `grep -P` (Perl regex support)
- Stories must have a `domain:` label in the `## 🏷️ Labels (Proposed)` section

## Known Issues / Gotchas

- **Arithmetic operations with `set -e`**: The script uses `|| true` after `((var++))` operations because incrementing from 0 returns exit code 1, which would terminate the script under `set -e`.
- **Missing domain labels**: Stories without a `domain:` label are skipped with a warning.
- **Non-numeric folders**: Only folders with numeric names (issue numbers) are processed.

## Related Scripts

- [publish_stories.py](./publish_stories.py) - Publishes rewritten stories to GitHub issues
- [PUBLISH_STORIES_README.md](./PUBLISH_STORIES_README.md) - Documentation for GitHub publishing

## Example Output

```
Found 186 after.md files
Processed 186 files with domain labels

[1/13] Processing domain:accounting (36 stories)...
  ✓ Wrote 36 stories to ./scripts/story-work/output/domain-accounting.txt
...
[13/13] Processing domain:workexec (53 stories)...
  ✓ Wrote 53 stories to ./scripts/story-work/output/domain-workexec.txt

✓ Complete! Files created in: ./scripts/story-work/output
  Summary: ./scripts/story-work/output/DOMAINS_SUMMARY.txt
```
