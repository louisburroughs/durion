#!/usr/bin/env zsh
emulate -L zsh
setopt errexit
setopt nounset
setopt pipefail

# Accounting issue loader/linker (jq-free) with optional preflight.
#
# v11 fixes:
# - Fix JSONDecodeError when resolving capability parent numbers:
#   Previous versions fed the Python program via stdin (here-doc), leaving no stdin for JSON.
#   Now we pass JSON via argv (python reads json.loads(sys.argv[2])).
#
# Usage:
#   ./load_and_link_accounting_with_preflight_v11.zsh -r owner/repo -f accounting-stories.json --dry-run
#   ./load_and_link_accounting_with_preflight_v11.zsh -r owner/repo -f accounting-epic.json --skip-preflight

usage() {
  print "Usage: $0 -r owner/repo -f issues.json [-p project-number] [-o project-owner] [--dry-run] [--preflight-only] [--skip-preflight]" >&2
  exit 1
}

REPO=""
FILE=""
PROJECT_NUM=""
PROJECT_OWNER=""
DRY_RUN=0
PREFLIGHT_ONLY=0
SKIP_PREFLIGHT=0

for arg in "$@"; do
  [[ "$arg" == "--dry-run" ]] && DRY_RUN=1
  [[ "$arg" == "--preflight-only" ]] && PREFLIGHT_ONLY=1
  [[ "$arg" == "--skip-preflight" ]] && SKIP_PREFLIGHT=1
done

ARGS=()
for arg in "$@"; do
  case "$arg" in
    --dry-run|--preflight-only|--skip-preflight) ;;
    *) ARGS+=("$arg") ;;
  esac
done
set -- "${ARGS[@]}"

while getopts ":r:f:p:o:" opt; do
  case "${opt}" in
    r) REPO="${OPTARG}" ;;
    f) FILE="${OPTARG}" ;;
    p) PROJECT_NUM="${OPTARG}" ;;
    o) PROJECT_OWNER="${OPTARG}" ;;
    *) usage ;;
  esac
done

[[ -z "${REPO}" || -z "${FILE}" ]] && usage
[[ ! -f "${FILE}" ]] && { print "File not found: ${FILE}" >&2; exit 2 }

command -v gh >/dev/null || { print "gh not found" >&2; exit 2 }
command -v python3 >/dev/null || { print "python3 not found" >&2; exit 2 }
gh auth status >/dev/null || { print "Not authenticated. Run: gh auth login" >&2; exit 2 }

OWNER="${REPO%%/*}"
REPO_NAME="${REPO##*/}"
[[ -z "${PROJECT_OWNER}" ]] && PROJECT_OWNER="${OWNER}"

say() { print -- "$@" }

say "== Accounting loader v11 =="
say "Repo: ${REPO}"
say "File: ${FILE}"
say "Flags: DRY_RUN=${DRY_RUN} PREFLIGHT_ONLY=${PREFLIGHT_ONLY} SKIP_PREFLIGHT=${SKIP_PREFLIGHT}"

typeset -A CAP_TITLES
CAP_TITLES[Events]="[CAP] Ingest Accounting Events (Cross-Module)"
CAP_TITLES[CoA]="[CAP] Maintain Chart of Accounts and Posting Categories"
CAP_TITLES[Categories]="[CAP] Maintain Chart of Accounts and Posting Categories"
CAP_TITLES[Mapping]="[CAP] Maintain Chart of Accounts and Posting Categories"
CAP_TITLES[GL]="[CAP] Post Journal Entries to the General Ledger"
CAP_TITLES[AR]="[CAP] Accounts Receivable (Invoice → Cash Application)"
CAP_TITLES[AP]="[CAP] Accounts Payable (Bill → Payment)"
CAP_TITLES[Close]="[CAP] Period Close, Adjustments, and Reporting"
CAP_TITLES[Adjustments]="[CAP] Period Close, Adjustments, and Reporting"
CAP_TITLES[Reporting]="[CAP] Period Close, Adjustments, and Reporting"
CAP_TITLES[Audit]="[CAP] Reconciliation, Audit, and Controls"
CAP_TITLES[Reconciliation]="[CAP] Reconciliation, Audit, and Controls"
CAP_TITLES[Controls]="[CAP] Reconciliation, Audit, and Controls"

parent_key_for_story_title() {
  local t="$1"
  if [[ "$t" == \[STORY\]\ Events:* ]]; then echo "Events"
  elif [[ "$t" == \[STORY\]\ CoA:* ]]; then echo "CoA"
  elif [[ "$t" == \[STORY\]\ Categories:* ]]; then echo "Categories"
  elif [[ "$t" == \[STORY\]\ Mapping:* ]]; then echo "Mapping"
  elif [[ "$t" == \[STORY\]\ GL:* ]]; then echo "GL"
  elif [[ "$t" == \[STORY\]\ AR:* ]]; then echo "AR"
  elif [[ "$t" == \[STORY\]\ AP:* ]]; then echo "AP"
  elif [[ "$t" == \[STORY\]\ Close:* ]]; then echo "Close"
  elif [[ "$t" == \[STORY\]\ Adjustments:* ]]; then echo "Adjustments"
  elif [[ "$t" == \[STORY\]\ Reporting:* ]]; then echo "Reporting"
  elif [[ "$t" == \[STORY\]\ Audit:* ]]; then echo "Audit"
  elif [[ "$t" == \[STORY\]\ Reconciliation:* ]]; then echo "Reconciliation"
  elif [[ "$t" == \[STORY\]\ Controls:* ]]; then echo "Controls"
  else echo ""
  fi
}

NEEDS_LINKING="$(python3 - <<'PY' "${FILE}"
import json,sys
data=json.load(open(sys.argv[1],"r",encoding="utf-8"))
print("1" if any((x.get("title","").startswith("[STORY]") for x in data)) else "0")
PY
)"
[[ "${NEEDS_LINKING}" == "1" ]] && say "Detected stories in input: linking ENABLED" || say "No stories detected: linking DISABLED (epic/capability import only)"

preflight_validate_autolink() {
  local repo="$1"
  local file="$2"

  if [[ "${NEEDS_LINKING}" != "1" ]]; then
    say "ℹ️  Pre-flight skipped (no stories in file)"
    return 0
  fi

  say "🔍 Pre-flight auto-link validation for ${file} in ${repo}"
  say "------------------------------------------------------"

  local caps_json
  caps_json="$(gh issue list --repo "$repo" --state all --search 'in:title "[CAP]"' --json title --limit 200 2>/dev/null || true)"
  [[ -z "${caps_json}" ]] && caps_json="[]"

  if [[ "${caps_json}" == "[]" ]]; then
    say "❌ No capability issues found in repo by title search: in:title \"[CAP]\"" >&2
    say "   Fix: import/create capability issues first, OR import a file that includes them before stories." >&2
    return 2
  fi

  python3 - "$file" "$caps_json" <<'PY'
import json, sys
file_path = sys.argv[1]
caps = {x["title"] for x in json.loads(sys.argv[2])}
issues = json.load(open(file_path, "r", encoding="utf-8"))

CAP = {
  "Events": "[CAP] Ingest Accounting Events (Cross-Module)",
  "CoA": "[CAP] Maintain Chart of Accounts and Posting Categories",
  "Categories": "[CAP] Maintain Chart of Accounts and Posting Categories",
  "Mapping": "[CAP] Maintain Chart of Accounts and Posting Categories",
  "GL": "[CAP] Post Journal Entries to the General Ledger",
  "AR": "[CAP] Accounts Receivable (Invoice → Cash Application)",
  "AP": "[CAP] Accounts Payable (Bill → Payment)",
  "Close": "[CAP] Period Close, Adjustments, and Reporting",
  "Adjustments": "[CAP] Period Close, Adjustments, and Reporting",
  "Reporting": "[CAP] Period Close, Adjustments, and Reporting",
  "Audit": "[CAP] Reconciliation, Audit, and Controls",
  "Reconciliation": "[CAP] Reconciliation, Audit, and Controls",
  "Controls": "[CAP] Reconciliation, Audit, and Controls",
}
def pk(t: str) -> str:
    if t.startswith("[STORY] Events:"): return "Events"
    if t.startswith("[STORY] CoA:"): return "CoA"
    if t.startswith("[STORY] Categories:"): return "Categories"
    if t.startswith("[STORY] Mapping:"): return "Mapping"
    if t.startswith("[STORY] GL:"): return "GL"
    if t.startswith("[STORY] AR:"): return "AR"
    if t.startswith("[STORY] AP:"): return "AP"
    if t.startswith("[STORY] Close:"): return "Close"
    if t.startswith("[STORY] Adjustments:"): return "Adjustments"
    if t.startswith("[STORY] Reporting:"): return "Reporting"
    if t.startswith("[STORY] Audit:"): return "Audit"
    if t.startswith("[STORY] Reconciliation:"): return "Reconciliation"
    if t.startswith("[STORY] Controls:"): return "Controls"
    return ""

errors = 0
for i in issues:
    t = i.get("title","")
    if not t.startswith("[STORY]"):
        continue
    p = pk(t)
    if not p:
        print(f"❌ NO PARENT KEY: {t}")
        errors += 1
        continue
    expected = CAP[p]
    if expected not in caps:
        print(f"❌ CAP NOT FOUND: {t} -> {expected}")
        errors += 1
    else:
        print(f"✅ {t} -> {expected}")

print("------------------------------------------------------")
if errors:
    print(f"❌ Pre-flight FAILED with {errors} error(s)")
    sys.exit(2)
print("✅ Pre-flight PASSED: all stories will auto-link correctly")
PY
}

if [[ "${SKIP_PREFLIGHT}" -eq 0 ]]; then
  preflight_validate_autolink "${REPO}" "${FILE}"
  pf_rc="$?"
  if [[ "${pf_rc}" -ne 0 ]]; then exit "${pf_rc}"; fi
  if [[ "${PREFLIGHT_ONLY}" -eq 1 ]]; then exit 0; fi
else
  if [[ "${PREFLIGHT_ONLY}" -eq 1 ]]; then
    say "ERROR: --preflight-only cannot be used with --skip-preflight" >&2
    exit 2
  fi
fi

json_array_has_name() {
  local wanted="$1"
  python3 -c 'import json,sys; wanted=sys.argv[1]; data=json.load(sys.stdin); print("1" if any((x.get("name")==wanted) for x in data) else "0")' "${wanted}"
}

ensure_label() {
  local label="$1"
  local labels_json
  labels_json="$(gh label list --repo "${REPO}" --limit 400 --json name 2>/dev/null || true)"
  [[ -z "${labels_json}" ]] && labels_json="[]"

  if [[ "$(print -r -- "${labels_json}" | json_array_has_name "${label}")" == "1" ]]; then
    return 0
  fi

  if [[ "${DRY_RUN}" -eq 1 ]]; then
    say "DRY-RUN: label missing (would create): ${label}"
    return 0
  fi
  say "Creating label: ${label}"
  gh label create "${label}" --repo "${REPO}" --color "BFDADC" --description "Kiro-related work" >/dev/null
}

issue_number_by_exact_title() {
  local wanted="$1"
  local issues_json
  issues_json="$(gh issue list --repo "${REPO}" --state all --search "in:title \"${wanted}\"" --json number,title --limit 50 2>/dev/null || true)"
  [[ -z "${issues_json}" ]] && issues_json="[]"

  # Pass JSON via argv, not stdin (stdin is used for the Python program in older patterns)
  python3 -c 'import json,sys; wanted=sys.argv[1]; data=json.loads(sys.argv[2]);
for x in data:
  if x.get("title")==wanted:
    print(x.get("number","")); raise SystemExit
print("")' "${wanted}" "${issues_json}"
}

issue_id_by_number() {
  local number="$1"
  gh api "repos/${OWNER}/${REPO_NAME}/issues/${number}" --jq '.id'
}

attach_subissue() {
  local parent_number="$1"
  local child_number="$2"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    say "  + DRY-RUN: would link as sub-issue: #${child_number} -> parent #${parent_number}"
    return 0
  fi
  local child_id
  child_id="$(issue_id_by_number "${child_number}")"
  say "  + link as sub-issue: #${child_number} (id=${child_id}) -> parent #${parent_number}"
  gh api -X POST "repos/${OWNER}/${REPO_NAME}/issues/${parent_number}/sub_issues" \
    -F "sub_issue_id=${child_id}" >/dev/null
}

add_to_project() {
  local issue_url="$1"
  [[ -z "${PROJECT_NUM}" ]] && return 0
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    say "  + DRY-RUN: would add to project #${PROJECT_NUM} (owner: ${PROJECT_OWNER})"
    return 0
  fi
  say "  + add to project #${PROJECT_NUM} (owner: ${PROJECT_OWNER})"
  gh project item-add "${PROJECT_NUM}" --owner "${PROJECT_OWNER}" --url "${issue_url}" >/dev/null
}

ensure_label "kiro"

typeset -A CAP_NUM
if [[ "${NEEDS_LINKING}" == "1" ]]; then
  say "Resolving capability parent issue numbers..."
  for key cap_title in ${(kv)CAP_TITLES}; do
    num="$(issue_number_by_exact_title "${cap_title}")"
    if [[ -z "${num}" ]]; then
      say "ERROR: Could not find capability issue with exact title: ${cap_title}" >&2
      say "       Fix: create/import capabilities first, then rerun story import." >&2
      exit 3
    fi
    CAP_NUM[$key]="${num}"
  done
fi

count="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1],"r",encoding="utf-8"))))' "${FILE}")"
say "Creating issues from ${FILE} (${count} items) in ${REPO}..."

python3 - <<'PY' "${FILE}" | while IFS=$'\t' read -r title_b64 labels_b64 body_path; do
import json, sys, base64, tempfile, os
path = sys.argv[1]

def b64(s: str) -> str:
  return base64.b64encode(s.encode("utf-8")).decode("ascii")

data = json.load(open(path, "r", encoding="utf-8"))
for obj in data:
  title = obj.get("title","")
  body = obj.get("body","")
  labels = obj.get("labels") or []
  labels = sorted(set(labels + ["kiro"]))
  labels_csv = ",".join(labels)

  fd, body_path = tempfile.mkstemp(prefix="kiro_body_", suffix=".md")
  with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as out:
    out.write(body)

  print(b64(title), b64(labels_csv), body_path, sep="\t")
PY

  title="$(python3 -c 'import base64,sys; print(base64.b64decode(sys.argv[1]).decode("utf-8"))' "${title_b64}")"
  labels_csv="$(python3 -c 'import base64,sys; print(base64.b64decode(sys.argv[1]).decode("utf-8"))' "${labels_b64}")"

  say "Creating: ${title}"

  if [[ "${DRY_RUN}" -eq 1 ]]; then
    say "  -> DRY-RUN: would create issue with labels: ${labels_csv}"
    rm -f "${body_path}" || true
    continue
  fi

  issue_url="$(
    gh issue create \
      --repo "${REPO}" \
      --title "${title}" \
      --body-file "${body_path}" \
      --label "${labels_csv}" \
    | tail -n 1
  )"

  rm -f "${body_path}" || true

  say "  -> ${issue_url}"
  add_to_project "${issue_url}" || true

  if [[ "${NEEDS_LINKING}" == "1" && "${title}" == \[STORY\]* ]]; then
    pkey="$(parent_key_for_story_title "${title}")"
    if [[ -z "${pkey}" ]]; then
      say "  ! WARNING: no parentKey match for story title (not linked): ${title}"
      continue
    fi
    parent_number="${CAP_NUM[$pkey]}"
    child_number="${issue_url##*/}"
    attach_subissue "${parent_number}" "${child_number}"
  fi
done

say "Done."
