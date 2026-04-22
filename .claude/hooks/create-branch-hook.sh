#!/usr/bin/env bash
set -euo pipefail

# Branch creation/switch hook for Orchestrator workflows.
#
# Purpose:
# - Create or switch to the execution branch for a story.
# - Replace ad-hoc branch creation instructions in orchestration.
#
# Usage:
#   ./.github/hooks/create-branch-hook.sh \
#     --repo /abs/path/to/durion-positivity-backend \
#     --base main \
#     --branch chore/cap-142
#
# Notes:
# - If the branch already exists locally, the hook switches to it.
# - If the branch does not exist, it is created from origin/<base> when available,
#   otherwise from the local <base> branch.
# - This hook does not push.

repo_path=""
base_branch=""
branch_name=""
remote_name="origin"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      repo_path="$2"
      shift 2
      ;;
    --base)
      base_branch="$2"
      shift 2
      ;;
    --branch)
      branch_name="$2"
      shift 2
      ;;
    --remote)
      remote_name="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$repo_path" || -z "$base_branch" || -z "$branch_name" ]]; then
  echo "Missing required arguments." >&2
  echo "Required: --repo --base --branch" >&2
  exit 2
fi

if [[ ! -d "$repo_path/.git" ]]; then
  echo "Repo path is not a git repository: $repo_path" >&2
  exit 2
fi

pushd "$repo_path" >/dev/null

git update-index -q --refresh
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit or stash changes before switching branches." >&2
  popd >/dev/null
  exit 2
fi

if git remote get-url "$remote_name" >/dev/null 2>&1; then
  git fetch "$remote_name" "$base_branch" --quiet || true
fi

if git show-ref --verify --quiet "refs/heads/$branch_name"; then
  git switch "$branch_name" >/dev/null
  source_ref="$branch_name"
else
  if git show-ref --verify --quiet "refs/remotes/$remote_name/$base_branch"; then
    git switch -c "$branch_name" --track "$remote_name/$base_branch" >/dev/null
    source_ref="$remote_name/$base_branch"
  elif git show-ref --verify --quiet "refs/heads/$base_branch"; then
    git switch -c "$branch_name" "$base_branch" >/dev/null
    source_ref="$base_branch"
  else
    echo "Base branch not found locally or on remote: $base_branch" >&2
    popd >/dev/null
    exit 2
  fi
fi

current_branch="$(git branch --show-current)"
hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

echo "Branch hook PASS | branch=${current_branch} | base=${base_branch} | source=${source_ref} | ts=${hook_timestamp}"

popd >/dev/null
