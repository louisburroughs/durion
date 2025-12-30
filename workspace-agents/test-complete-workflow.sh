#!/bin/bash

echo "🧪 **COMPLETE WORKFLOW TEST**"
echo "============================"
echo ""

# Check if GITHUB_TOKEN is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ GITHUB_TOKEN environment variable is not set"
    echo "   Please set it with: export GITHUB_TOKEN=your_token_here"
    exit 1
fi

echo "🔍 **Step 1: Testing GitHub API Connection**"
echo "============================================"
java -cp target/classes GitHubApiClientSSLBypass $GITHUB_TOKEN --test-connection

echo ""
echo "🔍 **Step 2: Testing Story Search (Current State)**"
echo "=================================================="
java -cp target/classes GitHubApiClientSSLBypass $GITHUB_TOKEN --test-search

echo ""
echo "📝 **Step 3: Instructions for Manual Testing**"
echo "=============================================="
echo "To test the complete workflow:"
echo ""
echo "1. Create a test issue in louisburroughs/durion with:"
echo "   - Title: '[STORY] Test Story for Workflow Validation'"
echo "   - Label: 'type:story'"
echo "   - Body: 'This is a test story to validate the automatic processing workflow.'"
echo ""
echo "2. Run the production monitor:"
echo "   ./start-production-ssl-bypass.sh"
echo ""
echo "3. The system should:"
echo "   - Detect the new story"
echo "   - Create frontend and backend implementation issues"
echo "   - Add a comment to the original story"
echo "   - Update coordination documents in .github/orchestration/"
echo "   - Mark the story as processed to prevent reprocessing"
echo ""
echo "4. Verify the results:"
echo "   - Check for new issues in durion-moqui-frontend and durion-positivity-backend"
echo "   - Check for comment on original story issue"
echo "   - Check .github/orchestration/ directory for updated files"
echo ""
echo "🏁 **Workflow test preparation complete!**"
echo ""
echo "The system is ready for end-to-end testing with real GitHub issues."