#!/usr/bin/env bash
set -euo pipefail

# Module verification hook for Orchestrator workflows.
#
# Purpose:
# - Run full Maven verify for touched backend modules in durion-positivity-backend.
# - Emit deterministic evidence for orchestration gates.
#
# Usage:
#   ./.github/hooks/module-verify-hook.sh \
#     --repo /abs/path/to/durion-positivity-backend \
#     [--modules pos-order,pos-invoice] \
#     [--base-ref origin/main --head-ref HEAD] \
#     [--fail-fast]
#
# Notes:
# - Provide either:
#   - --modules (explicit module list), or
#   - --base-ref (detect touched modules via git diff base..head).
# - If neither is provided, touched modules are detected from local staged/unstaged
#   changes only.

repo_path=""
modules_arg=""
base_ref=""
head_ref="HEAD"
fail_fast="false"
include_untracked="true"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      repo_path="$2"
      shift 2
      ;;
    --modules)
      modules_arg="$2"
      shift 2
      ;;
    --base-ref)
      base_ref="$2"
      shift 2
      ;;
    --head-ref)
      head_ref="$2"
      shift 2
      ;;
    --fail-fast)
      fail_fast="true"
      shift
      ;;
    --include-untracked)
      include_untracked="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$repo_path" ]]; then
  echo "Missing required arguments." >&2
  echo "Required: --repo and one of --modules|--base-ref (or local changes present)" >&2
  exit 2
fi

if [[ "$include_untracked" != "true" && "$include_untracked" != "false" ]]; then
  echo "--include-untracked must be true or false (received: $include_untracked)" >&2
  exit 2
fi

if [[ ! -d "$repo_path/.git" ]]; then
  echo "Repo path is not a git repository: $repo_path" >&2
  exit 2
fi

if [[ ! -f "$repo_path/mvnw" ]]; then
  echo "Maven wrapper not found: $repo_path/mvnw" >&2
  exit 2
fi

declare -a modules=()

add_module_if_missing() {
  local candidate="$1"
  local existing
  [[ -z "$candidate" ]] && return
  for existing in "${modules[@]}"; do
    if [[ "$existing" == "$candidate" ]]; then
      return
    fi
  done
  modules+=("$candidate")
}

collect_modules_from_text() {
  local text="$1"
  local module=""
  while IFS= read -r module; do
    [[ -z "$module" ]] && continue
    add_module_if_missing "$module"
  done < <(printf '%s\n' "$text")
}

pushd "$repo_path" >/dev/null

if [[ -n "$modules_arg" ]]; then
  collect_modules_from_text "$(printf '%s' "$modules_arg" | tr ', ' '\n\n' | sed '/^$/d')"
else
  changed_files=""
  if [[ -n "$base_ref" ]]; then
    if ! git rev-parse --verify "$base_ref" >/dev/null 2>&1; then
      echo "Base ref not found: $base_ref" >&2
      popd >/dev/null
      exit 2
    fi
    if ! git rev-parse --verify "$head_ref" >/dev/null 2>&1; then
      echo "Head ref not found: $head_ref" >&2
      popd >/dev/null
      exit 2
    fi
    changed_files="$(git diff --name-only "${base_ref}..${head_ref}")"
  else
    changed_files="$(
      {
        git diff --name-only
        git diff --cached --name-only
        if [[ "$include_untracked" == "true" ]]; then
          git ls-files --others --exclude-standard
        fi
      } | sort -u
    )"
  fi

  touched_modules="$(
    printf '%s\n' "$changed_files" \
      | awk -F/ '/^pos-[^/]+\/.*/ { print $1 }' \
      | sort -u
  )"
  collect_modules_from_text "$touched_modules"
fi

if [[ ${#modules[@]} -eq 0 ]]; then
  echo "No touched backend modules detected. Nothing to verify." >&2
  popd >/dev/null
  exit 2
fi

for module in "${modules[@]}"; do
  if [[ ! -d "$repo_path/$module" || ! -f "$repo_path/$module/pom.xml" ]]; then
    echo "Invalid module path (missing directory or pom.xml): $module" >&2
    popd >/dev/null
    exit 2
  fi
done

declare -a passed_modules=()
declare -a failed_modules=()

module_csv="$(IFS=,; echo "${modules[*]}")"
echo "Module verify target | modules=${module_csv}"

for module in "${modules[@]}"; do
  verify_cmd=(./mvnw -pl "$module" -DskipTests=false verify)
  set +e
  "${verify_cmd[@]}"
  verify_exit=$?
  set -e

  hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  if [[ $verify_exit -eq 0 ]]; then
    passed_modules+=("$module")
    echo "Module verify PASS | module=${module} | ts=${hook_timestamp}"
  else
    failed_modules+=("$module")
    echo "Module verify FAIL | module=${module} | exit=${verify_exit} | ts=${hook_timestamp}"
    if [[ "$fail_fast" == "true" ]]; then
      break
    fi
  fi
done

total_count=${#modules[@]}
passed_count=${#passed_modules[@]}
failed_count=${#failed_modules[@]}

failed_csv=""
if [[ $failed_count -gt 0 ]]; then
  failed_csv="$(IFS=,; echo "${failed_modules[*]}")"
fi

echo "Module verify summary | total=${total_count} | passed=${passed_count} | failed=${failed_count} | failed_modules=${failed_csv}"

popd >/dev/null

if [[ $failed_count -gt 0 ]]; then
  exit 1
fi
