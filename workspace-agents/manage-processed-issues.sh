#!/bin/bash

# Utility script to manage processed issues

PROCESSED_FILE=".github/orchestration/processed-issues.txt"

case "$1" in
    "list")
        echo "📋 **PROCESSED ISSUES LIST**"
        echo "=========================="
        if [ -f "$PROCESSED_FILE" ]; then
            echo "📂 File: $PROCESSED_FILE"
            echo "📊 Total processed issues: $(wc -l < "$PROCESSED_FILE")"
            echo ""
            echo "Issue Numbers:"
            cat "$PROCESSED_FILE" | sort -n | sed 's/^/  #/'
        else
            echo "ℹ️ No processed issues file found"
            echo "   File: $PROCESSED_FILE"
        fi
        ;;
    
    "add")
        if [ -z "$2" ]; then
            echo "❌ Usage: $0 add <issue_number>"
            echo "   Example: $0 add 123"
            exit 1
        fi
        
        mkdir -p "$(dirname "$PROCESSED_FILE")"
        echo "$2" >> "$PROCESSED_FILE"
        echo "✅ Added issue #$2 to processed list"
        echo "   This issue will be skipped in future processing"
        ;;
    
    "remove")
        if [ -z "$2" ]; then
            echo "❌ Usage: $0 remove <issue_number>"
            echo "   Example: $0 remove 123"
            exit 1
        fi
        
        if [ -f "$PROCESSED_FILE" ]; then
            grep -v "^$2$" "$PROCESSED_FILE" > "${PROCESSED_FILE}.tmp"
            mv "${PROCESSED_FILE}.tmp" "$PROCESSED_FILE"
            echo "✅ Removed issue #$2 from processed list"
            echo "   This issue will be processed again if it has [STORY] label"
        else
            echo "ℹ️ No processed issues file found"
        fi
        ;;
    
    "clear")
        echo "⚠️ **WARNING: This will clear ALL processing history!**"
        echo "   All previously processed issues will be processed again."
        echo ""
        read -p "Are you sure? (yes/no): " confirm
        
        if [ "$confirm" = "yes" ]; then
            rm -f "$PROCESSED_FILE"
            echo "🗑️ Cleared all processing history"
            echo "   All [STORY] issues will be processed on next run"
        else
            echo "❌ Operation cancelled"
        fi
        ;;
    
    "check")
        if [ -z "$2" ]; then
            echo "❌ Usage: $0 check <issue_number>"
            echo "   Example: $0 check 123"
            exit 1
        fi
        
        if [ -f "$PROCESSED_FILE" ] && grep -q "^$2$" "$PROCESSED_FILE"; then
            echo "✅ Issue #$2 has been processed"
            echo "   It will be skipped in future runs"
        else
            echo "❌ Issue #$2 has NOT been processed"
            echo "   It will be processed if it has [STORY] label"
        fi
        ;;
    
    *)
        echo "🔧 **PROCESSED ISSUES MANAGER**"
        echo "============================="
        echo ""
        echo "Usage: $0 <command> [arguments]"
        echo ""
        echo "Commands:"
        echo "  list                    - Show all processed issues"
        echo "  add <issue_number>      - Mark an issue as processed (skip it)"
        echo "  remove <issue_number>   - Remove from processed list (reprocess it)"
        echo "  check <issue_number>    - Check if an issue has been processed"
        echo "  clear                   - Clear all processing history (DANGEROUS)"
        echo ""
        echo "Examples:"
        echo "  $0 list                 # Show all processed issues"
        echo "  $0 add 123              # Skip issue #123 in future runs"
        echo "  $0 remove 123           # Allow issue #123 to be processed again"
        echo "  $0 check 123            # Check if issue #123 was processed"
        echo "  $0 clear                # Clear all history (will reprocess everything)"
        echo ""
        echo "📁 Processed issues file: $PROCESSED_FILE"
        ;;
esac