#!/usr/bin/env bash
set -euo pipefail

# Initialize capability run artifacts from the shared template.
#
# Purpose:
# - Create docs/capabilities/CAP-*/runs/latest.md for each capability.
# - Seed latest.md from docs/capabilities/RUN_ARTIFACT_TEMPLATE.md.
#
# Usage:
#   ./.github/hooks/init-capability-runs-hook.sh
#   ./.github/hooks/init-capability-runs-hook.sh --dry-run
#   ./.github/hooks/init-capability-runs-hook.sh --overwrite
#   ./.github/hooks/init-capability-runs-hook.sh --repo-root /abs/path/to/durion

repo_root=""
template_path=""
overwrite="false"
dry_run="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo-root)
      repo_root="$2"
      shift 2
      ;;
    --template)
      template_path="$2"
      shift 2
      ;;
    --overwrite)
      overwrite="true"
      shift
      ;;
    --dry-run)
      dry_run="true"
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$repo_root" ]]; then
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  repo_root="$(cd "$script_dir/../.." && pwd)"
fi

if [[ -z "$template_path" ]]; then
  template_path="$repo_root/docs/capabilities/RUN_ARTIFACT_TEMPLATE.md"
fi

if [[ ! -d "$repo_root/docs/capabilities" ]]; then
  echo "Capabilities directory not found under repo root: $repo_root" >&2
  exit 2
fi

if [[ ! -f "$template_path" ]]; then
  echo "Template file not found: $template_path" >&2
  exit 2
fi

render_latest_md() {
  local cap_id="$1"
  local ts_utc="$2"

  cat <<EOF
# Capability Run Artifact

Use this run record with:
- Manifest: docs/capabilities/${cap_id}/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/${cap_id}/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 1. Run Metadata

- Capability: ${cap_id}
- Run Timestamp (UTC): ${ts_utc}
- Agent/Operator: <name>
- Branch(es): <branch names>
- Status: in-progress | completed | blocked

## 2. Inputs Used

- Manifest: docs/capabilities/${cap_id}/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/${cap_id}/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| <story title> | <#> | <#> | done/blocked/partial | <short note> |

## 4. Implementation Changes

### Frontend Files Changed
- <path/to/file1>
- <path/to/file2>

### Behavior Implemented
- <story behavior 1>
- <story behavior 2>

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| <story> | <operationId> | <openapi/spec + sdk> | <path> | done/blocked |

## 6. Validation

### Commands Run
\`\`\`bash
<command 1>
<command 2>
\`\`\`

### Results
- Build: pass/fail
- Tests: pass/fail
- Lint: pass/fail
- Typecheck: pass/fail

## 7. Blockers and Decisions

- Blocker: <description>
  - Impact: <scope>
  - Needed: <decision/input/fix>

## 8. Follow-Up Actions

- [ ] <action 1>
- [ ] <action 2>

## 9. Completion Gate

Mark complete only if all are true:
- [ ] All workset stories processed.
- [ ] All required operations wired or explicitly blocked with reason.
- [ ] Acceptance criteria verified against story markdown and wireframe.
- [ ] Validation commands executed and results recorded.
- [ ] runs/latest.md reflects final state.
EOF
}

created_count=0
updated_count=0
skipped_count=0

ts_utc="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

while IFS= read -r -d '' cap_dir; do
  cap_id="$(basename "$cap_dir")"
  runs_dir="$cap_dir/runs"
  latest_file="$runs_dir/latest.md"

  if [[ -f "$latest_file" && "$overwrite" != "true" ]]; then
    skipped_count=$((skipped_count + 1))
    echo "SKIP  $latest_file"
    continue
  fi

  if [[ "$dry_run" == "true" ]]; then
    if [[ -f "$latest_file" ]]; then
      updated_count=$((updated_count + 1))
      echo "DRY   update $latest_file"
    else
      created_count=$((created_count + 1))
      echo "DRY   create $latest_file"
    fi
    continue
  fi

  mkdir -p "$runs_dir/history"
  render_latest_md "$cap_id" "$ts_utc" > "$latest_file"

  if [[ -f "$latest_file" && "$overwrite" == "true" ]]; then
    updated_count=$((updated_count + 1))
    echo "WRITE update $latest_file"
  else
    created_count=$((created_count + 1))
    echo "WRITE create $latest_file"
  fi
done < <(find "$repo_root/docs/capabilities" -maxdepth 1 -mindepth 1 -type d -name 'CAP-*' -print0 | sort -z)

hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "Init capability runs hook PASS | created=${created_count} | updated=${updated_count} | skipped=${skipped_count} | dry_run=${dry_run} | ts=${hook_timestamp}"