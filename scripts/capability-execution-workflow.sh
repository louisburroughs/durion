#!/bin/bash

################################################################################
# Capability Execution Workflow Orchestrator (Steps 1–5)
# 
# Purpose: Execute a complete capability build from contract definition through
#          integration testing with minimal stops.
#
# Usage:   bash capability-execution-workflow.sh <CAP_ID> <PARENT_STORY_NUMBER>
#
# Example: bash capability-execution-workflow.sh cap:pos-order-capture 123
#
# Stops:   
#   - STOP 1 (USER): After backend PR merge (manual verification & merge gate)
#   - STOP 2 (USER): After frontend PR merge (manual verification & merge gate)
#   - STOP 3 (USER): After integration tests complete
#
################################################################################

set -e  # Exit on error

# Colors for readability
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m' # No Color

function print_section() {
  echo -e "\n${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
  echo -e "${BLUE}║ $1${NC}"
  echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}\n"
}

function print_step() {
  echo -e "\n${GREEN}→ $1${NC}"
}

function print_warning() {
  echo -e "${YELLOW}⚠ WARNING: $1${NC}"
}

function print_stop() {
  echo -e "${RED}⏹ STOP #$1 (USER ACTION REQUIRED)${NC}"
  echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

function check_prerequisites() {
  if [ -z "$CAP_ID" ] || [ -z "$PARENT_STORY" ]; then
    echo "Usage: bash capability-execution-workflow.sh <CAP_ID> <PARENT_STORY_NUMBER>"
    echo "Example: bash capability-execution-workflow.sh cap:pos-order-capture 123"
    exit 1
  fi
}

function init_workflow() {
  print_section "CAPABILITY EXECUTION WORKFLOW: $CAP_ID → Story #$PARENT_STORY"
  
  echo "This script orchestrates steps 1–5 of the capability workflow."
  echo "It coordinates contract definition → backend implementation → frontend implementation → integration."
  echo ""
  echo "You will see 3 mandatory stops (for merge gates + verification)."
  echo ""
  
  # Get domain from user (could be auto-detected from issue, but safer to ask)
  read -p "Domain (e.g., accounting, inventory, workexec): " DOMAIN
  if [ -z "$DOMAIN" ]; then
    echo "Domain required."
    exit 1
  fi
  
  export CAP_ID PARENT_STORY DOMAIN
  print_step "Initialized: CAP_ID=$CAP_ID, PARENT_STORY=$PARENT_STORY, DOMAIN=$DOMAIN"
}

function step_1_backend_contract_pr() {
  print_section "STEP 1: Backend Contract PR (durion) — FIRST"
  
  echo "This step declares the intent via a contract guide entry in \`durion\` repo."
  echo ""
  echo "CONTRACT LOCATION:"
  echo "  durion/domains/$DOMAIN/.business-rules/BACKEND_CONTRACT_GUIDE.md"
  echo ""
  echo "WHAT GOES IN:"
  echo "  - Story linkage: durion#$PARENT_STORY + child issue links"
  echo "  - Endpoint/event contract summary"
  echo "  - Examples (request/response shapes)"
  echo "  - Behavioral assertions (always-true invariants)"
  echo "  - Marker: contract-status: draft"
  echo ""
  
  print_step "[USER] Open durion repo in IDE and create a new branch:"
  echo "  git checkout -b cap/$CAP_ID"
  echo ""
  
  print_step "[AGENT: GitHub Copilot] Update contract guide"
  echo ""
  echo "Trigger in VS Code (durion repo, cap/$CAP_ID branch):"
  echo "  - Open durion/domains/$DOMAIN/.business-rules/BACKEND_CONTRACT_GUIDE.md"
  echo "  - Position cursor at the anchor/heading for your story"
  echo "  - Open Copilot Chat (Ctrl+Shift+I)"
  echo ""
  echo "PROMPT:"
  echo ""
  cat << 'EOF'
You are working on capability CAP_ID (replace with actual ID).
Parent STORY: durion#PARENT_STORY_NUMBER (replace with actual number).

I need you to add/update a contract guide entry for this story in this backend contract guide file.

Include:
1. Story linkage (durion#PARENT_STORY_NUMBER, backend child issue link)
2. Endpoint or event name
3. HTTP method + path (if API) or event topic (if event-driven)
4. Request/response shape examples
5. Behavioral assertions (always-true invariants, e.g., "order.total must equal sum(items)")
6. Error cases (validation, auth, idempotency)
7. Marker: contract-status: draft

Use the existing guide format as a template. Keep examples realistic and concise.
EOF
  
  echo ""
  print_step "[USER] Review Copilot's generated entry"
  echo "  - Check examples for correctness"
  echo "  - Validate assertions match acceptance criteria"
  echo "  - Commit locally: git add . && git commit -m 'contract(cap/$CAP_ID): declare contract for story durion#$PARENT_STORY'"
  echo ""
  
  print_step "[USER] Push & open PR on durion"
  echo "  - Branch: cap/$CAP_ID"
  echo "  - PR title: contract(cap/$CAP_ID): declare contract for story durion#$PARENT_STORY"
  echo "  - PR body (required fields):"
  echo "      Capability: $CAP_ID"
  echo "      Parent STORY: durion#$PARENT_STORY"
  echo "      Contract guide entry: link to anchor in BACKEND_CONTRACT_GUIDE.md"
  echo "      Status: Draft (ready for backend agent to implement)"
  echo ""
  echo "  - Assign reviewers (codeowners or domain experts)"
  echo ""
  
  read -p "Press ENTER when durion contract PR is open (save the PR URL when prompted below)..."
  read -p "Durion contract PR URL: " DURION_CONTRACT_PR_URL
  export DURION_CONTRACT_PR_URL
  
  echo -e "${GREEN}✓ Step 1 complete: Contract PR declared at $DURION_CONTRACT_PR_URL${NC}"
}

function step_2_backend_code_pr() {
  print_section "STEP 2: Backend Code PR(s)"
  
  echo "This step implements the API/service logic in pos-* modules."
  echo "This PR may be opened in parallel with step 1, but CANNOT MERGE until step 1 merges."
  echo ""
  
  print_step "[USER] Open durion-positivity-backend repo in IDE"
  echo "  git checkout -b cap/$CAP_ID"
  echo ""
  
  print_step "[AGENT: GitHub Copilot] Implement backend functionality"
  echo ""
  echo "Trigger in VS Code (durion-positivity-backend repo, cap/$CAP_ID branch):"
  echo "  - Open the relevant pos-* module (e.g., pos-order/)"
  echo "  - Open Copilot Chat (Ctrl+Shift+I)"
  echo ""
  echo "PROMPT:"
  echo ""
  cat << 'EOF'
You are implementing capability CAP_ID (replace with actual ID).
Parent STORY: durion#PARENT_STORY_NUMBER (replace with actual number).
Backend child issue: <repo>#CHILD_ISSUE_NUMBER (replace with actual number).

Contract guide entry (stable reference):
  durion repo, domains/DOMAIN_NAME/.business-rules/BACKEND_CONTRACT_GUIDE.md
  Anchor: [describe or link the specific section]

I need you to:
1. Implement the endpoint/service to match the contract
2. Add provider behavioral contract tests (ContractBehaviorIT)
3. Include examples from the contract guide in the tests
4. Add validation & error handling per the assertions
5. Include concurrency-safe patterns if needed (idempotency, optimistic locking)

Follow existing pos-* module patterns for:
  - Controller layer (keep thin, delegate to service)
  - Service layer (orchestration, business logic)
  - Repository layer (Spring Data JPA)
  - Entity/DTO shapes

Module structure:
  pos-*/src/main/java/com/durion/pos*/controller/
  pos-*/src/main/java/com/durion/pos*/service/
  pos-*/src/main/java/com/durion/pos*/repository/
  pos-*/src/test/java/com/durion/pos*/contract/

Tests: Follow the ContractBehaviorIT pattern:
  - Happy path (from examples in guide)
  - Validation errors
  - Auth/permission errors (if applicable)
  - Idempotency (if applicable)
  - Concurrency-safe invariants

Add or update OpenAPI annotations (@Operation, @ApiResponse, etc.) if the module exposes REST.
EOF
  
  echo ""
  print_step "[USER] Review Copilot's implementation"
  echo "  - Verify endpoint matches contract (path, method, status codes)"
  echo "  - Check test coverage (happy path + error cases from guide)"
  echo "  - Run tests locally:"
  echo "      cd durion-positivity-backend"
  echo "      ./mvnw -pl pos-<module> -am test"
  echo ""
  
  print_step "[USER] Commit & push backend code"
  echo "  git add . && git commit -m 'feat(cap/$CAP_ID): implement story durion#$PARENT_STORY'"
  echo "  git push origin cap/$CAP_ID"
  echo ""
  
  print_step "[USER] Open PR on durion-positivity-backend"
  echo "  - Branch: cap/$CAP_ID"
  echo "  - PR title: feat(cap/$CAP_ID): implement story durion#$PARENT_STORY"
  echo "  - PR body (required):"
  echo "      Capability: $CAP_ID"
  echo "      Parent STORY: durion#$PARENT_STORY"
  echo "      Child issue: <repo>#CHILD_BACKEND_ISSUE_NUMBER"
  echo "      Contract guide entry: [link to durion BACKEND_CONTRACT_GUIDE.md anchor]"
  echo "      Durion contract PR: $DURION_CONTRACT_PR_URL"
  echo "      Status: Ready for review (waiting for contract PR merge)"
  echo ""
  
  read -p "Press ENTER when backend code PR is open (save the PR URL when prompted below)..."
  read -p "Backend code PR URL: " BACKEND_PR_URL
  export BACKEND_PR_URL
  
  echo -e "${GREEN}✓ Step 2 complete: Backend implementation PR at $BACKEND_PR_URL${NC}"
}

function step_3_contract_freeze() {
  print_section "STEP 3: Contract Freeze"
  
  echo "This gate ensures the backend contract is finalized before frontend work begins."
  echo ""
  
  print_step "[USER] PREREQUISITE: Durion contract PR must be merged"
  echo ""
  echo "Check status:"
  echo "  1. Go to $DURION_CONTRACT_PR_URL"
  echo "  2. Verify all reviews are approved"
  echo "  3. Merge the PR (squash or standard merge)"
  echo ""
  
  read -p "Has the durion contract PR been merged? (yes/no) " CONTRACT_MERGED
  if [ "$CONTRACT_MERGED" != "yes" ]; then
    print_warning "Contract PR must be merged before proceeding. Please merge and re-run."
    exit 1
  fi
  
  print_step "[USER] AFTER contract PR merges: Update contract guide entry to stable-for-ui"
  echo ""
  echo "In durion repo (master branch, now that contract PR is merged):"
  echo "  1. Pull master: git checkout master && git pull origin master"
  echo "  2. Open domains/$DOMAIN/.business-rules/BACKEND_CONTRACT_GUIDE.md"
  echo "  3. Find the contract entry for your story"
  echo "  4. Change: contract-status: draft → contract-status: stable-for-ui"
  echo "  5. Commit: git commit -m 'contract($CAP_ID): freeze contract to stable-for-ui'"
  echo "  6. Push: git push origin master"
  echo ""
  
  print_step "[USER] (OPTIONAL) Check if backend PR needs update if contract changed"
  echo ""
  echo "If implementation diverged from final contract during backend PR review:"
  echo "  - Update durion/domains/$DOMAIN/.business-rules/BACKEND_CONTRACT_GUIDE.md examples/assertions"
  echo "  - Revert contract-status to 'draft'"
  echo "  - Update backend PR commit message and re-request review"
  echo "  - DO NOT MERGE backend PR until contract is stable-for-ui again"
  echo ""
  
  echo -e "${GREEN}✓ Step 3 complete: Contract frozen to stable-for-ui${NC}"
}

function step_4_frontend_pr() {
  print_section "STEP 4: Frontend PR(s)"
  
  echo "Frontend work begins ONLY once contract is stable-for-ui."
  echo "Frontend repo: durion-moqui-frontend and/or component-specific repos."
  echo ""
  
  print_step "[USER] Open durion-moqui-frontend in IDE (or component repo)"
  echo "  git checkout -b cap/$CAP_ID"
  echo ""
  
  print_step "[AGENT: GitHub Copilot] Implement frontend UI & integration"
  echo ""
  echo "Trigger in VS Code (durion-moqui-frontend repo, cap/$CAP_ID branch):"
  echo "  - Open relevant component under runtime/component/ or UI asset path"
  echo "  - Open Copilot Chat (Ctrl+Shift+I)"
  echo ""
  echo "PROMPT:"
  echo ""
  cat << 'EOF'
You are implementing capability CAP_ID (replace with actual ID).
Parent STORY: durion#PARENT_STORY_NUMBER (replace with actual number).
Frontend child issue: <repo>#CHILD_ISSUE_NUMBER (replace with actual number).

Contract guide entry (stable-for-ui):
  durion repo, domains/DOMAIN_NAME/.business-rules/BACKEND_CONTRACT_GUIDE.md
  Anchor: [describe or link the specific section]

Backend API endpoint/event is now implemented and available:
  Backend PR: [link to backend PR or merged commit]

I need you to:
1. Consume the backend API/event in the frontend
2. Implement UI screens/pages matching the wireframes
3. Wire form submissions, validation, and error handling
4. Add UI tests (Jest) for key user flows
5. Ensure error handling matches backend error model (shape, codes, messages)

Frontend stack:
  - Vue 3 (Composition API)
  - Quasar for components
  - TypeScript 5
  - Test framework: Jest (or configured in package.json)

Patterns to follow:
  - Services layer (composables or service classes) for API calls
  - Components for UI (keep presentation logic in components, business in services)
  - Use existing API bridge (runtime/component/durion-positivity/) for backend calls
  - Type-safe: ensure TypeScript types match contract DTO/API shapes

Wireframes/acceptance criteria:
  [Reference the wireframe path or acceptance criteria from the parent STORY]

Examples from contract (for testing):
  [Paste the examples from the backend contract guide]
EOF
  
  echo ""
  print_step "[USER] Review Copilot's frontend implementation"
  echo "  - Check UI matches wireframes"
  echo "  - Verify API calls use correct endpoints and shapes"
  echo "  - Run tests locally:"
  echo "      cd durion-moqui-frontend"
  echo "      npm test"
  echo "  - Check linting:"
  echo "      npm run lint && npm run type-check"
  echo ""
  
  print_step "[USER] Commit & push frontend code"
  echo "  git add . && git commit -m 'feat(cap/$CAP_ID): implement UI for story durion#$PARENT_STORY'"
  echo "  git push origin cap/$CAP_ID"
  echo ""
  
  print_step "[USER] Open PR on durion-moqui-frontend (or component repo)"
  echo "  - Branch: cap/$CAP_ID"
  echo "  - PR title: feat(cap/$CAP_ID): implement UI for story durion#$PARENT_STORY"
  echo "  - PR body (required):"
  echo "      Capability: $CAP_ID"
  echo "      Parent STORY: durion#$PARENT_STORY"
  echo "      Child issue: <repo>#CHILD_FRONTEND_ISSUE_NUMBER"
  echo "      Contract guide entry: [link to durion BACKEND_CONTRACT_GUIDE.md anchor]"
  echo "      Backend PR: $BACKEND_PR_URL (should be merged)"
  echo "      Wireframes: [link to wireframe in component repo]"
  echo ""
  
  read -p "Press ENTER when frontend PR is open (save the PR URL when prompted below)..."
  read -p "Frontend PR URL: " FRONTEND_PR_URL
  export FRONTEND_PR_URL
  
  echo -e "${GREEN}✓ Step 4 complete: Frontend implementation PR at $FRONTEND_PR_URL${NC}"
}

function step_5_integration_completion() {
  print_section "STEP 5: Integration Completion"
  
  echo "Verify all PRs are merged and run integration tests."
  echo ""
  
  print_stop "1"
  cat << 'EOF'
BACKEND MERGE GATE — VERIFY & MERGE

Actions:
  1. Go to backend PR: BACKEND_PR_URL
  2. Verify:
     - All CI checks pass (tests, lint, contract-sync check if configured)
     - Code reviews approved
     - No merge conflicts
     - Contract PR is merged (check PR description link)
  3. Merge the backend PR (squash or standard merge)
  4. Confirm merge in GitHub

Once merged:
  → Move to frontend merge gate (next STOP)
EOF
  
  read -p "Press ENTER after backend PR is merged..."
  
  print_stop "2"
  cat << 'EOF'
FRONTEND MERGE GATE — VERIFY & MERGE

Actions:
  1. Go to frontend PR: FRONTEND_PR_URL
  2. Verify:
     - All CI checks pass (tests, lint, type-check)
     - Code reviews approved
     - No merge conflicts
     - Backend PR is merged (check PR description link)
  3. Merge the frontend PR (squash or standard merge)
  4. Confirm merge in GitHub

Once merged:
  → Move to integration testing (next STOP)
EOF
  
  read -p "Press ENTER after frontend PR is merged..."
  
  print_stop "3"
  cat << 'EOF'
INTEGRATION TESTING & VERIFICATION

Actions:
  1. Spin up local stack (if not already running):
     - Backend: start pos-api-gateway and relevant pos-* services
     - Frontend: build and serve Moqui runtime
     
     From durion-moqui-frontend:
       ./gradlew build -x test
       java -jar runtime/build/libs/moqui.war
     
     Or use Docker Compose:
       docker-compose -f docker/moqui-postgres-compose.yml up -d
  
  2. Run the user journey from acceptance criteria:
     - Open browser to http://localhost:8080/webroot/
     - Navigate to the feature (screen/flow)
     - Perform actions matching story acceptance criteria
     - Verify responses and side effects match contract
     - Test error cases (invalid input, auth, etc.)
  
  3. Verify parent STORY checklist:
     - Update durion issue durion#PARENT_STORY
     - Link merged backend PR
     - Link merged frontend PR
     - Close story (or move to Done in project board)
  
  4. Capability completion:
     - If this was the last STORY for cap/CAP_ID → capability is complete
     - Remove cap/CAP_ID branches (local + remote) when all stories in the capability are done
     - Archive cap/CAP_ID docs in durion/docs/capabilities/ (optional)

EOF
  
  read -p "Press ENTER after integration testing is complete and verified..."
  
  echo -e "${GREEN}✓ Step 5 complete: Capability $CAP_ID delivered and verified${NC}"
}

################################################################################
# Main Execution
################################################################################

CAP_ID="${1}"
PARENT_STORY="${2}"

check_prerequisites
init_workflow
step_1_backend_contract_pr
step_2_backend_code_pr
step_3_contract_freeze

# Major stop: user must verify & merge backend PR before frontend work
read -p "Are you ready to proceed with frontend implementation? (yes/no) " READY_FOR_FRONTEND
if [ "$READY_FOR_FRONTEND" != "yes" ]; then
  echo "Pausing workflow. Resume when ready."
  exit 0
fi

step_4_frontend_pr
step_5_integration_completion

print_section "CAPABILITY WORKFLOW COMPLETE"
echo "✓ Capability $CAP_ID is fully delivered."
echo ""
echo "Summary:"
echo "  - Durion contract PR: $DURION_CONTRACT_PR_URL"
echo "  - Backend implementation PR: $BACKEND_PR_URL"
echo "  - Frontend implementation PR: $FRONTEND_PR_URL"
echo "  - Parent STORY: durion#$PARENT_STORY"
echo ""
echo "Next steps:"
echo "  1. Archive/document this capability in durion/docs/capabilities/$CAP_ID/"
echo "  2. Review lessons learned with the team"
echo "  3. Begin next capability"
echo ""
