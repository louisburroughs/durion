#!/usr/bin/env bash
set -euo pipefail

# Self-test for mutation-check-hook.sh.
#
# WHY THIS EXISTS
# The mutation hook's entire value is that it does not lie. It reports whether a guarantee is defended,
# and every "DEFENDED" recorded in a wave ledger is trusted on its word — so a bug in the hook silently
# devalues every check that ran through it.
#
# It has already had one such bug. Gate 3 checked that a "Tests run:" line EXISTED in the Maven output;
# Maven prints "Tests run: 0, Failures: 0" when a selector matches nothing, so a run in which nothing
# executed passed the gate and was then reported as UNDEFENDED — a false finding, which is worse than no
# finding. The trigger was mundane: surefire's `Class#method` does not match a method inside a @Nested
# class (it needs `Class$Nested#method`), and this codebase uses @Nested heavily.
#
# The nested-selector case is therefore the first thing this script covers. Isolated unit-testing of the
# count-parsing expression would NOT have caught the original bug, because the bug was in what the gate
# considered sufficient evidence, not in string handling — so these cases drive the real hook end to end
# against real Maven output.
#
# This script is mirrored, byte-identical, at .github/hooks/ and .claude/hooks/. Run whichever copy you
# are reading: it resolves the hook under test as a sibling of itself, so each copy tests the hook that
# sits beside it.
#
# Usage:
#   mutation-check-selftest.sh [--repo /abs/path/to/durion-positivity-backend]
#
# Exit codes: 0 = every case behaved as specified
#             1 = the hook misbehaved (details printed), or the fixture no longer matches the codebase

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
hook="${script_dir}/mutation-check-hook.sh"
repo_path="${MUTATION_CHECK_SELFTEST_REPO:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) repo_path="$2"; shift 2 ;;
    -h|--help) sed -n '3,28p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$repo_path" ]]; then
  # Sibling layout, matching the rest of the hooks.
  repo_path="$(cd "${script_dir}/../.." && pwd)/durion-positivity-backend"
fi

if [[ ! -x "$hook" ]]; then
  echo "SELFTEST MISUSE | hook not executable at $hook" >&2
  exit 1
fi
if [[ ! -d "$repo_path" ]]; then
  echo "SELFTEST MISUSE | backend repo not found at $repo_path (pass --repo)" >&2
  exit 1
fi

# ── Fixture ───────────────────────────────────────────────────────────────────────────
# A real guard with a real test, deliberately rather than a synthetic one: the bug being guarded against
# only appears against genuine surefire output. If this code moves, the self-test fails loudly with
# "pattern matched 0 times" — which is correct behaviour, not breakage. Repoint the fixture.
fixture_module="pos-supplier"
fixture_file="pos-supplier/src/main/java/com/positivity/supplier/internal/service/SupplierExchangeAuditServiceImpl.java"
fixture_find='@Transactional(noRollbackFor = PayloadUnreadableException.class)'
fixture_replace='@Transactional'
fixture_class="SupplierExchangeAuditPersistenceTest"
fixture_nested="PayloadReads"
fixture_method="keepsTheAccessRecordWhenTheStoredContentCannotBeDecrypted"
# What the failure output must contain. Deliberately NOT the AssertJ .as(...) description: when
# assertThatThrownBy fails because nothing was thrown at all, AssertJ raises before the description is
# attached, so matching on it would report FAILED FOR THE WRONG REASON on a perfectly good check.
fixture_expected_message="noRollbackFor must keep this record"

failures=0
skipped=0
case_number=0

# Runs the hook and asserts an expected exit code and an expected substring in its output.
expect() {
  local description="$1" expected_exit="$2" expected_text="$3"
  shift 3
  case_number=$((case_number + 1))
  echo "── case ${case_number}: ${description}"

  local output actual_exit
  set +e
  output="$("$@" 2>&1)"
  actual_exit=$?
  set -e

  local ok=true
  if [[ "$actual_exit" != "$expected_exit" ]]; then
    echo "   FAIL | expected exit ${expected_exit}, got ${actual_exit}"
    ok=false
  fi
  # `grep -qF --` : the expected text can legitimately start with a dash (an option name being
  # reported), and without the terminator grep would parse it as its own flag and fail confusingly.
  if ! grep -qF -- "$expected_text" <<<"$output"; then
    echo "   FAIL | output did not contain: ${expected_text}"
    ok=false
  fi

  # The hook restores on every exit path, including error and interrupt. Verify that here rather than
  # trusting it: a hook that leaves a mutation in the tree is far worse than one that misreports.
  if ! git -C "$repo_path" diff --quiet -- "$fixture_file"; then
    echo "   FAIL | ${fixture_file} was left mutated"
    git -C "$repo_path" checkout -- "$fixture_file"
    ok=false
  fi

  if $ok; then
    echo "   ok"
  else
    echo "   ---- hook output ----"
    sed 's/^/   | /' <<<"$output" | tail -25
    failures=$((failures + 1))
  fi
}

if ! git -C "$repo_path" diff --quiet -- "$fixture_file"; then
  echo "SELFTEST MISUSE | ${fixture_file} has uncommitted changes; the restoration check needs a clean" >&2
  echo "  baseline to compare against. Commit or stash first." >&2
  exit 1
fi

echo "Mutation-check self-test | hook=${hook} | repo=${repo_path}"
echo

# ── Case 1: THE REGRESSION ────────────────────────────────────────────────────────────
# A method inside a @Nested class addressed with the plain `Class#method` form. Surefire matches nothing
# and Maven exits 0 having printed "Tests run: 0". The hook must ABORT and say so. Before the fix it
# reported UNDEFENDED — asserting that a guarantee was untested on the strength of no test having run.
expect "nested method addressed as Class#method aborts instead of reporting UNDEFENDED" \
  1 "no tests actually ran" \
  "$hook" --repo "$repo_path" --module "$fixture_module" --file "$fixture_file" \
  --find "$fixture_find" --replace "$fixture_replace" \
  --test "${fixture_class}#${fixture_method}"

# The abort must also point at the fix, or the next person re-runs it and draws the same false conclusion.
expect "the abort message names the \$Nested selector form" \
  1 'Outer$Nested#method' \
  "$hook" --repo "$repo_path" --module "$fixture_module" --file "$fixture_file" \
  --find "$fixture_find" --replace "$fixture_replace" \
  --test "${fixture_class}#${fixture_method}"

# ── Case 3: the same check, addressed correctly, must reach a real verdict ─────────────
# The only case that needs a working Docker daemon: the fixture is a Testcontainers-backed
# @DataJpaTest, so without one the context never starts and the test errors before reaching the
# assertion. Skipped rather than failed, and announced rather than quietly dropped — this is the
# case that proves a real DEFENDED verdict, so a run without it has not tested the hook's main
# claim. (The hook itself behaves correctly here: --expect-fail-message catches the infrastructure
# error and reports FAILED FOR THE WRONG REASON instead of a verdict it cannot support.)
if docker info >/dev/null 2>&1; then
  expect "the same guard with Class\$Nested#method reports DEFENDED" \
    0 "MUTATION CHECK RESULT: DEFENDED" \
    "$hook" --repo "$repo_path" --module "$fixture_module" --file "$fixture_file" \
    --find "$fixture_find" --replace "$fixture_replace" \
    --test "${fixture_class}\$${fixture_nested}#${fixture_method}" \
    --expect-fail-message "$fixture_expected_message"
else
  skipped=$((skipped + 1))
  echo "── case skipped: no usable Docker daemon, so the Testcontainers fixture cannot start."
  echo "   This is the case that proves a real DEFENDED verdict. Re-run where Docker works"
  echo "   before trusting a pass from this script."
fi

# ── Case 4: gate 1 still guards a stale pattern ───────────────────────────────────────
# The hook's original reason for existing: a search pattern that no longer matches (formatters rewrite
# source constantly) must abort before anything runs, never produce a pass.
expect "a pattern that matches nothing aborts before running any test" \
  1 "pattern matched 0 times" \
  "$hook" --repo "$repo_path" --module "$fixture_module" --file "$fixture_file" \
  --find 'this text is deliberately absent from the source' --replace 'x' \
  --test "${fixture_class}\$${fixture_nested}#${fixture_method}" \
  --expect-fail-message "$fixture_expected_message"

# ── Case 5: a Spring-context test without --expect-fail-message must be refused ────────
# A context test can fail because the assertion fired or because the mutation broke context startup, and
# those are indistinguishable by exit code. The fixture is a @DataJpaTest, so omitting the expected message
# must be refused rather than yielding a verdict that cannot mean what it says.
expect "a Spring-context test without --expect-fail-message is refused" \
  1 "--expect-fail-message is required" \
  "$hook" --repo "$repo_path" --module "$fixture_module" --file "$fixture_file" \
  --find "$fixture_find" --replace "$fixture_replace" \
  --test "${fixture_class}\$${fixture_nested}#${fixture_method}"

# ── Case 6: a JDK that is not there is named as such, not as a selector problem ────────
# The regression this guards: the hook defaulted --java-home to /opt/jdk25, a path nothing creates.
# Maven then died on the enforcer having run nothing, and gate 3 reported "no tests actually ran" —
# which reads as a bad --test selector and sends the next person to debug a selector that is fine.
# Gate 0 must catch it first, and must say the JDK is the problem.
expect "a --java-home that does not exist aborts naming the JDK, not the selector" \
  1 "no runnable bin/javac" \
  "$hook" --repo "$repo_path" --module "$fixture_module" --file "$fixture_file" \
  --find "$fixture_find" --replace "$fixture_replace" \
  --java-home /nonexistent/jdk25 \
  --test "${fixture_class}\$${fixture_nested}#${fixture_method}" \
  --expect-fail-message "$fixture_expected_message"

# ── Case 7: the wrong Java is a distinct, named failure ───────────────────────────────
# The other half. A JDK that exists but is too old fails on the enforcer with output that looks
# nothing like a version problem, so gate 0 checks the version rather than letting Maven imply it.
#
# Driven against a stub rather than a real JDK found on the machine. An earlier revision hunted
# /usr/lib/jvm for a non-25 JDK and skipped when it found none — but the hook's candidates also
# include $JAVA_HOME, $HOME/.jdk and /opt, so on a host whose only older JDK sits in one of those
# the case skipped while a perfectly good subject was sitting there, and the suite still printed
# PASS (PARTIAL). A skip that depends on where someone happens to have installed a JDK is not a
# precondition, it is a hole. Gate 0 reads nothing but `javac -version`, so a stub exercises the
# real contract, always runs, and says the same thing on every machine.
wrong_major_jdk="$(mktemp -d)"
mkdir -p "$wrong_major_jdk/bin"
cat > "$wrong_major_jdk/bin/javac" <<'STUB'
#!/bin/sh
echo "javac 21.0.10"
STUB
chmod +x "$wrong_major_jdk/bin/javac"
trap 'rm -rf "$wrong_major_jdk"' EXIT

expect "a --java-home on the wrong major aborts naming the version" \
  1 "but the backend enforcer requires Java 25" \
  "$hook" --repo "$repo_path" --module "$fixture_module" --file "$fixture_file" \
  --find "$fixture_find" --replace "$fixture_replace" \
  --java-home "$wrong_major_jdk" \
  --test "${fixture_class}\$${fixture_nested}#${fixture_method}" \
  --expect-fail-message "$fixture_expected_message"

echo
if [[ "$failures" == "0" ]]; then
  if [[ "$skipped" == "0" ]]; then
    echo "SELFTEST PASS | ${case_number} cases, the hook behaves as specified"
  else
    # Never just "PASS": a skipped case is an untested claim, and the whole point of this script is
    # that an untested claim is not allowed to read as a proven one.
    echo "SELFTEST PASS (PARTIAL) | ${case_number} cases ran, ${skipped} skipped for missing"
    echo "  prerequisites (listed above). Every case that ran behaves as specified."
  fi
  exit 0
fi
echo "SELFTEST FAIL | ${failures} of ${case_number} cases misbehaved" >&2
echo "  The mutation hook is only worth running if it cannot lie. Fix it before trusting any" >&2
echo "  DEFENDED/UNDEFENDED result produced by it." >&2
exit 1
