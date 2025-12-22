#!/usr/bin/env zsh
emulate -L zsh

setopt errexit
setopt nounset
setopt pipefail

# Bulk load issues from a JSON array file
#
# Requirements:
#   - GitHub CLI authenticated: gh auth status
#   - jq installed
#
# Usage:
#   ./load_issues.zsh -r owner/repo -f issues.json [-p project-number]

usage() {
  print "Usage: $0 -r owner/repo -f issues.json [-p project-number]" >&2
  print "  -r  GitHub repo in owner/repo form (required)" >&2
  print "  -f  Path to JSON array file (required)" >&2
  print "  -p  Optional GitHub Project v2 number to add issues to (owner-scoped)" >&2
  exit 1
}

REPO=""
FILE=""
PROJECT_NUM=""

while getopts ":r:f:p:" opt; do
  case "${opt}" in
    r) REPO="${OPTARG}" ;;
    f) FILE="${OPTARG}" ;;
    p) PROJECT_NUM="${OPTARG}" ;;
    *) usage ;;
  esac
done

[[ -z "${REPO}" || -z "${FILE}" ]] && usage
[[ ! -f "${FILE}" ]] && { print "File not found: ${FILE}" >&2; exit 2 }

command -v gh >/dev/null || { print "gh not found" >&2; exit 2 }
command -v jq >/dev/null || { print "jq not found" >&2; exit 2 }

# Ensure authentication
gh auth status >/dev/null || {
  print "Not authenticated. Run: gh auth login" >&2
  exit 2
}

# Ensure the "kiro" label exists
if ! gh label list --repo "${REPO}" --limit 200 | awk '{print $1}' | grep -qx "kiro"; then
  print "Creating label: kiro"
  gh label create "kiro" \
    --repo "${REPO}" \
    --color "BFDADC" \
    --description "Imported / Kiro-related work"
else
  print "Label exists: kiro"
fi

add_to_project() {
  local issue_url="$1"
  if [[ -n "${PROJECT_NUM}" ]]; then
    local owner="${REPO%%/*}"
    gh project item-add "${PROJECT_NUM}" \
      --owner "${owner}" \
      --url "${issue_url}" >/dev/null
  fi
}

count=$(jq 'length' "${FILE}")
print "Importing ${count} issues into ${REPO}..."

jq -c '.[]' "${FILE}" | while read -r item; do
  title=$(jq -r '.title' <<< "${item}")
  body=$(jq -r '.body' <<< "${item}")

  labels_csv=$(jq -r '
    [((.labels // []) + ["kiro"]) | unique[]] | join(",")
  ' <<< "${item}")

  tmp_body="$(mktemp)"
  print -r -- "${body}" > "${tmp_body}"

  print "Creating: ${title}"
  issue_url=$(
  gh issue create \
    --repo "${REPO}" \
    --title "${title}" \
    --body-file "${tmp_body}" \
    --label "${labels_csv}" |
    tail -n 1
 )

  rm -f "${tmp_body}"

  print "  -> ${issue_url}"
  add_to_project "${issue_url}" || true
done

print "Done."
