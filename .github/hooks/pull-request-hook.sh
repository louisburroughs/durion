#!/usr/bin/env bash
set -euo pipefail

# Pull request orchestration hook.
#
# Purpose:
# - Create pull request for the prepared branch.
# - Launch OpenAPI generation after PR creation.
#
# Usage:
#   ./.github/hooks/pull-request-hook.sh \
#     --repo /abs/path/to/durion-positivity-backend \
#     --story CAP-123 \
#     --base main \
#     --head chore/cap-123 \
#     --title "cap/123 feat: implement story" \
#     --body-file /abs/path/to/pr-body.md
#
# Notes:
# - This hook does not commit or push.
# - It creates the PR with `gh`, launches OpenAPI generation, and emits deterministic orchestration evidence.

repo_path=""
story=""
base_branch=""
head_branch=""
pr_title=""
pr_body_file=""
pr_body=""
is_draft="false"

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

if [[ -n "$pr_body_file" ]]; then
  if [[ ! -f "$pr_body_file" ]]; then
    echo "PR body file not found: $pr_body_file" >&2
    popd >/dev/null
    exit 2
  fi
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

openapi_script="$repo_path/scripts/generate-openapi.sh"
if [[ ! -f "$openapi_script" ]]; then
  echo "OpenAPI script not found: $openapi_script" >&2
  popd >/dev/null
  exit 2
fi

if [[ ! -x "$openapi_script" ]]; then
  chmod +x "$openapi_script"
fi

hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

echo "Pull-request hook PASS | story=${story} | pr=#${pr_number} | url=${pr_url} | ts=${hook_timestamp}"

openapi_log="$repo_path/logs/openapi-generate-${story}-${pr_number}.log"
mkdir -p "$repo_path/logs"

nohup "$openapi_script" >"$openapi_log" 2>&1 &
openapi_pid="$!"

echo "OpenAPI generation launched | pid=${openapi_pid} | log=${openapi_log}"

popd >/dev/null
