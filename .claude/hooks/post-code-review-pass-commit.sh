#!/usr/bin/env bash
set -euo pipefail

# Post-code-review-pass commit hook for Orchestrator workflows.
#
# Purpose:
# - Run immediately after Code Review Agent returns PASS for a story.
# - Create a bounded commit for review-driven corrections before coverage begins.
#
# Usage:
#   ./.github/hooks/post-code-review-pass-commit.sh \
#     --repo /abs/path/to/durion-positivity-backend \
#     --story CAP-123 \
#     --module pos-order \
#     --review-verdict PASS
#
# Notes:
# - This hook only commits if there are staged or unstaged changes.
# - It does not push.

repo_path=""
story=""
module=""
review_verdict=""

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
    --review-verdict)
      review_verdict="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$repo_path" || -z "$story" || -z "$module" || -z "$review_verdict" ]]; then
  echo "Missing required arguments." >&2
  echo "Required: --repo --story --module --review-verdict" >&2
  exit 2
fi

if [[ "$review_verdict" != "PASS" ]]; then
  echo "Review verdict is '$review_verdict'. Hook only commits on PASS." >&2
  exit 2
fi

if [[ ! -d "$repo_path/.git" ]]; then
  echo "Repo path is not a git repository: $repo_path" >&2
  exit 2
fi

pushd "$repo_path" >/dev/null

git update-index -q --refresh

if [[ -z "$(git status --porcelain)" ]]; then
  echo "No changes detected. Skipping post-review commit."
  popd >/dev/null
  exit 0
fi

git add -A

if [[ -z "$(git diff --cached --name-only)" ]]; then
  echo "No staged changes after add. Skipping post-review commit."
  popd >/dev/null
  exit 0
fi

commit_message="chore(${module}): apply review-pass updates for ${story}

Code Review Agent verdict: ${review_verdict}
Auto-commit after review loop completion before coverage phase."

git commit -m "$commit_message"

echo "Post-review commit created for ${story} (${module})"
echo "Commit: $(git rev-parse --short HEAD)"

popd >/dev/null
