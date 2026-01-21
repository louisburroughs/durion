# Quick Start: fix_issue_openai.py

## Installation

1. Ensure you have Python 3.8+ and the required environment variables:

```bash
# Check Python version
python3 --version

# Set up environment variables
export OPENAI_API_KEY="sk-proj-..."  # Get from https://platform.openai.com/api-keys
export GITHUB_TOKEN="ghp_..."        # Get from https://github.com/settings/tokens (optional for --update-github)
```

2. Copy the script to your Durion workspace (already done):
```bash
ls -la /home/louisb/Projects/durion/scripts/fix_frontend_issues/fix_issue_openai.py
```

## Basic Usage

### Process a single file

```bash
cd /home/louisb/Projects/durion

# Specify domain explicitly
python3 scripts/fix_frontend_issues/fix_issue_openai.py inventory \
  scripts/story-work/frontend/67/after.md

# Check the output
cat scripts/story-work/frontend/67/fixed.md
```

### Process by issue ID

```bash
python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issue 67 \
  --issue-dir scripts/story-work/frontend
```

### Process multiple issues

```bash
# By list
python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issues 65,67,68 \
  --issue-dir scripts/story-work/frontend

# By range
python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issue-range 60-70 \
  --issue-dir scripts/story-work/frontend
```

### With GitHub updates

```bash
python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issue 67 \
  --issue-dir scripts/story-work/frontend \
  --update-github \
  --owner louisburroughs \
  --repo durion-moqui-frontend
```

## Dry-run Mode

Test without making changes:

```bash
python3 scripts/fix_frontend_issues/fix_issue_openai.py inventory \
  scripts/story-work/frontend/67/after.md \
  --dry-run
```

## Troubleshooting

### "OPENAI_API_KEY environment variable not set"

```bash
export OPENAI_API_KEY="sk-..."
python3 scripts/fix_frontend_issues/fix_issue_openai.py --help
```

### "OpenAI API error (401): Unauthorized"

Your API key is invalid or expired. Get a new one from: https://platform.openai.com/api-keys

### "Domain not specified and no domain label found"

The script couldn't determine the domain. Either:
1. Pass domain as first argument: `python3 script.py inventory /path/to/after.md`
2. Add a domain label to the Required section of after.md:
   ```markdown
   ### Required
   - domain:inventory
   ```

### "File not found: /path/to/after.md"

Verify the path exists and is correct:
```bash
ls -la /path/to/after.md
```

## Viewing Results

### Check fixed.md

```bash
cat scripts/story-work/frontend/67/fixed.md
```

### View debug log

```bash
tail -100 scripts/story-work/frontend/67/fix_issue_debug.log
```

### Check GitHub issue

Visit: `https://github.com/louisburroughs/durion-moqui-frontend/issues/67`

## Common Workflows

### Workflow 1: Single Issue Testing

```bash
# Step 1: Test with dry-run first
python3 scripts/fix_frontend_issues/fix_issue_openai.py inventory \
  scripts/story-work/frontend/67/after.md \
  --dry-run

# Step 2: Process without GitHub update
python3 scripts/fix_frontend_issues/fix_issue_openai.py inventory \
  scripts/story-work/frontend/67/after.md

# Step 3: Review fixed.md
cat scripts/story-work/frontend/67/fixed.md

# Step 4: Update GitHub if satisfied
python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issue 67 \
  --issue-dir scripts/story-work/frontend \
  --update-github \
  --owner louisburroughs \
  --repo durion-moqui-frontend
```

### Workflow 2: Batch Processing

```bash
# Process range 60-70
python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issue-range 60-70 \
  --issue-dir scripts/story-work/frontend \
  --update-github \
  --owner louisburroughs \
  --repo durion-moqui-frontend

# Monitor progress
for i in {60..70}; do
  [ -f "scripts/story-work/frontend/$i/fixed.md" ] && echo "✓ $i" || echo "✗ $i"
done
```

### Workflow 3: Custom Models

```bash
# Use GPT-4 Turbo for better quality
python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issue 67 \
  --issue-dir scripts/story-work/frontend \
  --model gpt-4-turbo

# Use gpt-4o-mini for cost efficiency (default)
python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issue 67 \
  --issue-dir scripts/story-work/frontend \
  --model gpt-4o-mini
```

## Cost Estimation

For processing a batch of 10 issues with average 3000 tokens each:

| Model | Input | Output | Est. Cost |
|-------|-------|--------|-----------|
| gpt-4o-mini | 30K tokens | 40K tokens | ~$0.02 |
| gpt-4-turbo | 30K tokens | 40K tokens | ~$0.05 |
| gpt-4o | 30K tokens | 40K tokens | ~$0.15 |

See: https://openai.com/pricing

## Advanced Options

### Custom prompt file

```bash
python3 scripts/fix_frontend_issues/fix_issue_openai.py inventory \
  scripts/story-work/frontend/67/after.md \
  --prompt-file /path/to/custom-prompt.md
```

### Environment variables only (no CLI args)

```bash
export OPENAI_MODEL="gpt-4-turbo"  # Default: gpt-4o-mini
export GITHUB_OWNER="louisburroughs"
export GITHUB_REPO="durion-moqui-frontend"

python3 scripts/fix_frontend_issues/fix_issue_openai.py \
  --issue 67 \
  --issue-dir scripts/story-work/frontend \
  --update-github
```

## Testing

Run unit tests:

```bash
python3 scripts/fix_frontend_issues/test_fix_issue_openai.py -v
```

Expected: All 21 tests pass

## Documentation

- **README**: [README_OPENAI.md](README_OPENAI.md)
- **Migration Guide**: [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md)
- **Examples**: [examples.sh](examples.sh)
- **Tests**: [test_fix_issue_openai.py](test_fix_issue_openai.py)

## Support

For issues or questions:
1. Check the log file: `fix_issue_debug.log`
2. Review the migration guide: [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md)
3. Run tests to verify setup: `python3 test_fix_issue_openai.py`
4. Check OpenAI API status: https://status.openai.com/
