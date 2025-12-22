#!/usr/bin/env zsh
emulate -L zsh

setopt errexit
setopt nounset
setopt pipefail

# Fully jq-free loader/linker (v4):
# Fixes a critical bug in v3: python scripts were provided via stdin (heredoc),
# which prevented reading JSON from stdin in piped contexts.
#
# Usage:
#   ./load_and_link_stories_v4.zsh --dry-run -r owner/repo -f stories.json [-p project-number] [-o project-owner]

usage() {
  print "Usage: $0 -r owner/repo -f stories.json [-p project-number] [-o project-owner] [--dry-run]" >&2
  exit 1
}

REPO=""
FILE=""
PROJECT_NUM=""
PROJECT_OWNER=""
DRY_RUN=0

for arg in "$@"; do
  [[ "$arg" == "--dry-run" ]] && DRY_RUN=1
done

ARGS=()
for arg in "$@"; do
  [[ "$arg" == "--dry-run" ]] && continue
  ARGS+=("$arg")
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

# ----- Helpers (jq-free) -----

json_array_has_name() {
  # Reads JSON array of objects from stdin, checks if any object has "name" == $1
  local wanted="$1"
  python3 -c 'import json,sys; wanted=sys.argv[1]; data=json.load(sys.stdin); print("1" if any((x.get("name")==wanted) for x in data) else "0")' "${wanted}"
}

json_array_find_issue_number_by_title() {
  # Reads JSON array of objects from stdin, prints .number for the first object with .title == $1, else blank
  local wanted="$1"
  python3 -c 'import json,sys; wanted=sys.argv[1]; data=json.load(sys.stdin); 
for x in data:
  if x.get("title")==wanted:
    print(x.get("number",""))
    sys.exit(0)
print("")' "${wanted}"
}

ensure_label() {
  local label="$1"
  local labels_json
  labels_json="$(gh label list --repo "${REPO}" --limit 400 --json name 2>/dev/null || true)"
  [[ -z "${labels_json}" ]] && { print "ERROR: gh label list returned empty output. Check repo access: ${REPO}" >&2; exit 2 }

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

ensure_label "kiro"

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

issue_number_by_exact_title() {
  local wanted="$1"
  local issues_json
  issues_json="$(gh issue list --repo "${REPO}" --state all --search "\"${wanted}\" in:title" --json number,title --limit 50 2>/dev/null || true)"
  [[ -z "${issues_json}" ]] && { print "ERROR: gh issue list returned empty output. Check repo access: ${REPO}" >&2; exit 2 }
  print -r -- "${issues_json}" | json_array_find_issue_number_by_title "${wanted}"
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
    -F sub_issue_id="${child_id}" >/dev/null
}

# Capability titles MUST match your 6 capability issues.
typeset -A CAP_TITLES
CAP_TITLES[Estimate]="[CAP] Create and Manage Estimates"
CAP_TITLES[Approval]="[CAP] Capture Customer Approval"
CAP_TITLES[Promotion]="[CAP] Promote Estimate to Workorder"
CAP_TITLES[Execution]="[CAP] Execute Workorder (Parts & Labor)"
CAP_TITLES[Completion]="[CAP] Complete Workorder"
CAP_TITLES[Invoicing]="[CAP] Convert Workorder to Invoice"

typeset -A CAP_NUM
for key title in ${(kv)CAP_TITLES}; do
  num="$(issue_number_by_exact_title "${title}")"
  if [[ -z "${num}" ]]; then
    say "ERROR: Could not find capability issue with exact title: ${title}" >&2
    say "       Fix: update CAP_TITLES or rename your capability issue to match." >&2
    exit 3
  fi
  CAP_NUM[$key]="${num}"
  say "Capability parent: ${key} -> #${num} (${title})"
done

# Count stories (python)
count="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1],"r",encoding="utf-8"))))' "${FILE}")"
say "Creating and linking ${count} story issues in ${REPO}..."

# Stream story records as tab-separated fields:
# parentKey<TAB>title_b64<TAB>labels_csv_b64<TAB>body_file_path
python3 - <<'PY' "${FILE}" | while IFS=$'\t' read -r pkey title_b64 labels_b64 body_path; do
import json, sys, base64, tempfile, os
path = sys.argv[1]

def b64(s: str) -> str:
  return base64.b64encode(s.encode("utf-8")).decode("ascii")

with open(path, "r", encoding="utf-8") as f:
  data = json.load(f)

def parent_key(title: str) -> str:
  if title.startswith("[STORY] Estimate:"): return "Estimate"
  if title.startswith("[STORY] Approval:"): return "Approval"
  if title.startswith("[STORY] Promotion:"): return "Promotion"
  if title.startswith("[STORY] Execution:"): return "Execution"
  if title.startswith("[STORY] Completion:"): return "Completion"
  if title.startswith("[STORY] Invoicing:"): return "Invoicing"
  return ""

for obj in data:
  title = obj.get("title","")
  body = obj.get("body","")
  labels = obj.get("labels") or []
  labels = sorted(set(labels + ["kiro"]))
  labels_csv = ",".join(labels)

  pk = parent_key(title)

  fd, body_path = tempfile.mkstemp(prefix="kiro_body_", suffix=".md")
  with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as out:
    out.write(body)

  print(pk, b64(title), b64(labels_csv), body_path, sep="\t")
PY

  if [[ -z "${pkey}" ]]; then
    say "WARNING: Could not determine parent capability for a story (skipping one item)." >&2
    rm -f "${body_path}" || true
    continue
  fi

  parent_number="${CAP_NUM[$pkey]}"

  title="$(python3 -c 'import base64,sys; print(base64.b64decode(sys.argv[1]).decode("utf-8"))' "${title_b64}")"
  labels_csv="$(python3 -c 'import base64,sys; print(base64.b64decode(sys.argv[1]).decode("utf-8"))' "${labels_b64}")"

  say "Creating: ${title}"

  if [[ "${DRY_RUN}" -eq 1 ]]; then
    say "  -> DRY-RUN: would create issue with labels: ${labels_csv}"
    say "  + DRY-RUN: would link under ${pkey} (#${parent_number})"
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

  issue_number="${issue_url##*/}"
  attach_subissue "${parent_number}" "${issue_number}"
done

say "Done."
