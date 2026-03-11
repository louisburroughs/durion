#!/usr/bin/env bash
set -euo pipefail

# Lightweight touched-file lint hook for backend Java modules.
#
# Purpose:
# - Run local lint/static checks only on touched Java files.
# - Avoid IDE-extension coupling (for example, SonarLint VS Code hooks).
#
# Default tool:
# - semgrep with the p/java ruleset.
#
# Usage:
#   ./.github/hooks/lint-run-hook.sh \
#     --repo /abs/path/to/durion-positivity-backend \
#     --module pos-order \
#     [--files pos-order/src/main/java/a/Foo.java,pos-order/src/test/java/a/FooTest.java] \
#     [--base-ref origin/main --head-ref HEAD] \
#     [--config p/java] \
#     [--install]
#
# Notes:
# - Provide one of:
#   - --files (explicit file list), or
#   - --base-ref (detect touched files from git diff base..head), or
#   - nothing (detect touched files from local staged/unstaged/untracked changes).
# - Only .java files are linted by this hook.

repo_path=""
module=""
files_arg=""
base_ref=""
head_ref="HEAD"
semgrep_config="p/java"
auto_install="false"
include_untracked="true"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      repo_path="$2"
      shift 2
      ;;
    --module)
      module="$2"
      shift 2
      ;;
    --files)
      files_arg="$2"
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
    --config)
      semgrep_config="$2"
      shift 2
      ;;
    --install)
      auto_install="true"
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

if [[ -z "$repo_path" || -z "$module" ]]; then
  echo "Missing required arguments." >&2
  echo "Required: --repo --module" >&2
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

if [[ ! -d "$repo_path/$module" || ! -f "$repo_path/$module/pom.xml" ]]; then
  echo "Invalid module path (missing directory or pom.xml): $module" >&2
  exit 2
fi

install_semgrep_if_needed() {
  if command -v semgrep >/dev/null 2>&1; then
    return 0
  fi

  if [[ "$auto_install" != "true" ]]; then
    echo "semgrep is not installed." >&2
    echo "Install with: pipx install semgrep" >&2
    echo "Or rerun this hook with --install." >&2
    return 1
  fi

  if command -v pipx >/dev/null 2>&1; then
    echo "Installing semgrep via pipx..."
    pipx install semgrep
  elif command -v python3 >/dev/null 2>&1; then
    echo "Installing semgrep via python3 --user..."
    python3 -m pip install --user semgrep
    export PATH="$HOME/.local/bin:$PATH"
  else
    echo "Cannot auto-install semgrep: neither pipx nor python3 found." >&2
    return 1
  fi

  if ! command -v semgrep >/dev/null 2>&1; then
    echo "semgrep installation did not produce a runnable semgrep binary." >&2
    return 1
  fi
}

declare -a candidate_files=()

add_candidate_file() {
  local path="$1"
  local normalized="$path"
  normalized="${normalized#./}"
  if [[ "$normalized" == "$repo_path/"* ]]; then
    normalized="${normalized#"$repo_path/"}"
  fi
  [[ -z "$normalized" ]] && return
  [[ "$normalized" != "$module/"* ]] && return
  [[ "$normalized" != *.java ]] && return
  if [[ -f "$repo_path/$normalized" ]]; then
    candidate_files+=("$normalized")
  fi
}

collect_from_files_arg() {
  local item=""
  while IFS= read -r item; do
    [[ -z "$item" ]] && continue
    add_candidate_file "$item"
  done < <(printf '%s\n' "$files_arg" | tr ', ' '\n\n' | sed '/^$/d')
}

collect_from_git_changes() {
  local changed_files=""
  pushd "$repo_path" >/dev/null

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
  popd >/dev/null

  local item=""
  while IFS= read -r item; do
    [[ -z "$item" ]] && continue
    add_candidate_file "$item"
  done < <(printf '%s\n' "$changed_files")
}

if [[ -n "$files_arg" ]]; then
  collect_from_files_arg
else
  collect_from_git_changes
fi

if [[ ${#candidate_files[@]} -eq 0 ]]; then
  echo "Lint skip | module=${module} | reason=no touched Java files"
  exit 0
fi

install_semgrep_if_needed

declare -a unique_files=()
declare -A seen=()
for path in "${candidate_files[@]}"; do
  if [[ -z "${seen[$path]+x}" ]]; then
    seen[$path]="1"
    unique_files+=("$path")
  fi
done

file_csv="$(IFS=,; echo "${unique_files[*]}")"
echo "Lint run context | module=${module} | config=${semgrep_config} | files=${file_csv}"

pushd "$repo_path" >/dev/null
set +e
semgrep --error --metrics=off --config "$semgrep_config" "${unique_files[@]}"
lint_exit=$?
set -e
popd >/dev/null

hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
if [[ $lint_exit -eq 0 ]]; then
  echo "Lint run PASS | module=${module} | files=${#unique_files[@]} | ts=${hook_timestamp}"
else
  echo "Lint run FAIL | module=${module} | exit=${lint_exit} | files=${#unique_files[@]} | ts=${hook_timestamp}"
fi

exit $lint_exit
