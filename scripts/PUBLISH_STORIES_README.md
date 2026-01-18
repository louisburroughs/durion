# Story Publishing Scripts

Scripts for publishing rewritten stories from `story-work/frontend/{issue}/after.md` to GitHub issues.

## Overview

The `publish_stories.py` script:
1. Reads `after.md` files from story folders
2. Validates the markdown structure
3. Updates GitHub issue bodies with the content
4. Applies labels in separate API calls
5. Tracks progress in a resumable plan file (`.publish_plan.json`)

## Prerequisites

- Python 3.10+
- `GITHUB_TOKEN` environment variable with repo write access

### Setting Up Python Environment

**Option 1: Virtual Environment (Recommended)**

```bash
cd /home/louisb/Projects/durion

# Create a virtual environment in the scripts directory
python3 -m venv scripts/.venv

# Activate it
source scripts/.venv/bin/activate

# Install dependencies
pip install -r scripts/requirements.txt

# Run scripts (venv is active)
./scripts/publish_stories.sh --issue 65 --dry-run

# Deactivate when done
deactivate
```

**Option 2: User-level Install**

```bash
pip3 install --user requests
```

**Note**: The `.venv` directory is gitignored and safe to create locally

## Usage

**Important**: Activate your virtual environment first if you created one:
```bash
source scripts/.venv/bin/activate
```

### Test with a Single Issue

```bash
# Using Python directly
python scripts/publish_stories.py \
  --story-root ./scripts/story-work/frontend \
  --owner louisburroughs \
  --repo durion-moqui-frontend \
  --issue 65

# Using the shell wrapper (assumes defaults)
./scripts/publish_stories.sh --issue 65
```

### Dry Run (Preview Changes)

```bash
python scripts/publish_stories.py \
  --story-root ./scripts/story-work/frontend \
  --owner louisburroughs \
  --repo durion-moqui-frontend \
  --issue 65 \
  --dry-run
```

### Batch Mode (Process All Pending)

```bash
# Using Python directly
python scripts/publish_stories.py \
  --story-root ./scripts/story-work/frontend \
  --owner louisburroughs \
  --repo durion-moqui-frontend

# Using the shell wrapper
./scripts/publish_stories.sh
```

### Include Recommended Labels

By default, only **Required** and **Blocking/Risk** labels are applied. To also apply **Recommended** labels:

```bash
./scripts/publish_stories.sh --include-recommended
```

### Fail on Blocking Labels

To abort publishing if blocking/risk labels are present:

```bash
./scripts/publish_stories.sh --fail-on-blocking
```

### Refresh the Plan

Scan for new issues and add them to the pending list:

```bash
./scripts/publish_stories.sh --force-refresh
```

### Verbose Logging

```bash
./scripts/publish_stories.sh --verbose --issue 65
```

## Plan File

Progress is tracked in `story-work/frontend/.publish_plan.json`:

```json
{
  "version": "1.0",
  "created_at": "2026-01-18T...",
  "updated_at": "2026-01-18T...",
  "owner": "louisburroughs",
  "repo": "durion-moqui-frontend",
  "state": {
    "pending": [66, 67, 68],
    "processing": [],
    "completed": {
      "65": {
        "processed_at": "2026-01-18T...",
        "file": "story-work/frontend/65/after.md"
      }
    },
    "failed": {
      "70": {
        "error": "Validation failed: Missing section",
        "last_attempt": "2026-01-18T..."
      }
    }
  }
}
```

## Validation Rules

The script validates that `after.md` contains:

1. **Labels Section**: `## 🏷️ Labels (Proposed)`
   - `### Required`
   - `### Recommended`
   - `### Blocking / Risk`

2. **Required Content Sections**:
   - `## Story Intent`
   - `## Actors & Stakeholders`
   - `## Preconditions`
   - `## Functional Behavior`
   - `## Acceptance Criteria`

## Label Application Strategy

### Always Applied
- All labels from `### Required`
- All labels from `### Blocking / Risk` (except "none")

### Optionally Applied
- Labels from `### Recommended` (with `--include-recommended`)

### Conflict Resolution
- **Status labels**: When applying a new `status:*` label, existing `status:*` labels are removed first
- **Domain labels**: When applying a new `domain:*` label (and no `blocked:domain-conflict`), existing `domain:*` labels are removed first

## Exit Codes

- `0` - Success
- `1` - General error or failures during batch processing
- `2` - Invalid arguments or missing files
- `3` - Missing `GITHUB_TOKEN`
- `4` - Parse error
- `5` - Blocking labels present (with `--fail-on-blocking`)

## Examples

### Workflow: Test One Issue First

```bash
# 1. Test with dry-run
./scripts/publish_stories.sh --issue 65 --dry-run

# 2. Apply to GitHub
./scripts/publish_stories.sh --issue 65

# 3. Check the result on GitHub, then batch process remaining
./scripts/publish_stories.sh
```

### Workflow: Batch Process with Safety Checks

```bash
# Process all, but abort on blocking labels
./scripts/publish_stories.sh --fail-on-blocking

# If any failed, check plan file for details
cat scripts/story-work/frontend/.publish_plan.json | jq '.state.failed'
```

### Workflow: Reprocess Failed Issues

```bash
# Edit plan file to move failed issues back to pending
# Or use --force-refresh to rescan all
./scripts/publish_stories.sh --force-refresh

# Then process specific issue
./scripts/publish_stories.sh --issue 70
```

## Integration with Story Rewriting

This script is designed to work with stories processed by `story_update.py`:

1. `extract_stories.py` - Extracts issues from GitHub to `stories/`
2. `story_update.py` - Rewrites stories using LLM, creates `after.md`
3. **`publish_stories.py`** - Publishes `after.md` back to GitHub

## Comparison with Backend Scripts

The backend uses similar scripts under `durion-positivity-backend/.story-work/tools/`:
- `publish_rewrite.sh` - Shell-based publishing
- `apply_labels_from_rewrite.sh` - Standalone label application

This script combines both functions in a single Python implementation with:
- Better error handling
- Resumable batch processing
- Atomic plan file updates
- GitHub API retry logic

## Environment Variables

- `GITHUB_TOKEN` (required) - GitHub personal access token with `repo` scope
- `OWNER` (optional, for shell wrapper) - Repository owner (default: `louisburroughs`)
- `REPO` (optional, for shell wrapper) - Repository name (default: `durion-moqui-frontend`)

## Troubleshooting

### "GITHUB_TOKEN environment variable required"

```bash
export GITHUB_TOKEN="ghp_your_token_here"
./scripts/publish_stories.sh --issue 65
```

### "after.md not found"

Check that the issue folder exists:
```bash
ls -la scripts/story-work/frontend/65/
```

### "Validation failed"

Read the error message and check `after.md` structure:
```bash
cat scripts/story-work/frontend/65/after.md
```

### Rate Limiting

The script automatically retries with exponential backoff. If you hit rate limits frequently, consider:
- Using a fine-grained token with higher rate limits
- Processing in smaller batches
- Increasing delays between requests

## Related Documentation

- [Story Extraction](./README.md#extract_storiespy) - Extracting issues from GitHub
- [Story Rewriting](./README.md#story_updatepy) - LLM-based story rewriting
- [Backend Story Tools](../durion-positivity-backend/.story-work/README.md) - Backend equivalent scripts
