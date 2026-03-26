#!/usr/bin/env bash
set -euo pipefail

# Pull request orchestration hook.
#
# Purpose:
# - Create pull request for the prepared branch.
# - Optionally launch a post-create background task after PR creation.
#
# Usage:
#   ./.github/hooks/pull-request-hook.sh \
#     --repo /abs/path/to/durion-positivity-frontend \
#     --story CAP-123 \
#     --base main \
#     --head chore/cap-123 \
#     --title "cap/123 feat: implement story" \
#     --body-file /abs/path/to/pr-body.md
#
# Notes:
# - This hook does not commit.
# - It will push the head branch to the remote if needed for non-interactive PR creation.
# - It creates the PR with `gh`, optionally launches a post-create background task,
#   and emits deterministic orchestration evidence.

repo_path=""
story=""
base_branch=""
head_branch=""
pr_title=""
pr_body_file=""
pr_body=""
is_draft="false"
remote_name="origin"
post_create_cmd=""
post_create_label=""

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
    --base)
      base_branch="$2"
      shift 2
      ;;
    --head)
      head_branch="$2"
      shift 2
      ;;
    --title)
      pr_title="$2"
      shift 2
      ;;
    --body-file)
      pr_body_file="$2"
      shift 2
      ;;
    --body)
      pr_body="$2"
      shift 2
      ;;
    --draft)
      is_draft="true"
      shift
      ;;
    --remote)
      remote_name="$2"
      shift 2
      ;;
    --post-create-cmd)
      post_create_cmd="$2"
      shift 2
      ;;
    --post-create-label)
      post_create_label="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$repo_path" || -z "$story" || -z "$base_branch" || -z "$head_branch" || -z "$pr_title" ]]; then
  echo "Missing required arguments." >&2
  echo "Required: --repo --story --base --head --title and one of --body-file|--body" >&2
  exit 2
fi

if [[ -z "$pr_body_file" && -z "$pr_body" ]]; then
  echo "Missing PR content: provide one of --body-file or --body" >&2
  exit 2
fi

if [[ -n "$pr_body_file" && -n "$pr_body" ]]; then
  echo "Use only one PR content source: --body-file or --body" >&2
  exit 2
fi

if [[ ! -d "$repo_path/.git" ]]; then
  echo "Repo path is not a git repository: $repo_path" >&2
  exit 2
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required but not found in PATH" >&2
  exit 2
fi

pushd "$repo_path" >/dev/null

if ! git rev-parse --verify "$head_branch" >/dev/null 2>&1; then
  echo "Head branch does not exist locally: $head_branch" >&2
  popd >/dev/null
  exit 2
fi

if ! git remote get-url "$remote_name" >/dev/null 2>&1; then
  echo "Remote not found: $remote_name" >&2
  popd >/dev/null
  exit 2
fi

if [[ -n "$pr_body_file" ]]; then
  if [[ ! -f "$pr_body_file" ]]; then
    echo "PR body file not found: $pr_body_file" >&2
    popd >/dev/null
    exit 2
  fi
fi

if ! git ls-remote --exit-code --heads "$remote_name" "$head_branch" >/dev/null 2>&1; then
  git push --set-upstream "$remote_name" "$head_branch" >/dev/null
fi

existing_pr_url="$(gh pr list --head "$head_branch" --state open --json url --jq '.[0].url' 2>/dev/null || true)"

if [[ -n "$existing_pr_url" ]]; then
  pr_url="$existing_pr_url"
else
  create_cmd=(gh pr create --base "$base_branch" --head "$head_branch" --title "$pr_title")
  if [[ -n "$pr_body_file" ]]; then
    create_cmd+=(--body-file "$pr_body_file")
  else
    create_cmd+=(--body "$pr_body")
  fi

  if [[ "$is_draft" == "true" ]]; then
    create_cmd+=(--draft)
  fi

  pr_url="$("${create_cmd[@]}")"
fi

pr_number="$(gh pr view "$pr_url" --json number --jq '.number')"

hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

echo "Pull-request hook PASS | story=${story} | pr=#${pr_number} | url=${pr_url} | ts=${hook_timestamp}"

if [[ -z "$post_create_cmd" ]]; then
  default_post_create_script="$repo_path/scripts/generate-openapi.sh"
  if [[ -f "$default_post_create_script" ]]; then
    if [[ ! -x "$default_post_create_script" ]]; then
      chmod +x "$default_post_create_script"
    fi
    post_create_cmd="$default_post_create_script"
    post_create_label="OpenAPI generation"
  fi
fi

if [[ -n "$post_create_cmd" ]]; then
  if [[ -z "$post_create_label" ]]; then
    post_create_label="Post-create task"
  fi

  post_create_slug="$(
    printf '%s' "$post_create_label" \
      | tr '[:upper:]' '[:lower:]' \
      | sed 's/[^a-z0-9]/-/g; s/--*/-/g; s/^-//; s/-$//'
  )"
  [[ -z "$post_create_slug" ]] && post_create_slug="post-create"

  post_create_log="$repo_path/logs/${post_create_slug}-${story}-${pr_number}.log"
  mkdir -p "$repo_path/logs"

  nohup bash -lc "cd \"$repo_path\" && $post_create_cmd" >"$post_create_log" 2>&1 &
  post_create_pid="$!"

  echo "${post_create_label} launched | pid=${post_create_pid} | log=${post_create_log}"
else
  echo "Post-create step skipped | reason=no configured command"
fi

popd >/dev/null
