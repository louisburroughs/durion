#!/bin/bash

# Inventory Issue Label Update Script
# Updates labels on the 20 Inventory issues resolved in Phase 2/3
# - Removes: blocked:clarification, status:draft
# - Adds: status:needs-review
# - Applies/updates domain labels (reassignments included)
#
# Requires GitHub CLI (`gh`). Auth via `gh auth login` or env vars.

set -e

REPO="durion-moqui-frontend"
OWNER="louisburroughs"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Issues staying in domain:inventory (12)
INVENTORY_ISSUES=(243 242 241 99 97 96 94 93 91 90 88 87)

# Issues moving to other domains
WORKEXEC_ISSUES=(244 92)       # picking & pick list/task creation
PRICING_ISSUES=(260)           # supplier/vendor cost tiers
PRODUCT_ISSUES=(121 120 119 81 108)  # product master, uom, lifecycle, search, fitment

# Labels to remove from all issues
LABELS_TO_REMOVE="blocked:clarification,status:draft"

# Base label to add to all issues
BASE_ADD_LABELS="status:needs-review"

echo -e "${BLUE}===============================================${NC}"
echo -e "${BLUE}Inventory Issue Label Update Script${NC}"
echo -e "${BLUE}===============================================${NC}"
echo ""
echo -e "${YELLOW}Repository: ${OWNER}/${REPO}${NC}"

# Check gh CLI
if ! command -v gh &> /dev/null; then
  echo -e "${RED}Error: GitHub CLI (gh) not found. Install from https://cli.github.com and run 'gh auth login'.${NC}"
  exit 1
fi

# Helper to update a single issue
update_issue_labels() {
  local issue_num=$1
  local add_labels=$2
  local remove_labels=$3

  echo -e "${BLUE}Processing Issue #${issue_num}...${NC}"

  # Add labels
  if [ -n "$add_labels" ]; then
    echo -e "  ${GREEN}Adding labels: ${add_labels}${NC}"
    gh issue edit $issue_num \
      --repo ${OWNER}/${REPO} \
      --add-label "$add_labels" > /dev/null
  fi

  # Remove labels
  if [ -n "$remove_labels" ]; then
    echo -e "  ${RED}Removing labels: ${remove_labels}${NC}"
    gh issue edit $issue_num \
      --repo ${OWNER}/${REPO} \
      --remove-label "$remove_labels" > /dev/null || true
  fi

  echo ""
}

# Inventory domain (stays domain:inventory)
echo -e "${GREEN}=== Staying in domain:inventory (12 issues) ===${NC}"
for issue in "${INVENTORY_ISSUES[@]}"; do
  update_issue_labels "$issue" "${BASE_ADD_LABELS},domain:inventory" "${LABELS_TO_REMOVE}"
done

# WorkExec reassignments
echo -e "${GREEN}=== Moving to domain:workexec (2 issues) ===${NC}"
for issue in "${WORKEXEC_ISSUES[@]}"; do
  update_issue_labels "$issue" "${BASE_ADD_LABELS},domain:workexec" "${LABELS_TO_REMOVE},domain:inventory"
done

# Pricing reassignments
echo -e "${GREEN}=== Moving to domain:pricing (1 issue) ===${NC}"
for issue in "${PRICING_ISSUES[@]}"; do
  update_issue_labels "$issue" "${BASE_ADD_LABELS},domain:pricing" "${LABELS_TO_REMOVE},domain:inventory"
done

# Product/Catalog reassignments
echo -e "${GREEN}=== Moving to domain:product (5 issues) ===${NC}"
for issue in "${PRODUCT_ISSUES[@]}"; do
  update_issue_labels "$issue" "${BASE_ADD_LABELS},domain:product" "${LABELS_TO_REMOVE},domain:inventory"
done

echo -e "${BLUE}===============================================${NC}"
echo -e "${GREEN}✅ Label update script complete!${NC}"
echo -e "${BLUE}===============================================${NC}"
echo ""
echo -e "${YELLOW}Summary of changes:${NC}"
echo -e "  ${GREEN}Added:${NC} status:needs-review (all 20 issues), domain:* per assignment"
echo -e "  ${RED}Removed:${NC} blocked:clarification, status:draft (all 20 issues), domain:inventory on reassigned issues"
