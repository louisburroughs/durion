#!/usr/bin/env bash
# consolidate-stories-by-domain.sh
# Consolidates rewritten stories from story-work/frontend by domain label
# Creates one text file per domain with aggregated story content
# Usage: ./consolidate-stories-by-domain.sh [story_root] [output_dir]

set -euo pipefail

STORY_ROOT="${1:-./scripts/story-work/frontend}"
OUTPUT_DIR="${2:-./scripts/story-work/output}"

# Validate story root exists
if [[ ! -d "$STORY_ROOT" ]]; then
  echo "ERROR: Story root not found: $STORY_ROOT" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

# Temporary associative array to track issues by domain
declare -A domain_issues
declare -A domain_counts

echo "Scanning stories in: $STORY_ROOT" >&2
echo "Output directory: $OUTPUT_DIR" >&2
echo "" >&2

# Function to extract domain label from after.md
extract_domain() {
  local file="$1"
  # Look for domain: label in the Required section
  # Pattern: - domain:something
  local domain
  domain=$(grep -oP '^\s*-\s*domain:\K\w+' "$file" 2>/dev/null | head -n 1 || true)
  if [[ -z "$domain" ]]; then
    echo "unknown"
  else
    echo "$domain"
  fi
}

# Function to extract title from after.md
extract_title() {
  local file="$1"
  # Look for Title: or **Title** in various formats
  # Try multiple patterns
  local title=""
  
  # Pattern 1: **Title**: Something or Title: Something
  title=$(grep -oP '(?:^\*\*Title\*\*:|^##?\s+Title:|^Title:)\s*\K.+' "$file" | head -n 1 || true)
  
  # Pattern 2: First H1 after Story Header
  if [[ -z "$title" ]]; then
    title=$(grep -A 5 'Story Header' "$file" | grep -oP '^##?\s+Title\s*$' -A 1 | tail -1 || true)
  fi
  
  # Pattern 3: First significant heading after labels
  if [[ -z "$title" ]]; then
    title=$(grep -A 20 'Rewrite Variant' "$file" | grep -oP '^##?\s+\d+\.\s+Story Header' -A 3 | grep -oP '^\*\*Title\*\*:\s*\K.+' | head -1 || true)
  fi
  
  # Fallback: just say "Story"
  if [[ -z "$title" ]]; then
    title="Story"
  fi
  
  echo "$title"
}

# Scan all after.md files
total_files=0
processed_files=0

# Use process substitution to avoid subshell issues
while IFS= read -r after_md; do
  [[ -z "$after_md" ]] && continue
  ((total_files++)) || true
  
  # Extract issue number from path (e.g., story-work/frontend/65/after.md -> 65)
  issue_number=$(basename "$(dirname "$after_md")")
  
  # Validate it's a number
  if ! [[ "$issue_number" =~ ^[0-9]+$ ]]; then
    echo "  ⚠ Skipping non-numeric folder: $issue_number" >&2
    continue
  fi
  
  # Extract domain
  domain=$(extract_domain "$after_md")
  
  if [[ "$domain" == "unknown" ]]; then
    echo "  ⚠ No domain found in issue #$issue_number" >&2
    continue
  fi
  
  # Extract title
  title=$(extract_title "$after_md")
  
  # Track this issue for this domain
  if [[ -z "${domain_issues[$domain]:-}" ]]; then
    domain_issues[$domain]=""
    domain_counts[$domain]=0
  fi
  
  domain_issues[$domain]+="$issue_number|$after_md|$title"$'\n'
  ((domain_counts[$domain]++)) || true
  ((processed_files++)) || true
  
done < <(find "$STORY_ROOT" -type f -name "after.md" | sort)

echo "Found $total_files after.md files" >&2
echo "Processed $processed_files files with domain labels" >&2
echo "" >&2

# Check if we have any domains
if [[ ${#domain_issues[@]} -eq 0 ]]; then
  echo "ERROR: No stories with domain labels found" >&2
  exit 1
fi

# Create output files for each domain
domain_counter=0
for domain in "${!domain_issues[@]}"; do
  ((domain_counter++)) || true
  output_file="$OUTPUT_DIR/domain-${domain}.txt"
  
  echo "[$domain_counter/${#domain_issues[@]}] Processing domain:$domain (${domain_counts[$domain]} stories)..." >&2
  
  {
    echo "════════════════════════════════════════════════════════════════"
    echo "DOMAIN: $domain"
    echo "Total Stories: ${domain_counts[$domain]}"
    echo "Generated: $(date)"
    echo "════════════════════════════════════════════════════════════════"
    echo ""
    
    # Process each issue for this domain
    while IFS='|' read -r issue_number file_path issue_title; do
      [[ -z "$issue_number" ]] && continue
      
      echo "════════════════════════════════════════════════════════════════"
      echo "ISSUE #$issue_number: $issue_title"
      echo "File: $file_path"
      echo "════════════════════════════════════════════════════════════════"
      echo ""
      
      # Read and output the entire after.md content
      cat "$file_path"
      
      echo ""
      echo ""
      
    done <<< "${domain_issues[$domain]}"
    
  } > "$output_file"
  
  echo "  ✓ Wrote ${domain_counts[$domain]} stories to $output_file" >&2
done

# Create summary file
summary_file="$OUTPUT_DIR/DOMAINS_SUMMARY.txt"
{
  echo "Domain Story Summary"
  echo "===================="
  echo "Story Root: $STORY_ROOT"
  echo "Generated: $(date)"
  echo ""
  
  # Sort domains alphabetically for summary
  for domain in $(printf '%s\n' "${!domain_counts[@]}" | sort); do
    printf "domain:%-15s %3d stories\n" "$domain" "${domain_counts[$domain]}"
  done
  
  echo ""
  total_stories=0
  for count in "${domain_counts[@]}"; do
    ((total_stories += count))
  done
  
  echo "Total: $total_stories stories across ${#domain_counts[@]} domains"
  echo ""
  echo "Output Files:"
  for domain in $(printf '%s\n' "${!domain_counts[@]}" | sort); do
    echo "  - domain-${domain}.txt"
  done
} > "$summary_file"

echo "" >&2
echo "✓ Complete! Files created in: $OUTPUT_DIR" >&2
echo "  Summary: $summary_file" >&2
echo "" >&2
cat "$summary_file" >&2
