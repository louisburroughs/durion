#!/usr/bin/env bash
set -euo pipefail

# Post-coverage commit hook for Orchestrator workflows.
#
# Purpose:
# - Run after Test Coverage Agent finishes and threshold is satisfied.
# - Create a bounded commit for coverage-related test changes.
#
# Usage:
#   ./.github/hooks/post-test-coverage-commit.sh \
#     --repo /abs/path/to/durion-positivity-backend \
#     --story CAP-123 \
#     --module pos-order \
#     --coverage-before 71.2 \
#     --coverage-after 82.5
#
# Notes:
# - This hook only commits if there are staged or unstaged changes.
# - It does not push.

repo_path=""
story=""
module=""
coverage_before=""
coverage_after=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      repo_path="$2"
      shift 2
      ;;
    --story)
      story="$2"
      shift 2
      ;;
    --module)
      module="$2"
      shift 2
      ;;
    --coverage-before)
      coverage_before="$2"
      shift 2
      ;;
    --coverage-after)
      coverage_after="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$repo_path" || -z "$story" || -z "$module" || -z "$coverage_before" || -z "$coverage_after" ]]; then
  echo "Missing required arguments." >&2
  echo "Required: --repo --story --module --coverage-before --coverage-after" >&2
  exit 2
fi

if [[ ! -d "$repo_path/.git" ]]; then
  echo "Repo path is not a git repository: $repo_path" >&2
  exit 2
fi

pushd "$repo_path" >/dev/null

git update-index -q --refresh

if [[ -z "$(git status --porcelain)" ]]; then
  echo "No changes detected. Skipping coverage commit."
  popd >/dev/null
  exit 0
fi

git add -A

if [[ -z "$(git diff --cached --name-only)" ]]; then
  echo "No staged changes after add. Skipping coverage commit."
  popd >/dev/null
  exit 0
fi

commit_message="test(${module}): harden coverage for ${story}

Coverage improved from ${coverage_before}% to ${coverage_after}%
via Test Coverage Agent follow-up tests."

git commit -m "$commit_message"

echo "Coverage commit created for ${story} (${module})"
echo "Commit: $(git rev-parse --short HEAD)"

popd >/dev/null
