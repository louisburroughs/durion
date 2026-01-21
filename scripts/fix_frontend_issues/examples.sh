#!/bin/bash
# Example: Using fix_issue_openai.py to update story issues

# Prerequisites:
# - OPENAI_API_KEY environment variable set
# - GITHUB_TOKEN environment variable set (for --update-github)
# - Story issue directories under scripts/story-work/frontend/

# Example 1: Single issue with domain specified
echo "Example 1: Process single issue (domain: inventory)"
python3 fix_issue_openai.py inventory \
  /home/louisb/Projects/durion/scripts/story-work/frontend/67/after.md

echo ""
echo "---"
echo ""

# Example 2: Process by issue ID (requires --issue-dir)
echo "Example 2: Process issue by ID and update GitHub"
python3 fix_issue_openai.py \
  --issue 67 \
  --issue-dir /home/louisb/Projects/durion/scripts/story-work/frontend \
  --prompt-file .github/prompts/story-frontend-rewrite.prompt.md \
  --update-github \
  --owner louisburroughs \
  --repo durion-moqui-frontend

echo ""
echo "---"
echo ""

# Example 3: Process multiple issues by list
echo "Example 3: Process multiple issues (65, 67, 68)"
python3 fix_issue_openai.py \
  --issues 65,67,68 \
  --issue-dir /home/louisb/Projects/durion/scripts/story-work/frontend \
  --model gpt-4o-mini \
  --update-github \
  --owner louisburroughs \
  --repo durion-moqui-frontend

echo ""
echo "---"
echo ""

# Example 4: Process a range
echo "Example 4: Process range of issues (60-65)"
python3 fix_issue_openai.py \
  --issue-range 60-65 \
  --issue-dir /home/louisb/Projects/durion/scripts/story-work/frontend \
  --update-github \
  --owner louisburroughs \
  --repo durion-moqui-frontend

echo ""
echo "---"
echo ""

# Example 5: Dry-run mode (no API calls or file writes)
echo "Example 5: Dry-run mode"
python3 fix_issue_openai.py inventory \
  /home/louisb/Projects/durion/scripts/story-work/frontend/67/after.md \
  --dry-run

echo ""
echo "---"
echo ""

# Example 6: Using a custom model
echo "Example 6: Using GPT-4 Turbo model"
python3 fix_issue_openai.py \
  --issue 67 \
  --issue-dir /home/louisb/Projects/durion/scripts/story-work/frontend \
  --model gpt-4-turbo \
  --update-github \
  --owner louisburroughs \
  --repo durion-moqui-frontend
