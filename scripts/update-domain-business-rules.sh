#!/usr/bin/env bash
set -euo pipefail

# Updates business-rule documents for each domain by calling OpenAI LLM.
#
# Inputs:  domain-*.txt files in story-work/output
#          Existing AGENT_GUIDE.md and STORY_VALIDATION_CHECKLIST.md from durion/domains/{domain}/.business-rules/
# Outputs: Updated AGENT_GUIDE.md and STORY_VALIDATION_CHECKLIST.md in durion/domains/{domain}/.business-rules/
#
# Requirements:
# - curl, jq
# - OPENAI_API_KEY env var

usage() {
  cat <<'USAGE'
Usage:
  update-domain-business-rules.sh [options]

Options:
  --input-dir DIR        Directory containing domain-*.txt (default: ./scripts/story-work/output)
  --domains-dir DIR      durion/domains root (default: ./domains)
  --model MODEL          OpenAI model (default: OPENAI_MODEL env or gpt-4o)
  --api-base URL         OpenAI API base URL (default: OPENAI_API_BASE env or https://api.openai.com/v1)
  --domain NAME          Only process a single domain key (e.g., inventory)
  --max-chars N          Max chars from story file to send (default: 120000)
  --max-tokens N         Max tokens for LLM response (default: 16000)
  --delay SECONDS        Delay between API calls (default: 2)
  --dry-run              Print what would be done, do not call API
  -h, --help             Show this help

Env vars:
  OPENAI_API_KEY         Required
  OPENAI_MODEL           Optional (overridden by --model)
  OPENAI_API_BASE        Optional (overridden by --api-base)
  OPENAI_ORG             Optional (sent as OpenAI-Organization header if set)

Notes:
  - This script reads existing AGENT_GUIDE.md and STORY_VALIDATION_CHECKLIST.md if present
  - The LLM is asked to UPDATE the existing docs based on new frontend stories
  - Consolidated Open Questions section is added to both documents
USAGE
}

INPUT_DIR="./scripts/story-work/output"
DOMAINS_DIR="./domains"
OPENAI_MODEL="${OPENAI_MODEL:-gpt-5.2}"
OPENAI_API_BASE="${OPENAI_API_BASE:-https://api.openai.com/v1}"
ONLY_DOMAIN=""
MAX_CHARS="120000"
MAX_TOKENS="16000"
DELAY="2"
DRY_RUN="0"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input-dir) INPUT_DIR="$2"; shift 2;;
    --domains-dir) DOMAINS_DIR="$2"; shift 2;;
    --model) OPENAI_MODEL="$2"; shift 2;;
    --api-base) OPENAI_API_BASE="$2"; shift 2;;
    --domain) ONLY_DOMAIN="$2"; shift 2;;
    --max-chars) MAX_CHARS="$2"; shift 2;;
    --max-tokens) MAX_TOKENS="$2"; shift 2;;
    --delay) DELAY="$2"; shift 2;;
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
if [[ ! -d "$DOMAINS_DIR" ]]; then
  echo "Domains dir not found: $DOMAINS_DIR" >&2
  exit 6
fi

truncate_to_max_chars() {
  local file="$1"
  local max_chars="$2"

  local bytes
  bytes=$(wc -c < "$file" | tr -d ' ')

  if [[ "$bytes" -le "$max_chars" ]]; then
    cat "$file"
    return 0
  fi

  printf "[NOTE] Input file truncated from %s bytes to %s bytes.\n\n" "$bytes" "$max_chars"
  head -c "$max_chars" "$file"
}

openai_chat() {
  local system_text="$1"
  local user_text="$2"
  local max_tokens="$3"

  local org_header=()
  if [[ -n "${OPENAI_ORG:-}" ]]; then
    org_header=(-H "OpenAI-Organization: ${OPENAI_ORG}")
  fi

  # Use temp files to avoid "Argument list too long" errors with large prompts
  local sys_file user_file payload_file
  sys_file=$(mktemp)
  user_file=$(mktemp)
  payload_file=$(mktemp)
  
  printf '%s' "$system_text" > "$sys_file"
  printf '%s' "$user_text" > "$user_file"

  # Build JSON payload using rawfile to read from temp files
  jq -n \
    --arg model "$OPENAI_MODEL" \
    --rawfile sys "$sys_file" \
    --rawfile usr "$user_file" \
    --argjson max_completion_tokens "$max_tokens" \
    '{
      model: $model,
      temperature: 0.2,
      max_completion_tokens: $max_completion_tokens,
      messages: [
        {role:"system", content:$sys},
        {role:"user", content:$usr}
      ]
    }' > "$payload_file"

  rm -f "$sys_file" "$user_file"

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
    --data-binary "@${payload_file}" || true)

  rm -f "$payload_file"

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

extract_open_questions() {
  local file="$1"
  # Extract all "Open Questions" sections from the domain story file
  # Filter out null bytes to avoid warnings
  grep -Pzo '(?s)##\s+(?:\d+\.\s+)?Open Questions.*?(?=\n##|\Z)' "$file" 2>/dev/null | tr -d '\0' || true
}

make_agent_guide_prompt() {
  local domain="$1"
  local existing_guide="$2"
  local open_questions="$3"
  
  if [[ -n "$existing_guide" ]]; then
    cat <<EOF
You are a senior staff engineer updating documentation for a domain-driven POS system.

Task: UPDATE the existing AGENT_GUIDE.md for domain '$domain' based on new frontend stories.

Requirements:
- Output valid Markdown with the complete updated document.
- Review the EXISTING guide and UPDATE it to incorporate insights from the NEW FRONTEND STORIES.
- Preserve existing structure but enhance with new information about:
  - Domain boundaries and responsibilities
  - Key entities and their relationships
  - Business rules and invariants
  - Integration patterns and events
  - API contracts and patterns
  - Security/authorization requirements
  - Observability guidance (metrics, logs, traces)
  - Testing strategies
  - Common pitfalls and gotchas
- Add a new section: "## Open Questions from Frontend Stories" containing consolidated questions from the stories.
- Be concrete and actionable; avoid generic platitudes.
- Flag areas needing clarification with "TODO" or "CLARIFY".
EOF
  else
    cat <<EOF
You are a senior staff engineer creating documentation for a domain-driven POS system.

Task: CREATE AGENT_GUIDE.md for domain '$domain' based on frontend stories.

Requirements:
- Output valid Markdown.
- Describe domain boundaries, authoritative data ownership, invariants, key workflows, and integration points.
- Include sections for: Purpose, Domain Boundaries, Key Entities/Concepts, Invariants/Business Rules, Events/Integrations, API Expectations, Security/Authorization, Observability, Testing Guidance, Common Pitfalls.
- Add section: "## Open Questions from Frontend Stories" with consolidated questions.
- Be concrete and actionable; flag unknowns with "TODO" or "CLARIFY".
EOF
  fi
  
  if [[ -n "$open_questions" ]]; then
    cat <<EOF

CONSOLIDATED OPEN QUESTIONS (extract and organize these):
$open_questions
EOF
  fi
}

make_checklist_prompt() {
  local domain="$1"
  local existing_checklist="$2"
  local open_questions="$3"
  
  if [[ -n "$existing_checklist" ]]; then
    cat <<EOF
You are updating a story validation checklist for domain '$domain'.

Task: UPDATE the existing STORY_VALIDATION_CHECKLIST.md based on new frontend stories.

Requirements:
- Output valid Markdown with checkbox format: - [ ] item
- Review the EXISTING checklist and UPDATE it to incorporate validation needs from NEW FRONTEND STORIES.
- Organize into sections: Scope/Ownership, Data Model & Validation, API Contract, Events & Idempotency, Security, Observability, Performance & Failure Modes, Testing, Documentation.
- Keep items actionable and verifiable (e.g., "Verify inventory quantities cannot be negative").
- Add section: "## Open Questions to Resolve" with questions that affect validation.
- Update or add items based on patterns in the frontend stories.
EOF
  else
    cat <<EOF
You are creating a story validation checklist for domain '$domain'.

Task: CREATE STORY_VALIDATION_CHECKLIST.md based on frontend stories.

Requirements:
- Output valid Markdown with checkbox format: - [ ] item
- Organize into sections: Scope/Ownership, Data Model & Validation, API Contract, Events & Idempotency, Security, Observability, Performance & Failure Modes, Testing, Documentation.
- Keep items actionable and verifiable.
- Add section: "## Open Questions to Resolve" with questions that affect validation.
EOF
  fi
  
  if [[ -n "$open_questions" ]]; then
    cat <<EOF

CONSOLIDATED OPEN QUESTIONS (extract validation-relevant ones):
$open_questions
EOF
  fi
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

  local domain_dir="$DOMAINS_DIR/$domain"
  if [[ ! -d "$domain_dir" ]]; then
    echo "[INFO] Creating domain directory: $domain_dir"
    mkdir -p "$domain_dir/.business-rules"
  fi

  local rules_dir="$domain_dir/.business-rules"
  mkdir -p "$rules_dir"

  local agent_guide_out="$rules_dir/AGENT_GUIDE.md"
  local checklist_out="$rules_dir/STORY_VALIDATION_CHECKLIST.md"

  echo ""
  echo "═══════════════════════════════════════════════════════════════"
  echo "Processing domain: $domain"
  echo "═══════════════════════════════════════════════════════════════"
  echo "[INFO] Reading stories from: $file"
  echo "[INFO] Output directory: $rules_dir"

  # Read existing docs if present
  local existing_agent_guide=""
  local existing_checklist=""
  
  if [[ -f "$agent_guide_out" ]]; then
    echo "[INFO] Found existing AGENT_GUIDE.md (will update)"
    existing_agent_guide=$(cat "$agent_guide_out")
  else
    echo "[INFO] No existing AGENT_GUIDE.md (will create new)"
  fi
  
  if [[ -f "$checklist_out" ]]; then
    echo "[INFO] Found existing STORY_VALIDATION_CHECKLIST.md (will update)"
    existing_checklist=$(cat "$checklist_out")
  else
    echo "[INFO] No existing STORY_VALIDATION_CHECKLIST.md (will create new)"
  fi

  if [[ "$DRY_RUN" == "1" ]]; then
    write_doc "$agent_guide_out" "# AGENT_GUIDE (dry-run)\n\nWould update from $file"
    write_doc "$checklist_out" "# STORY_VALIDATION_CHECKLIST (dry-run)\n\nWould update from $file"
    echo "[DRY-RUN] Would process domain '$domain'"
    return 0
  fi

  # Extract open questions from stories
  echo "[INFO] Extracting open questions from stories..."
  local open_questions
  open_questions=$(extract_open_questions "$file")
  if [[ -n "$open_questions" ]]; then
    echo "[INFO] Found $(echo "$open_questions" | grep -c "##" || echo 0) Open Questions sections"
  fi

  # Truncate story input if needed
  local story_input
  story_input=$(truncate_to_max_chars "$file" "$MAX_CHARS")

  local sys_common
  sys_common=$'You are a senior staff engineer.\nYou produce secure-by-default, pragmatic documentation.\nBe specific and actionable.\nDo not include secrets or PII.\n'

  # Generate/update AGENT_GUIDE
  echo "[INFO] Calling OpenAI API for AGENT_GUIDE.md..."
  local agent_guide_prompt
  agent_guide_prompt="$(make_agent_guide_prompt "$domain" "$existing_agent_guide" "$open_questions")

EXISTING AGENT_GUIDE (if any):
${existing_agent_guide:-[None - create new]}

═══════════════════════════════════════════════════════════════
NEW FRONTEND STORIES:
═══════════════════════════════════════════════════════════════

${story_input}"

  local agent_guide
  agent_guide=$(openai_chat "$sys_common" "$agent_guide_prompt" "$MAX_TOKENS")

  if [[ -z "$agent_guide" ]]; then
    echo "[ERROR] Empty AGENT_GUIDE response for domain '$domain'" >&2
    return 1
  fi

  write_doc "$agent_guide_out" "$agent_guide"
  echo "[OK] Updated: $agent_guide_out"

  # Delay between API calls
  if [[ "$DELAY" != "0" ]]; then
    echo "[INFO] Waiting ${DELAY}s before next API call..."
    sleep "$DELAY"
  fi

  # Generate/update STORY_VALIDATION_CHECKLIST
  echo "[INFO] Calling OpenAI API for STORY_VALIDATION_CHECKLIST.md..."
  local checklist_prompt
  checklist_prompt="$(make_checklist_prompt "$domain" "$existing_checklist" "$open_questions")

EXISTING CHECKLIST (if any):
${existing_checklist:-[None - create new]}

═══════════════════════════════════════════════════════════════
NEW FRONTEND STORIES:
═══════════════════════════════════════════════════════════════

${story_input}"

  local checklist
  checklist=$(openai_chat "$sys_common" "$checklist_prompt" "$MAX_TOKENS")

  if [[ -z "$checklist" ]]; then
    echo "[ERROR] Empty STORY_VALIDATION_CHECKLIST response for domain '$domain'" >&2
    return 1
  fi

  write_doc "$checklist_out" "$checklist"
  echo "[OK] Updated: $checklist_out"
  echo ""
}

# Main execution
echo "═══════════════════════════════════════════════════════════════"
echo "Domain Business Rules Updater"
echo "═══════════════════════════════════════════════════════════════"
echo "Input: $INPUT_DIR"
echo "Domains: $DOMAINS_DIR"
echo "Model: $OPENAI_MODEL"
echo "Max tokens: $MAX_TOKENS"
echo "Delay between calls: ${DELAY}s"
echo "═══════════════════════════════════════════════════════════════"

shopt -s nullglob
files=("$INPUT_DIR"/domain-*.txt)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "No domain-*.txt files found in $INPUT_DIR" >&2
  exit 7
fi

echo "Found ${#files[@]} domain files to process"

processed=0
skipped=0
failed=0

for f in "${files[@]}"; do
  if process_domain_file "$f"; then
    ((processed++)) || true
  else
    echo "[ERROR] Failed to process: $f" >&2
    ((failed++)) || true
  fi
  
  # Delay between domains (except after last one)
  if [[ "$DRY_RUN" != "1" && "$DELAY" != "0" && "$f" != "${files[-1]}" ]]; then
    echo "[INFO] Waiting ${DELAY}s before next domain..."
    sleep "$DELAY"
  fi
done

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "Summary"
echo "═══════════════════════════════════════════════════════════════"
echo "Processed: $processed"
echo "Failed: $failed"
echo "═══════════════════════════════════════════════════════════════"

if [[ "$failed" -gt 0 ]]; then
  exit 1
fi

echo "Done."
