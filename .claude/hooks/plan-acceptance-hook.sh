#!/usr/bin/env bash
set -euo pipefail

# Plan acceptance hook for Orchestrator workflows.
#
# Purpose:
# - Validate Planner output shape before orchestration proceeds.
# - Enforce core plan gates deterministically.
#
# Usage:
#   ./.github/hooks/plan-acceptance-hook.sh \
#     --plan-file /abs/path/to/Durion-Processing.md

plan_file=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --plan-file)
      plan_file="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$plan_file" ]]; then
  echo "Missing required arguments." >&2
  echo "Required: --plan-file" >&2
  exit 2
fi

if [[ ! -f "$plan_file" ]]; then
  echo "Plan file not found: $plan_file" >&2
  exit 2
fi

step1_line="$(grep -nE "Step 1:" "$plan_file" | head -n1 | cut -d: -f1 || true)"
final_step_line="$(grep -nE "Final Step:" "$plan_file" | head -n1 | cut -d: -f1 || true)"

if [[ -z "$step1_line" ]]; then
  echo "Plan acceptance FAIL | missing=Step 1 label"
  exit 1
fi

if [[ -z "$final_step_line" ]]; then
  echo "Plan acceptance FAIL | missing=Final Step label"
  exit 1
fi

if (( step1_line >= final_step_line )); then
  echo "Plan acceptance FAIL | order=Step 1 must appear before Final Step"
  exit 1
fi

step1_text="$(sed -n "${step1_line}p" "$plan_file" | tr '[:upper:]' '[:lower:]')"
if [[ "$step1_text" != *read* || "$step1_text" != *source* ]]; then
  echo "Plan acceptance FAIL | step1=must indicate source-material reading"
  exit 1
fi

final_step_context="$(sed -n "${final_step_line},$((final_step_line + 4))p" "$plan_file")"
if [[ ! "$final_step_context" =~ pull-request-hook\.sh ]]; then
  echo "Plan acceptance FAIL | final_step=must reference durion/.github/hooks/pull-request-hook.sh"
  exit 1
fi

hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "Plan acceptance hook PASS | step1_line=${step1_line} | final_step_line=${final_step_line} | ts=${hook_timestamp}"
