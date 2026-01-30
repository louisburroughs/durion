#!/usr/bin/env bash
set -euo pipefail

# Generates business-rule documents for each domain export by calling OpenAI.
#
# Inputs:  domain-* files in $INPUT_DIR
# Outputs: $BACKEND_DIR/<pos-module>/.business-rules/<domain>/{AGENT_GUIDE.md,STORY_VALIDATION_CHECKLIST.md}
#
# Requirements:
# - curl, jq
# - OPENAI_API_KEY env var

usage() {
  cat <<'USAGE'
Usage:
  generate-domain-business-rules.sh [options]

Options:
  --input-dir DIR        Directory containing domain-*.txt (default: /home/louisb/Projects/durion/scripts/output)
  --backend-dir DIR      durion-positivity-backend root (default: /home/louisb/Projects/durion-positivity-backend)
  --model MODEL          OpenAI model (default: OPENAI_MODEL env or gpt-4.1-mini)
  --api-base URL         OpenAI API base URL (default: OPENAI_API_BASE env or https://api.openai.com/v1)
  --domain NAME          Only process a single domain key (e.g., inventory)
  --max-chars N          Max chars from input file to send (default: 120000)
  --dry-run              Print what would be done, do not call API
  -h, --help             Show this help

Env vars:
  OPENAI_API_KEY         Required
  OPENAI_MODEL           Optional (overridden by --model)
  OPENAI_API_BASE        Optional (overridden by --api-base)
  OPENAI_ORG             Optional (sent as OpenAI-Organization header if set)

Notes:
  - This script intentionally writes outputs under .business-rules/<domain>/ to avoid collisions
    when multiple domains map to the same pos-* module.
USAGE
}

INPUT_DIR="/home/louisb/Projects/durion/scripts/output"
BACKEND_DIR="/home/louisb/Projects/durion-positivity-backend"
OPENAI_MODEL="${OPENAI_MODEL:-gpt-5.2}"
OPENAI_API_BASE="${OPENAI_API_BASE:-https://api.openai.com/v1}"
ONLY_DOMAIN=""
MAX_CHARS="120000"
DRY_RUN="0"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input-dir) INPUT_DIR="$2"; shift 2;;
    --backend-dir) BACKEND_DIR="$2"; shift 2;;
    --model) OPENAI_MODEL="$2"; shift 2;;
    --api-base) OPENAI_API_BASE="$2"; shift 2;;
    --domain) ONLY_DOMAIN="$2"; shift 2;;
    --max-chars) MAX_CHARS="$2"; shift 2;;
    --dry-run) DRY_RUN="1"; shift 1;;
    -h|--help) usage; exit 0;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2;;
  esac
done

require_bin() {
  local bin="$1"
  if ! command -v "$bin" >/dev/null 2>&1; then
    echo "Missing required dependency: $bin" >&2
    exit 3
  fi
}

require_env() {
  local var="$1"
  if [[ -z "${!var:-}" ]]; then
    echo "Missing required env var: $var" >&2
    exit 4
  fi
}

require_bin curl
require_bin jq
if [[ "$DRY_RUN" != "1" ]]; then
  require_env OPENAI_API_KEY
fi

if [[ ! -d "$INPUT_DIR" ]]; then
  echo "Input dir not found: $INPUT_DIR" >&2
  exit 5
fi
if [[ ! -d "$BACKEND_DIR" ]]; then
  echo "Backend dir not found: $BACKEND_DIR" >&2
  exit 6
fi

# Domain -> pos-* module mapping. Adjust as needed.
# If no mapping exists, we fall back to pos-<domain> when present.
declare -A DOMAIN_TO_MODULE=(
  [accounting]="pos-accounting"
  [audit]="pos-event-receiver"
  [billing]="pos-invoice"
  [crm]="pos-customer"
  [inventory]="pos-inventory"
  [location]="pos-location"
  [order]="pos-order"
  [people]="pos-people"
  [positivity]="pos-agent-framework"
  [pricing]="pos-price"
  [catalog]="pos-catalog"
  [product]="pos-catalog"
  [security]="pos-security-service"
  [shopmgmt]="pos-shop-manager"
  [workexec]="pos-workorder"
  [mcp]="pos-mcp-server"
  [ui]="pos-image"
  [ui-mobile]="pos-image"
)

module_for_domain() {
  local domain="$1"
  if [[ -n "${DOMAIN_TO_MODULE[$domain]+_}" ]]; then
    echo "${DOMAIN_TO_MODULE[$domain]}"
    return 0
  fi

  local fallback="pos-${domain}"
  if [[ -d "$BACKEND_DIR/$fallback" ]]; then
    echo "$fallback"
    return 0
  fi

  return 1
}

truncate_to_max_chars() {
  local file="$1"
  local max_chars="$2"

  # Use wc -c to get byte count; assume UTF-8-ish text.
  local bytes
  bytes=$(wc -c < "$file" | tr -d ' ')

  if [[ "$bytes" -le "$max_chars" ]]; then
    cat "$file"
    return 0
  fi

  # head -c is byte-based; good enough for our prompt input.
  printf "[NOTE] Input file truncated from %s bytes to %s bytes.\n\n" "$bytes" "$max_chars"
  head -c "$max_chars" "$file"
}

openai_chat() {
  local system_text="$1"
  local user_text="$2"

  local org_header=()
  if [[ -n "${OPENAI_ORG:-}" ]]; then
    org_header=(-H "OpenAI-Organization: ${OPENAI_ORG}")
  fi

  local payload
  payload=$(jq -n \
    --arg model "$OPENAI_MODEL" \
    --arg sys "$system_text" \
    --arg usr "$user_text" \
    '{
      model: $model,
      temperature: 0.2,
      messages: [
        {role:"system", content:$sys},
        {role:"user", content:$usr}
      ]
    }')

  # Use a temp file to capture response body for better error messages.
  local resp_file
  resp_file=$(mktemp)

  local http_code
  http_code=$(curl -sS \
    -o "$resp_file" \
    -w "%{http_code}" \
    -X POST "${OPENAI_API_BASE%/}/chat/completions" \
    -H "Authorization: Bearer ${OPENAI_API_KEY}" \
    -H "Content-Type: application/json" \
    "${org_header[@]}" \
    --data-binary "$payload" || true)

  if [[ "$http_code" -lt 200 || "$http_code" -ge 300 ]]; then
    echo "OpenAI API error (HTTP $http_code):" >&2
    cat "$resp_file" >&2
    rm -f "$resp_file"
    return 1
  fi

  jq -r '.choices[0].message.content // empty' < "$resp_file"
  rm -f "$resp_file"
}

write_doc() {
  local out_file="$1"
  local content="$2"

  local tmp
  tmp=$(mktemp)
  printf "%s\n" "$content" > "$tmp"
  mv "$tmp" "$out_file"
}

make_agent_guide_prompt() {
  local domain="$1"
  cat <<EOF
You are writing documentation for a Java/Spring Boot microservice in a modular POS system.

Task: Create AGENT_GUIDE.md for the domain '$domain'.

Requirements:
- Output valid Markdown.
- Be concise but concrete: describe domain boundaries, authoritative data ownership, invariants, key workflows, and integration points.
- Include sections for: Purpose, Domain Boundaries, Key Entities/Concepts, Invariants/Business Rules, Events/Integrations, API Expectations (high-level), Security/Authorization assumptions, Observability (logs/metrics/tracing), Testing guidance, and Common pitfalls.
- Do NOT invent APIs that are not implied by the input; use "TBD" where needed.
EOF
}

make_checklist_prompt() {
  local domain="$1"
  cat <<EOF
Create STORY_VALIDATION_CHECKLIST.md for the domain '$domain'.

This checklist will be used by engineers/reviewers to validate any story implementation in this domain.

Requirements:
- Output valid Markdown.
- Use checkboxes (e.g., - [ ] item).
- Organize into sections: Scope/Ownership, Data Model & Validation, API Contract, Events & Idempotency, Security, Observability, Performance & Failure Modes, Testing, Documentation.
- Keep items actionable and verifiable.
EOF
}

process_domain_file() {
  local file="$1"
  local base
  base=$(basename "$file")

  # domain-foo.txt -> foo
  local domain
  domain="${base#domain-}"
  domain="${domain%.txt}"

  if [[ -n "$ONLY_DOMAIN" && "$domain" != "$ONLY_DOMAIN" ]]; then
    return 0
  fi

  if [[ ! -s "$file" ]]; then
    echo "[SKIP] Empty domain file: $file" >&2
    return 0
  fi

  local module
  if ! module=$(module_for_domain "$domain"); then
    echo "[SKIP] No module mapping for domain '$domain' (file: $file)" >&2
    return 0
  fi

  local module_dir="$BACKEND_DIR/$module"
  if [[ ! -d "$module_dir" ]]; then
    echo "[SKIP] Module dir not found: $module_dir" >&2
    return 0
  fi

  local out_dir="$module_dir/.business-rules/$domain"
  local agent_guide_out="$out_dir/AGENT_GUIDE.md"
  local checklist_out="$out_dir/STORY_VALIDATION_CHECKLIST.md"

  echo "[INFO] Domain '$domain' -> module '$module'"
  echo "[INFO] Reading: $file"
  echo "[INFO] Writing: $agent_guide_out"
  echo "[INFO] Writing: $checklist_out"

  if [[ "$DRY_RUN" == "1" ]]; then
    mkdir -p "$out_dir"
    write_doc "$agent_guide_out" "# AGENT_GUIDE (dry-run)\n\nWould generate from $file"
    write_doc "$checklist_out" "# STORY_VALIDATION_CHECKLIST (dry-run)\n\nWould generate from $file"
    return 0
  fi

  mkdir -p "$out_dir"

  local input_text
  input_text=$(truncate_to_max_chars "$file" "$MAX_CHARS")

  local sys_common
  sys_common=$'You are a senior staff engineer.\nYou produce secure-by-default, pragmatic docs.\nDo not include secrets.\n'

  local agent_guide
  agent_guide=$(openai_chat \
    "$sys_common" \
    "$(make_agent_guide_prompt "$domain")

INPUT (domain export):

${input_text}")

  if [[ -z "$agent_guide" ]]; then
    echo "[ERROR] Empty AGENT_GUIDE response for domain '$domain'" >&2
    return 1
  fi

  local checklist
  checklist=$(openai_chat \
    "$sys_common" \
    "$(make_checklist_prompt "$domain")

INPUT (domain export):

${input_text}")

  if [[ -z "$checklist" ]]; then
    echo "[ERROR] Empty STORY_VALIDATION_CHECKLIST response for domain '$domain'" >&2
    return 1
  fi

  write_doc "$agent_guide_out" "$agent_guide"
  write_doc "$checklist_out" "$checklist"

  echo "[OK] Generated docs for domain '$domain'"
}

shopt -s nullglob
files=("$INPUT_DIR"/domain-*.txt)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "No domain-*.txt files found in $INPUT_DIR" >&2
  exit 7
fi

for f in "${files[@]}"; do
  process_domain_file "$f"
done

echo "Done."
