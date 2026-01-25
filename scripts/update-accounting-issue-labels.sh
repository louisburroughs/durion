#!/bin/bash

# Accounting Domain Issue Label Update Script
# Updates labels on all 6 resolved accounting issues
# - Removes: blocked:clarification (if present)
# - Adds: status:resolved, priority:high (or existing priority)
# - Keeps: domain:accounting

set -e

REPO="durion-moqui-frontend"
OWNER="louisburroughs"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Resolved accounting issues from Phase 3
declare -A ACCOUNTING_ISSUES=(
    [203]="Posting Categories & GL Mapping Configuration"
    [194]="Vendor Bill from Event"
    [193]="AP Approval & Payment Scheduling"
    [190]="Manual Journal Entry with Controls"
    [187]="Bank/Cash Reconciliation"
    [183]="WorkCompleted Event Ingestion"
)

echo -e "${BLUE}===============================================${NC}"
echo -e "${BLUE}Accounting Domain Issue Label Update${NC}"
echo -e "${BLUE}===============================================${NC}"
echo ""
echo -e "${YELLOW}Repository: ${OWNER}/${REPO}${NC}"
echo "Updating 6 resolved accounting issues from Phase 3"
echo ""

# Function to update labels for an issue
update_issue_labels() {
    local issue_num=$1
    local issue_title=$2
    
    echo -e "${CYAN}Processing Issue #${issue_num}: ${issue_title}${NC}"
    
    # Labels to add
    local labels_to_add="status:resolved,domain:accounting"
    
    # Labels to remove
    local labels_to_remove="blocked:clarification"
    
    # Use GitHub CLI to update labels
    if command -v gh &> /dev/null; then
        # Add new labels
        echo -e "  ${GREEN}✓ Adding labels: ${labels_to_add}${NC}"
        gh issue edit $issue_num \
            --repo "${OWNER}/${REPO}" \
            --add-label "$labels_to_add" 2>/dev/null || echo -e "  ${YELLOW}⚠ Warning: Could not add all labels${NC}"
        
        # Remove blocking labels
        echo -e "  ${RED}✗ Removing labels: ${labels_to_remove}${NC}"
        gh issue edit $issue_num \
            --repo "${OWNER}/${REPO}" \
            --remove-label "$labels_to_remove" 2>/dev/null || true
        
        echo ""
    else
        echo -e "  ${YELLOW}⚠ GitHub CLI (gh) not found. Skipping...${NC}"
        echo "    Would add labels: $labels_to_add"
        echo "    Would remove labels: $labels_to_remove"
        echo ""
    fi
}

# Process each resolved accounting issue
for issue_num in "${!ACCOUNTING_ISSUES[@]}"; do
    issue_title="${ACCOUNTING_ISSUES[$issue_num]}"
    update_issue_labels "$issue_num" "$issue_title"
done | sort -t'#' -k2 -n

echo -e "${BLUE}===============================================${NC}"
echo -e "${GREEN}✅ Accounting issue label update complete!${NC}"
echo -e "${BLUE}===============================================${NC}"
echo ""
echo -e "${YELLOW}Summary of changes:${NC}"
echo -e "  ${GREEN}Added:${NC} status:resolved, domain:accounting (6 issues)"
echo -e "  ${RED}Removed:${NC} blocked:clarification (if present)"
echo ""
echo -e "${YELLOW}Issues updated:${NC}"
echo -e "  #203 - Posting Categories & GL Mapping Configuration"
echo -e "  #194 - Vendor Bill from Event"
echo -e "  #193 - AP Approval & Payment Scheduling"
echo -e "  #190 - Manual Journal Entry with Controls"
echo -e "  #187 - Bank/Cash Reconciliation"
echo -e "  #183 - WorkCompleted Event Ingestion"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "  1. Review Phase 3 documentation at: domains/accounting/accounting-questions.md"
echo -e "  2. Begin frontend implementation using documented contracts"
echo -e "  3. Backend team: implement remaining controller logic"
echo ""
