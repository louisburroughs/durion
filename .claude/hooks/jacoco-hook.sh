#!/usr/bin/env bash
set -euo pipefail

# JaCoCo execution hook for Test Coverage Agent workflows.
#
# Purpose:
# - Run JaCoCo coverage for a target module using parent-POM plugin configuration.
# - Provide deterministic artifact evidence for orchestration.
#
# Usage:
#   ./.github/hooks/jacoco-hook.sh \
#     --repo /abs/path/to/durion-positivity-backend \
#     --module pos-order \
#     [--test-pattern '*Service*Test,*Util*Test,*Helper*Test'] \
#     [--bootstrap] \
#     [--low-resource-tests true|false] \
#     [--skip-its true|false]
#
# Notes:
# - The coverage loop does not use clean or -am.
# - Optional bootstrap runs one dependency install pass:
#     ./mvnw -pl <module> -am -DskipTests install

repo_path=""
module=""
test_pattern=""
run_bootstrap="false"
skip_its="true"
maven_quiet="true"
low_resource_tests="true"

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
    --test-pattern)
      test_pattern="$2"
      shift 2
      ;;
    --bootstrap)
      run_bootstrap="true"
      shift
      ;;
    --skip-its)
      skip_its="$2"
      shift 2
      ;;
    --jacoco-version)
      # Backward compatibility: version is managed in parent pom.xml.
      shift 2
      ;;
    --no-quiet)
      maven_quiet="false"
      shift
      ;;
    --low-resource-tests)
      low_resource_tests="$2"
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

if [[ "$skip_its" != "true" && "$skip_its" != "false" ]]; then
  echo "--skip-its must be true or false (received: $skip_its)" >&2
  exit 2
fi

if [[ "$low_resource_tests" != "true" && "$low_resource_tests" != "false" ]]; then
  echo "--low-resource-tests must be true or false (received: $low_resource_tests)" >&2
  exit 2
fi

if [[ ! -d "$repo_path/.git" ]]; then
  echo "Repo path is not a git repository: $repo_path" >&2
  exit 2
fi

# ── JavaScript/npm dispatch ───────────────────────────────────────────────────
if [[ -f "$repo_path/package.json" && ! -f "$repo_path/mvnw" ]]; then
  # Resolve module directory
  pkg_dir="$repo_path"
  if [[ -n "$module" ]]; then
    if [[ -d "$repo_path/packages/$module" ]]; then
      pkg_dir="$repo_path/packages/$module"
    elif [[ -d "$repo_path/$module" ]]; then
      pkg_dir="$repo_path/$module"
    fi
  fi

  cov_dir="$pkg_dir/coverage"

  if [[ "$run_bootstrap" == "true" ]]; then
    pushd "$pkg_dir" >/dev/null
    set +e
    npm install --silent
    boot_exit=$?
    set -e
    popd >/dev/null
    if [[ $boot_exit -ne 0 ]]; then
      echo "failure_stage: bootstrap"
      echo "primary_blocker: npm install failed for module ${module}"
      exit $boot_exit
    fi
  fi

  pushd "$pkg_dir" >/dev/null
  set +e
  if [[ -n "$test_pattern" ]]; then
    npx jest --coverage --passWithNoTests --testPathPattern="$test_pattern"
  else
    npx jest --coverage --passWithNoTests
  fi
  cov_exit=$?
  set -e
  popd >/dev/null

  if [[ $cov_exit -ne 0 ]]; then
    echo "failure_stage: test_execution"
    echo "test_status: failed"
    echo "primary_blocker: jest --coverage failed (exit=${cov_exit}) for module ${module}"
    exit $cov_exit
  fi

  hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

  # Determine which coverage artifact Jest produced
  lcov_info="${cov_dir}/lcov.info"
  cov_json="${cov_dir}/coverage-final.json"
  cov_text="${cov_dir}/coverage-summary.json"

  if [[ -f "$cov_json" ]]; then
    report_source="jest-coverage-json"
  elif [[ -f "$lcov_info" ]]; then
    report_source="jest-lcov"
  else
    report_source="jest-coverage"
  fi

  echo "JaCoCo hook PASS | module=${module} | report=${report_source} | ts=${hook_timestamp}"

  json_state="missing"; lcov_state="missing"
  [[ -f "$cov_json" ]] && json_state="present"
  [[ -f "$lcov_info" ]] && lcov_state="present"
  echo "Artifacts | exec=N/A | json=${json_state} | lcov=${lcov_state}"

  exit 0
fi
# ── End JavaScript/npm dispatch ───────────────────────────────────────────────

if [[ ! -f "$repo_path/mvnw" ]]; then
  echo "Maven wrapper not found: $repo_path/mvnw" >&2
  exit 2
fi

if [[ ! -d "$repo_path/$module" ]]; then
  echo "Module path not found: $repo_path/$module" >&2
  exit 2
fi

exec_path="$repo_path/$module/target/jacoco.exec"
csv_path="$repo_path/$module/target/site/jacoco/jacoco.csv"
xml_path="$repo_path/$module/target/site/jacoco/jacoco.xml"

failure_stage="preflight"
test_status="not_run"
jacoco_agent_status="not_run"
report_status="not_run"

artifact_state() {
  local artifact_path="$1"
  if [[ -f "$artifact_path" ]]; then
    echo "present"
  else
    echo "missing"
  fi
}

print_failure_block() {
  local primary_blocker="$1"
  cat <<EOF
failure_stage: ${failure_stage}
test_status: ${test_status}
jacoco_agent_status: ${jacoco_agent_status}
report_status: ${report_status}
artifacts:
  - ${module}/target/jacoco.exec: $(artifact_state "$exec_path")
  - ${module}/target/site/jacoco/jacoco.csv: $(artifact_state "$csv_path")
  - ${module}/target/site/jacoco/jacoco.xml: $(artifact_state "$xml_path")
primary_blocker: ${primary_blocker}
EOF
}

pushd "$repo_path" >/dev/null

if [[ "$run_bootstrap" == "true" ]]; then
  failure_stage="bootstrap"
  bootstrap_cmd=(./mvnw -pl "$module" -am -DskipTests "-DlowResourceTests=${low_resource_tests}" install)
  set +e
  "${bootstrap_cmd[@]}"
  bootstrap_exit=$?
  set -e
  if [[ $bootstrap_exit -ne 0 ]]; then
    print_failure_block "Bootstrap install failed for module ${module}"
    popd >/dev/null
    exit $bootstrap_exit
  fi
fi

failure_stage="test_execution"

jacoco_cmd=(./mvnw -pl "$module")
if [[ "$maven_quiet" == "true" ]]; then
  jacoco_cmd+=(-q)
fi
jacoco_cmd+=(
  "-DskipTests=false"
  "-DskipITs=${skip_its}"
  "-DlowResourceTests=${low_resource_tests}"
  "-Dmaven.test.failure.ignore=true"
)
if [[ -n "$test_pattern" ]]; then
  jacoco_cmd+=("-Dtest=${test_pattern}")
fi
jacoco_cmd+=(
  test
  jacoco:report
)

set +e
"${jacoco_cmd[@]}"
jacoco_exit=$?
set -e

if [[ $jacoco_exit -ne 0 ]]; then
  test_status="failed"
  jacoco_agent_status="failed"
  report_status="missing"
  print_failure_block "JaCoCo command failed (exit=${jacoco_exit})"
  popd >/dev/null
  exit $jacoco_exit
fi

test_status="passed"
jacoco_agent_status="attached"

report_source=""
if [[ -f "$csv_path" ]]; then
  report_source="csv"
  report_status="generated"
elif [[ -f "$xml_path" ]]; then
  report_source="xml"
  report_status="generated"
elif [[ -f "$exec_path" ]]; then
  failure_stage="jacoco_report"
  report_cmd=(./mvnw -pl "$module")
  if [[ "$maven_quiet" == "true" ]]; then
    report_cmd+=(-q)
  fi
  report_cmd+=(jacoco:report)

  set +e
  "${report_cmd[@]}"
  report_exit=$?
  set -e
  if [[ $report_exit -ne 0 ]]; then
    report_status="missing"
    print_failure_block "JaCoCo report regeneration failed (exit=${report_exit})"
    popd >/dev/null
    exit $report_exit
  fi

  if [[ -f "$csv_path" ]]; then
    report_source="csv"
    report_status="generated"
  elif [[ -f "$xml_path" ]]; then
    report_source="xml"
    report_status="generated"
  else
    failure_stage="coverage_parse"
    report_status="missing"
    print_failure_block "JaCoCo exec exists but csv/xml report artifacts are still missing"
    popd >/dev/null
    exit 1
  fi
else
  failure_stage="jacoco_report"
  report_status="missing"
  print_failure_block "No JaCoCo exec artifact produced by the test run"
  popd >/dev/null
  exit 1
fi

hook_timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "JaCoCo hook PASS | module=${module} | report=${report_source} | ts=${hook_timestamp}"
echo "Artifacts | exec=$(artifact_state "$exec_path") | csv=$(artifact_state "$csv_path") | xml=$(artifact_state "$xml_path")"

popd >/dev/null
