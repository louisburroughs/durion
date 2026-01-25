#!/bin/bash

# WorkExec Issue Label Update Script
# Updates labels on all 28 WorkExec issues
# - Removes: blocked:clarification, status:draft
# - Adds: status:needs-review
# - Updates domain labels for 9 issues moving to other domains

set -e

REPO="durion-moqui-frontend"
OWNER="louisburroughs"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Issues staying in domain:workexec (8 issues)
WORKEXEC_ISSUES=(222 216 162 219 157 79 134 129)

# Issues moving to domain:people (5 issues)
PEOPLE_ISSUES=(149 146 145 132 131)

# Issues moving to domain:shopmgmt (4 issues)
SHOPMGMT_ISSUES=(138 137 133 127)

# Issues moving to domain:order (1 issue)
ORDER_ISSUES=(85)

# Phase 2 issues (already completed, keeping for reference)
PHASE2_ISSUES=(271 269)

echo -e "${BLUE}===============================================${NC}"
echo -e "${BLUE}WorkExec Issue Label Update Script${NC}"
echo -e "${BLUE}===============================================${NC}"
echo ""
echo -e "${YELLOW}Repository: ${OWNER}/${REPO}${NC}"
echo ""

# Function to update labels for an issue
update_issue_labels() {
    local issue_num=$1
    local add_labels=$2
    local remove_labels=$3
    local domain_label=$4
    
    echo -e "${BLUE}Processing Issue #${issue_num}...${NC}"
    
    # Build the labels to add
    local labels_to_add="status:needs-review"
    if [ -n "$add_labels" ]; then
        labels_to_add="${labels_to_add},$add_labels"
    fi
    if [ -n "$domain_label" ]; then
        labels_to_add="${labels_to_add},$domain_label"
    fi
    
    # Build the labels to remove
    local labels_to_remove="blocked:clarification,status:draft"
    if [ -n "$remove_labels" ]; then
        labels_to_remove="${labels_to_remove},$remove_labels"
    fi
    
    # Use GitHub CLI to update labels
    if command -v gh &> /dev/null; then
        # Add new labels
        echo -e "  ${GREEN}Adding labels: ${labels_to_add}${NC}"
        gh issue edit $issue_num \
            --repo "${OWNER}/${REPO}" \
            --add-label "$labels_to_add" 2>/dev/null || echo "  ${YELLOW}Warning: Could not add all labels${NC}"
        
        # Remove old labels
        echo -e "  ${RED}Removing labels: ${labels_to_remove}${NC}"
        gh issue edit $issue_num \
            --repo "${OWNER}/${REPO}" \
            --remove-label "$labels_to_remove" 2>/dev/null || echo "  ${YELLOW}Warning: Could not remove all labels${NC}"
    else
        echo -e "  ${YELLOW}GitHub CLI (gh) not found. Skipping...${NC}"
        echo "  Would add labels: $labels_to_add"
        echo "  Would remove labels: $labels_to_remove"
    fi
    
    echo ""
}

# Process workexec issues (staying in domain:workexec)
echo -e "${GREEN}=== Issues Staying in domain:workexec (8 issues) ===${NC}"
for issue in "${WORKEXEC_ISSUES[@]}"; do
    update_issue_labels $issue "" "" "domain:workexec"
done

# Process people issues (moving to domain:people)
echo -e "${GREEN}=== Issues Moving to domain:people (5 issues) ===${NC}"
for issue in "${PEOPLE_ISSUES[@]}"; do
    update_issue_labels $issue "" "domain:workexec" "domain:people"
done

# Process shopmgmt issues (moving to domain:shopmgmt)
echo -e "${GREEN}=== Issues Moving to domain:shopmgmt (4 issues) ===${NC}"
for issue in "${SHOPMGMT_ISSUES[@]}"; do
    update_issue_labels $issue "" "domain:workexec" "domain:shopmgmt"
done

# Process order issues (moving to domain:order)
echo -e "${GREEN}=== Issues Moving to domain:order (1 issue) ===${NC}"
for issue in "${ORDER_ISSUES[@]}"; do
    update_issue_labels $issue "" "domain:workexec" "domain:order"
done

echo -e "${BLUE}===============================================${NC}"
echo -e "${GREEN}✅ Label update script complete!${NC}"
echo -e "${BLUE}===============================================${NC}"
echo ""
echo -e "${YELLOW}Summary of changes:${NC}"
echo -e "  ${GREEN}Added:${NC} status:needs-review (all 28 issues)"
echo -e "  ${RED}Removed:${NC} blocked:clarification, status:draft (all 28 issues)"
echo -e "  ${RED}Removed:${NC} domain:workexec (9 issues moving to other domains)"
echo ""
echo -e "${YELLOW}Domain assignments:${NC}"
echo -e "  ${GREEN}domain:workexec${NC}: #222, #216, #162, #219, #157, #79, #134, #129 (8 issues)"
echo -e "  ${GREEN}domain:people${NC}: #149, #146, #145, #132, #131 (5 issues)"
echo -e "  ${GREEN}domain:shopmgmt${NC}: #138, #137, #133, #127 (4 issues)"
echo -e "  ${GREEN}domain:order${NC}: #85 (1 issue)"
echo ""
echo -e "${YELLOW}Note: Issue #133 is marked as duplicate of #137${NC}"
echo ""
