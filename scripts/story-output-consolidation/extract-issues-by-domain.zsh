#!/usr/bin/env zsh
# extract-issues-by-domain.zsh
# Extracts GitHub issues from durion-moqui-frontend grouped by domain label
# Creates one text file per domain with aggregated issue bodies and labels
# Usage: ./extract-issues-by-domain.zsh [output_dir] [state]
#   state: 'open' (default), 'closed', or 'all'

REPO="${REPO:-louisburroughs/durion-moqui-frontend}"
OUTPUT_DIR="${1:-.}"
ISSUE_STATE="${2:-open}"  # Default to 'open' issues only

# Validate state parameter
if [[ ! "$ISSUE_STATE" =~ ^(open|closed|all)$ ]]; then
  echo "ERROR: Invalid state '$ISSUE_STATE'. Must be 'open', 'closed', or 'all'." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

# Check for GitHub authentication
if ! gh auth status >/dev/null 2>&1; then
  echo "ERROR: GitHub CLI not authenticated. Run 'gh auth login' first." >&2
  exit 1
fi

echo "✓ Authenticated to GitHub" >&2
echo "Fetching domain labels from $REPO..." >&2
echo "Filtering issues by state: $ISSUE_STATE" >&2
echo "Additional filter: blocked:clarification label" >&2

# First, get all domain labels (one per line)
domain_labels=$(gh api repos/"$REPO"/labels --paginate --jq '.[] | select(.name | startswith("domain:")) | .name' | sed 's/domain://')

if [[ -z "$domain_labels" ]]; then
  echo "No domain labels found in repository." >&2
  exit 0
fi

# Count and display domains
domain_count=$(echo "$domain_labels" | grep -c '^')
echo "Found $domain_count domain labels" >&2
echo "" >&2

# Process each domain
counter=0
while IFS= read -r domain; do
  [[ -z "$domain" ]] && continue
  
  ((counter++))
  output_file="$OUTPUT_DIR/domain-${domain}.txt"
  
  echo "[$counter/$domain_count] Processing domain:$domain..." >&2
  
  # Get issues for this domain (no pager, filtered by state and blocked:clarification label)
  gh issue list \
    --repo "$REPO" \
    --state "$ISSUE_STATE" \
    --label "domain:$domain" \
    --label "blocked:clarification" \
    --limit 1000 \
    --json number,title,labels,body \
    --jq '.[] | "════════════════════════════════════════\nISSUE #\(.number): \(.title)\nLABELS: \(.labels | map(.name) | join(","))\nBODY:\n\(.body // "")\n"' \
    > "$output_file"
  
  issue_count=$(grep -c '^════════════════════════════════════════' "$output_file" 2>/dev/null)
  echo "  ✓ Wrote $issue_count issues to $output_file" >&2
  
done <<< "$domain_labels"

# Create summary
summary_file="$OUTPUT_DIR/DOMAINS_SUMMARY.txt"
{
  echo "Domain Issue Summary"
  echo "===================="
  echo "Repository: $REPO"
  echo "Issue State: $ISSUE_STATE"
  echo "Filter: blocked:clarification label"
  echo "Generated: $(date)"
  echo ""
  
  for domain_file in "$OUTPUT_DIR"/domain-*.txt; do
    [[ -f "$domain_file" ]] || continue
    domain=$(basename "$domain_file" .txt | sed 's/^domain-//')
    count=$(grep -c '^════════════════════════════════════════' "$domain_file" 2>/dev/null)
    printf "domain:%-15s %3d issues\n" "$domain" "$count"
  done
  
  echo ""
  total=$(grep -h '^════════════════════════════════════════' "$OUTPUT_DIR"/domain-*.txt 2>/dev/null | wc -l)
  unique=$(grep -h '^ISSUE #' "$OUTPUT_DIR"/domain-*.txt 2>/dev/null | sort -u | wc -l)
  echo "Total: $total issue entries across $domain_count domains"
  echo "Unique issues: $unique (some issues have multiple domain labels)"
} > "$summary_file"

echo "" >&2
echo "✓ Complete! Files created in: $OUTPUT_DIR" >&2
echo "  Summary: $summary_file" >&2
