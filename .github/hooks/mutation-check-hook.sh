#!/usr/bin/env bash
set -euo pipefail

# Mutation-check hook: prove a test actually defends the guarantee it claims to.
#
# WHY THIS EXISTS
# A mutation check is only evidence if the mutation was really applied. During CAP-317 a hand-rolled
# mutation script reported success while its search pattern silently failed to match (the file had been
# reformatted by spotless). The test then PASSED, which would have been recorded as "the guard is
# proven" when nothing had been tested at all — the same "passes for the wrong reason" defect class the
# tests themselves exist to catch.
#
# This hook makes the three things that made that possible impossible to skip:
#   1. The search pattern must match EXACTLY ONCE, or the run aborts before touching anything.
#   2. The file must actually differ after mutation, or the run aborts.
#   3. The test must FAIL under mutation. A pass means the guarantee is undefended, and the hook
#      reports that as the finding it is rather than as success.
# The original file is restored unconditionally, including on error or interrupt, and the restoration is
# itself verified byte-for-byte.
#
# Usage:
#   ./.github/hooks/mutation-check-hook.sh \
#     --repo /abs/path/to/durion-positivity-backend \
#     --file pos-supplier/src/main/java/.../Foo.java \
#     --find 'the exact source text to remove or change' \
#     --replace 'what to put in its place (may be empty)' \
#     --module pos-supplier \
#     --test 'FooTest#theGuaranteeUnderTest' \
#     [--expect-fail-message 'substring the failure output must contain']
#
# Exit codes: 0 = mutation applied AND the test failed (the guarantee is defended)
#             1 = misuse, pattern problem, or the test PASSED under mutation (guarantee undefended)

repo_path=""
target_file=""
find_text=""
replace_text=""
module=""
test_selector=""
expect_message=""
# The backend enforcer requires Java 25. An inherited JAVA_HOME pointing at an older JDK makes every
# run fail on the enforcer, which the gates below would report as "no tests ran" rather than a result.
java_home="${MUTATION_CHECK_JAVA_HOME:-/opt/jdk25}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) repo_path="$2"; shift 2 ;;
    --file) target_file="$2"; shift 2 ;;
    --find) find_text="$2"; shift 2 ;;
    --replace) replace_text="$2"; shift 2 ;;
    --module) module="$2"; shift 2 ;;
    --test) test_selector="$2"; shift 2 ;;
    --expect-fail-message) expect_message="$2"; shift 2 ;;
    --java-home) java_home="$2"; shift 2 ;;
    -h|--help) sed -n '3,30p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

for required in repo_path target_file find_text module test_selector; do
  if [[ -z "${!required}" ]]; then
    echo "MUTATION CHECK MISUSE | --${required//_/-} is required" >&2
    exit 1
  fi
done

cd "$repo_path"
if [[ ! -f "$target_file" ]]; then
  echo "MUTATION CHECK MISUSE | file not found: $target_file" >&2
  exit 1
fi

backup="$(mktemp)"
cp "$target_file" "$backup"

restore() {
  cp "$backup" "$target_file"
  if ! cmp -s "$backup" "$target_file"; then
    echo "MUTATION CHECK FATAL | could not restore $target_file — restore it from git before continuing" >&2
    exit 1
  fi
  rm -f "$backup"
}
# Restore on ANY exit path, including a failed test or an interrupt: a mutation left in the tree is
# far worse than a missing check.
trap restore EXIT INT TERM

# ── Gate 1: the pattern must match exactly once ────────────────────────────────────────
occurrences=$(FIND="$find_text" python3 -c '
import os, sys
needle = os.environ["FIND"]
with open(sys.argv[1], encoding="utf-8") as handle:
    print(handle.read().count(needle))
' "$target_file")

if [[ "$occurrences" != "1" ]]; then
  echo "MUTATION CHECK ABORTED | pattern matched ${occurrences} times, expected exactly 1."
  echo "  This is the failure mode this hook exists for: a non-matching pattern would have left the"
  echo "  source unchanged, the test would have PASSED, and the guarantee would have been recorded as"
  echo "  proven without being tested. Re-read the current file text (formatters rewrite it)."
  exit 1
fi

# ── Apply ─────────────────────────────────────────────────────────────────────────────
FIND="$find_text" REPLACE="$replace_text" python3 -c '
import os, sys
needle, replacement = os.environ["FIND"], os.environ["REPLACE"]
path = sys.argv[1]
with open(path, encoding="utf-8") as handle:
    text = handle.read()
with open(path, "w", encoding="utf-8") as handle:
    handle.write(text.replace(needle, replacement, 1))
' "$target_file"

# ── Gate 2: the file must actually have changed ────────────────────────────────────────
if cmp -s "$backup" "$target_file"; then
  echo "MUTATION CHECK ABORTED | file is byte-identical after mutation; nothing was changed." >&2
  exit 1
fi
echo "Mutation applied to $target_file:"
diff <(cat "$backup") "$target_file" | sed 's/^/    /' | head -20 || true
echo

# ── Gate 3: the test must fail ────────────────────────────────────────────────────────
output_file="$(mktemp)"
set +e
JAVA_HOME="$java_home" ./mvnw -o -pl "$module" test \
  -Dtest="$test_selector" -DfailIfNoSpecifiedTests=false > "$output_file" 2>&1
test_exit=$?
set -e

# The COUNT must be non-zero, not merely present. Maven prints a "Tests run: 0, Failures: 0" summary
# even when a selector matched nothing, so grepping for the line's existence accepted a run in which
# nothing executed — and a run with no tests neither fails nor proves anything, which gate 3 would then
# report as UNDEFENDED. That is this hook's own failure mode wearing a different hat; it was hit for
# real on a `Class#method` selector naming a method inside a @Nested class (surefire needs
# `Class$Nested#method`).
executed=$(grep -oE "Tests run: [0-9]+" "$output_file" | grep -oE "[0-9]+$" | sort -rn | head -1 || true)
executed="${executed:-0}"
if [[ "$executed" == "0" ]]; then
  echo "MUTATION CHECK ABORTED | no tests actually ran for selector '$test_selector'." >&2
  echo "  Maven reported 'Tests run: 0'. The selector matched nothing, so NOTHING was proven either" >&2
  echo "  way. For a method in a @Nested class use 'Outer\$Nested#method'." >&2
  grep -E "Tests run:|ERROR|BUILD" "$output_file" | head -10 >&2 || true
  rm -f "$output_file"
  exit 1
fi

if [[ "$test_exit" == "0" ]]; then
  echo "MUTATION CHECK RESULT: UNDEFENDED"
  echo "  '$test_selector' PASSED with the guarantee removed, so it does not actually test it."
  echo "  Treat this as a finding: the test needs strengthening, not the mutation retrying."
  rm -f "$output_file"
  exit 1
fi

if [[ -n "$expect_message" ]] && ! grep -qF "$expect_message" "$output_file"; then
  echo "MUTATION CHECK RESULT: FAILED FOR THE WRONG REASON"
  echo "  The test failed, but its output does not contain: $expect_message"
  echo "  It may be failing on setup or an unrelated assertion rather than the guarantee."
  grep -E "expected|but was|Tests run:" "$output_file" | head -10 | sed 's/^/    /'
  rm -f "$output_file"
  exit 1
fi

echo "MUTATION CHECK RESULT: DEFENDED"
grep -E "Tests run:|expected|but was" "$output_file" | head -6 | sed 's/^/    /'
rm -f "$output_file"
exit 0
