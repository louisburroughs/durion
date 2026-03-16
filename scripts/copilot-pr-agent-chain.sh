#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  copilot-pr-agent-chain.sh \
    --manifest <path/to/CAPABILITY_MANIFEST.yaml> \
    [--prompt1 <file>] \
    [--prompt2 <file>] \
    [--pr <number>] \
    [--repo <owner/repo>] \
    [--min-delay-seconds <seconds>] \
    [--max-extra-wait-seconds <seconds>] \
    [--poll-seconds <seconds>] \
    [--copilot-login-filter <substring>] \
    [--model <model>] \
    [--status-only]

Description:
  1) Runs Copilot agent with prompt1 and CAPABILITY_MANIFEST content.
  2) Waits at least min-delay-seconds (default: 600).
  3) Resolves PR from the manifest (stories[*].pr_links.backend_pr) unless --pr is set.
  4) Polls PR reviews/reviewRequests to detect Copilot review completion when possible.
  5) Runs Copilot agent with prompt2 and updated CAPABILITY_MANIFEST content (unless --status-only).

Notes:
  - Requires: copilot, gh, jq
  - --prompt1/--prompt2 are optional if defaults are set in this script.
  - If PR is unknown before prompt1, the first agent should write it to stories[*].pr_links.backend_pr.
  - "in progress" is inferred from review request presence + no new Copilot review yet.
USAGE
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

PR_NUMBER=""
PROMPT1_FILE="~/IdeaProjects/durion/.github/prompts/orchestrator.prompt.md"
PROMPT2_FILE="~/IdeaProjects/durion/.github/prompts/pr-review-orchestrator.prompt.md"
MANIFEST_FILE=""
REPO="louisburroughs/durion-positivity-backend"
MIN_DELAY_SECONDS=600
MAX_EXTRA_WAIT_SECONDS=1800
POLL_SECONDS=30
COPILOT_LOGIN_FILTER="copilot"
COPILOT_MODEL=""
STATUS_ONLY=false
ACTIVE_PR_NUMBER=""
ACTIVE_REPO=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest)
      MANIFEST_FILE="${2:-}"
      shift 2
      ;;
    --pr)
      PR_NUMBER="${2:-}"
      shift 2
      ;;
    --prompt1)
      PROMPT1_FILE="${2:-}"
      shift 2
      ;;
    --prompt2)
      PROMPT2_FILE="${2:-}"
      shift 2
      ;;
    --repo)
      REPO="${2:-}"
      shift 2
      ;;
    --min-delay-seconds)
      MIN_DELAY_SECONDS="${2:-}"
      shift 2
      ;;
    --max-extra-wait-seconds)
      MAX_EXTRA_WAIT_SECONDS="${2:-}"
      shift 2
      ;;
    --poll-seconds)
      POLL_SECONDS="${2:-}"
      shift 2
      ;;
    --copilot-login-filter)
      COPILOT_LOGIN_FILTER="${2:-}"
      shift 2
      ;;
    --model)
      COPILOT_MODEL="${2:-}"
      shift 2
      ;;
    --status-only)
      STATUS_ONLY=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

require_cmd copilot
require_cmd gh
require_cmd jq

if ! [[ "$MIN_DELAY_SECONDS" =~ ^[0-9]+$ && "$MAX_EXTRA_WAIT_SECONDS" =~ ^[0-9]+$ && "$POLL_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "Delay/wait/poll values must be positive integers" >&2
  exit 1
fi

if [[ -n "$MANIFEST_FILE" && ! -f "$MANIFEST_FILE" ]]; then
  echo "Manifest file not found: $MANIFEST_FILE" >&2
  exit 1
fi

if [[ "$STATUS_ONLY" == "false" ]]; then
  if [[ -z "$PROMPT1_FILE" || -z "$PROMPT2_FILE" ]]; then
    echo "Prompt files are not configured. Set defaults in this script or pass --prompt1/--prompt2." >&2
    usage
    exit 1
  fi

  if [[ -z "$MANIFEST_FILE" ]]; then
    echo "--manifest is required unless --status-only is set" >&2
    usage
    exit 1
  fi

  if [[ ! -f "$PROMPT1_FILE" || ! -f "$PROMPT2_FILE" ]]; then
    echo "Prompt file not found" >&2
    exit 1
  fi
fi

if [[ "$STATUS_ONLY" == "true" && -z "$PR_NUMBER" && -z "$MANIFEST_FILE" ]]; then
  echo "--status-only needs either --pr or --manifest" >&2
  usage
  exit 1
fi

gh_pr_view_json() {
  if [[ -z "$ACTIVE_PR_NUMBER" ]]; then
    echo "Active PR number is not set" >&2
    return 1
  fi

  if [[ -n "$ACTIVE_REPO" ]]; then
    gh pr view "$ACTIVE_PR_NUMBER" --repo "$ACTIVE_REPO" --json "$1"
  elif [[ -n "$REPO" ]]; then
    gh pr view "$ACTIVE_PR_NUMBER" --repo "$REPO" --json "$1"
  else
    gh pr view "$ACTIVE_PR_NUMBER" --json "$1"
  fi
}

get_copilot_review_stats() {
  gh_pr_view_json reviews | jq -r --arg f "$COPILOT_LOGIN_FILTER" '
    [ .reviews[]
      | select(((.author.login // "") | ascii_downcase) | contains($f | ascii_downcase))
    ] as $copilot
    | "\($copilot | length)\t\(($copilot | map(.submittedAt // "") | sort | last) // "")"
  '
}

get_copilot_request_count() {
  gh_pr_view_json reviewRequests | jq -r --arg f "$COPILOT_LOGIN_FILTER" '
    [ .reviewRequests[]?
      | (.requestedReviewer.login // "")
      | ascii_downcase
      | contains($f | ascii_downcase)
      | select(.)
    ]
    | length
  '
}

parse_manifest_scalar_by_key() {
  local key="$1"
  if [[ -z "$MANIFEST_FILE" ]]; then
    return 1
  fi

  awk -v key="$key" '
    $0 ~ "^[[:space:]]*" key ":[[:space:]]*" {
      line = $0
      sub(/^[^:]*:[[:space:]]*/, "", line)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
      if (line == "\"\"" || line == "'"'"''"'"'" || line == "null") {
        next
      }
      gsub(/^["'"'"']|["'"'"']$/, "", line)
      if (line != "") {
        print line
        exit
      }
    }
  ' "$MANIFEST_FILE"
}

extract_pr_ref_from_manifest() {
  if [[ -z "$MANIFEST_FILE" ]]; then
    return 1
  fi

  local value
  value="$(parse_manifest_scalar_by_key backend_pr || true)"
  if [[ -n "$value" ]]; then
    echo "$value"
    return 0
  fi

  value="$(parse_manifest_scalar_by_key durion_contract_pr || true)"
  if [[ -n "$value" ]]; then
    echo "$value"
    return 0
  fi

  value="$(awk 'match($0, /https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+\/pull\/[0-9]+/) { print substr($0, RSTART, RLENGTH); exit }' "$MANIFEST_FILE")"
  if [[ -n "$value" ]]; then
    echo "$value"
    return 0
  fi

  value="$(awk 'match($0, /\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+\/pull\/[0-9]+/) { print substr($0, RSTART, RLENGTH); exit }' "$MANIFEST_FILE")"
  if [[ -n "$value" ]]; then
    echo "$value"
    return 0
  fi

  return 1
}

extract_pr_number_from_ref() {
  local ref="$1"

  if [[ "$ref" =~ ^[[:space:]]*#?([0-9]+)[[:space:]]*$ ]]; then
    echo "${BASH_REMATCH[1]}"
    return 0
  fi

  if [[ "$ref" =~ /pull/([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return 0
  fi

  return 1
}

extract_backend_repo_from_manifest() {
  if [[ -z "$MANIFEST_FILE" ]]; then
    return 1
  fi

  awk '
    /^[[:space:]]*repositories:[[:space:]]*$/ {
      in_repos = 1
      slug = ""
      type = ""
      next
    }

    in_repos && /^[^[:space:]]/ {
      in_repos = 0
    }

    in_repos {
      if ($0 ~ /^[[:space:]]*-[[:space:]]*name:[[:space:]]*/) {
        slug = ""
        type = ""
      }

      if ($0 ~ /^[[:space:]]*slug:[[:space:]]*/) {
        line = $0
        sub(/^[^:]*:[[:space:]]*/, "", line)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
        gsub(/^["'"'"']|["'"'"']$/, "", line)
        slug = line
      }

      if ($0 ~ /^[[:space:]]*type:[[:space:]]*/) {
        line = $0
        sub(/^[^:]*:[[:space:]]*/, "", line)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
        gsub(/^["'"'"']|["'"'"']$/, "", line)
        type = line
        if (type == "backend" && slug != "") {
          print slug
          exit
        }
      }
    }
  ' "$MANIFEST_FILE"
}

extract_repo_from_pr_ref() {
  local ref="$1"

  if [[ -n "$REPO" ]]; then
    echo "$REPO"
    return 0
  fi

  if [[ "$ref" =~ github\.com/([^/]+/[^/]+)/pull/[0-9]+ ]]; then
    echo "${BASH_REMATCH[1]}"
    return 0
  fi

  if [[ "$ref" =~ ^/?([^/]+/[^/]+)/pull/[0-9]+ ]]; then
    echo "${BASH_REMATCH[1]}"
    return 0
  fi

  local manifest_repo
  manifest_repo="$(extract_backend_repo_from_manifest || true)"
  if [[ -n "$manifest_repo" ]]; then
    echo "$manifest_repo"
    return 0
  fi

  return 1
}

resolve_active_pr_context() {
  local resolved_pr="" resolved_repo="" pr_ref=""

  if [[ -n "$PR_NUMBER" ]]; then
    resolved_pr="$PR_NUMBER"
  fi

  if [[ -z "$resolved_pr" ]]; then
    pr_ref="$(extract_pr_ref_from_manifest || true)"
    if [[ -z "$pr_ref" ]]; then
      return 1
    fi
    resolved_pr="$(extract_pr_number_from_ref "$pr_ref" || true)"
  fi

  if [[ -z "$resolved_pr" ]]; then
    return 1
  fi

  if [[ -n "$REPO" ]]; then
    resolved_repo="$REPO"
  elif [[ -n "$pr_ref" ]]; then
    resolved_repo="$(extract_repo_from_pr_ref "$pr_ref" || true)"
  else
    resolved_repo="$(extract_backend_repo_from_manifest || true)"
  fi

  ACTIVE_PR_NUMBER="$resolved_pr"
  ACTIVE_REPO="$resolved_repo"
  return 0
}

print_status() {
  if ! resolve_active_pr_context; then
    echo "No PR resolved from --pr or manifest yet."
    if [[ -n "$MANIFEST_FILE" ]]; then
      local pr_ref
      pr_ref="$(extract_pr_ref_from_manifest || true)"
      echo "Manifest PR reference: ${pr_ref:-none}"
      echo "Hint: first agent should set stories[*].pr_links.backend_pr in the manifest."
    fi
    return 0
  fi

  local stats count latest req
  stats="$(get_copilot_review_stats)"
  count="${stats%%$'\t'*}"
  latest="${stats#*$'\t'}"
  req="$(get_copilot_request_count)"

  echo "PR: #$ACTIVE_PR_NUMBER${ACTIVE_REPO:+ ($ACTIVE_REPO)}"
  echo "Copilot review count: $count"
  echo "Last copilot review submittedAt: ${latest:-none}"
  echo "Copilot review requests pending: $req"

  if [[ "$req" -gt 0 ]]; then
    echo "In progress: likely yes (requested and no completion guarantee yet)"
  else
    echo "In progress: unknown/no active review request detected"
  fi
}

run_agent_with_prompt_file() {
  local prompt_file="$1"
  local prompt_text
  prompt_text="$(cat "$prompt_file")"

  if [[ -n "$MANIFEST_FILE" ]]; then
    prompt_text="${prompt_text}"$'\n\n'"CAPABILITY_MANIFEST_PATH: ${MANIFEST_FILE}"$'\n'
    prompt_text="${prompt_text}Use this manifest for context in this run."
    prompt_text="${prompt_text} If you create or update a backend PR, update stories[*].pr_links.backend_pr with the PR URL/path."$'\n'
    prompt_text="${prompt_text}CAPABILITY_MANIFEST_CONTENT_START"$'\n'"$(cat "$MANIFEST_FILE")"$'\n'"CAPABILITY_MANIFEST_CONTENT_END"
  fi

  local cmd=(copilot --yolo --allow-all-tools --allow-all-paths --allow-all-urls)
  if [[ -n "$COPILOT_MODEL" ]]; then
    cmd+=(--model "$COPILOT_MODEL")
  fi
  cmd+=(-p "$prompt_text")

  "${cmd[@]}"
}

if [[ "$STATUS_ONLY" == "true" ]]; then
  print_status
  exit 0
fi

first_agent_started_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

echo "Launching first Copilot agent prompt from: $PROMPT1_FILE"
echo "Manifest context: $MANIFEST_FILE"
run_agent_with_prompt_file "$PROMPT1_FILE"

echo "Sleeping minimum delay: ${MIN_DELAY_SECONDS}s"
sleep "$MIN_DELAY_SECONDS"

deadline=$(( $(date +%s) + MAX_EXTRA_WAIT_SECONDS ))

while ! resolve_active_pr_context; do
  if (( $(date +%s) >= deadline )); then
    echo "No PR resolved from manifest within ${MAX_EXTRA_WAIT_SECONDS}s after minimum delay."
    echo "Continuing to second prompt without PR review polling."
    echo "Launching second Copilot agent prompt from: $PROMPT2_FILE"
    run_agent_with_prompt_file "$PROMPT2_FILE"
    exit 0
  fi

  echo "Waiting for manifest PR path (stories[*].pr_links.backend_pr)..."
  sleep "$POLL_SECONDS"
done

echo "Resolved PR for polling: #$ACTIVE_PR_NUMBER${ACTIVE_REPO:+ ($ACTIVE_REPO)}"

while true; do
  current_stats="$(get_copilot_review_stats)"
  current_count="${current_stats%%$'\t'*}"
  current_latest="${current_stats#*$'\t'}"
  request_count="$(get_copilot_request_count)"

  if [[ -n "$current_latest" && "$current_latest" > "$first_agent_started_at" ]]; then
    echo "Detected Copilot review submitted after first prompt start: $current_latest"
    break
  fi

  if (( request_count == 0 )); then
    echo "No pending Copilot review request detected; proceeding."
    break
  fi

  if (( $(date +%s) >= deadline )); then
    echo "Timed out waiting for Copilot review update within ${MAX_EXTRA_WAIT_SECONDS}s. Continuing."
    break
  fi

  echo "Copilot review request still pending; waiting ${POLL_SECONDS}s..."
  echo "Current Copilot review count: $current_count; latest submittedAt: ${current_latest:-none}"

  sleep "$POLL_SECONDS"
done

echo "Launching second Copilot agent prompt from: $PROMPT2_FILE"
run_agent_with_prompt_file "$PROMPT2_FILE"
