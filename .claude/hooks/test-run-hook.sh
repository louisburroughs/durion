#!/usr/bin/env bash
set -euo pipefail

# Test run hook for Orchestrator/Test Agent workflows.
#
# Purpose:
# - Centralize module test/verify command execution.
# - Auto-enable low-resource mode for full-module runs.
# - Keep targeted single-test runs fast without forcing low-resource flags.
#
# Usage:
#   ./.github/hooks/test-run-hook.sh \
#     --repo /abs/path/to/durion-positivity-backend \
#     --module pos-order \
#     [--goal test|verify|integration-test] \
#     [--also-make] \
#     [--test 'ClassNameTest'] \
#     [--it-test 'ClassNameIT'] \
#     [--low-resource auto|true|false] \
#     [--quiet] \
#     [--maven-arg '-DskipITs=true']
#
# Behavior:
# - low-resource=auto (default):
#   - enabled for full-module runs (no --test/--it-test)
#   - disabled for targeted runs (--test or --it-test provided)

repo_path=""
module=""
goal="test"
also_make="false"
test_pattern=""
it_test_pattern=""
low_resource="auto"
maven_quiet="false"
declare -a extra_maven_args=()

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
    --goal)
      goal="$2"
      shift 2
      ;;
    --also-make)
      also_make="true"
      shift
      ;;
    --test)
      test_pattern="$2"
      shift 2
      ;;
    --it-test)
      it_test_pattern="$2"
      shift 2
      ;;
    --low-resource)
      low_resource="$2"
      shift 2
      ;;
    --quiet)
      maven_quiet="true"
      shift
      ;;
    --maven-arg)
      extra_maven_args+=("$2")
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

case "$goal" in
  test|verify|integration-test)
    ;;
  *)
    echo "--goal must be one of: test, verify, integration-test (received: $goal)" >&2
    exit 2
    ;;
esac

# ── JavaScript/npm dispatch ───────────────────────────────────────────────────
if [[ -f "$repo_path/package.json" && ! -f "$repo_path/mvnw" ]]; then
  # Resolve module directory: packages/<module>, <module>, or repo root
  pkg_dir="$repo_path"
  if [[ -n "$module" ]]; then
    if [[ -d "$repo_path/packages/$module" ]]; then
      pkg_dir="$repo_path/packages/$module"
    elif [[ -d "$repo_path/$module" ]]; then
      pkg_dir="$repo_path/$module"
    fi
    # else: module name supplied but no subdirectory found; use repo root
  fi

  is_targeted="false"
  if [[ -n "$test_pattern" || -n "$it_test_pattern" ]]; then
    is_targeted="true"
  fi

  pushd "$pkg_dir" >/dev/null
  node_cmd=(npx jest --passWithNoTests)
  if [[ -n "$test_pattern" ]]; then
    node_cmd+=(--testPathPattern="$test_pattern")
  elif [[ -n "$it_test_pattern" ]]; then
    node_cmd+=(--testPathPattern="$it_test_pattern")
  fi
  if [[ "$is_targeted" == "false" ]]; then
    node_cmd+=(--coverage)
  fi
  if [[ "$maven_quiet" == "true" ]]; then
    node_cmd+=(--silent)
  fi

  set +e
  "${node_cmd[@]}"
  js_exit=$?
  set -e

  popd >/dev/null
  exit $js_exit
fi
# ── End JavaScript/npm dispatch ───────────────────────────────────────────────

if [[ "$low_resource" != "auto" && "$low_resource" != "true" && "$low_resource" != "false" ]]; then
  echo "--low-resource must be auto, true, or false (received: $low_resource)" >&2
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

targeted_run="false"
if [[ -n "$test_pattern" || -n "$it_test_pattern" ]]; then
  targeted_run="true"
fi

effective_low_resource="false"
if [[ "$low_resource" == "true" ]]; then
  effective_low_resource="true"
elif [[ "$low_resource" == "auto" && "$targeted_run" == "false" ]]; then
  effective_low_resource="true"
fi

cmd=(./mvnw -pl "$module")
if [[ "$also_make" == "true" ]]; then
  cmd+=(-am)
fi
if [[ "$maven_quiet" == "true" ]]; then
  cmd+=(-q)
fi
if [[ -n "$test_pattern" ]]; then
  cmd+=("-Dtest=${test_pattern}")
fi
if [[ -n "$it_test_pattern" ]]; then
  cmd+=("-Dit.test=${it_test_pattern}")
fi
if [[ "$effective_low_resource" == "true" ]]; then
  cmd+=("-DlowResourceTests=true")
fi
if [[ ${#extra_maven_args[@]} -gt 0 ]]; then
  cmd+=("${extra_maven_args[@]}")
fi
cmd+=("$goal")

pushd "$repo_path" >/dev/null

echo "Test run context | module=${module} | goal=${goal} | targeted=${targeted_run} | lowResource=${effective_low_resource}"
echo "Test run command | ${cmd[*]}"

set +e
"${cmd[@]}"
run_exit=$?
set -e

hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
if [[ $run_exit -eq 0 ]]; then
  echo "Test run PASS | module=${module} | goal=${goal} | ts=${hook_timestamp}"
else
  echo "Test run FAIL | module=${module} | goal=${goal} | exit=${run_exit} | ts=${hook_timestamp}"
fi

popd >/dev/null
exit $run_exit
