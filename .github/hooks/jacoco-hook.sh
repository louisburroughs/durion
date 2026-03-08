#!/usr/bin/env bash
set -euo pipefail

# JaCoCo execution hook for Test Coverage Agent workflows.
#
# Purpose:
# - Run explicit JaCoCo goals for a target module, even when the module does not
#   declare the JaCoCo plugin.
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
jacoco_version="0.8.11"
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
      jacoco_version="$2"
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
  "org.jacoco:jacoco-maven-plugin:${jacoco_version}:prepare-agent"
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
  "org.jacoco:jacoco-maven-plugin:${jacoco_version}:report"
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
  report_cmd+=("org.jacoco:jacoco-maven-plugin:${jacoco_version}:report")

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
