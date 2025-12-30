#!/bin/bash

echo "🧪 **TESTING COMPILED GITHUB API CLIENT**"
echo "========================================"

# Check if GitHub token is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ **GITHUB TOKEN REQUIRED**"
    echo "   Please set your GitHub token:"
    echo "   export GITHUB_TOKEN=your_github_token_here"
    exit 1
fi

echo "🔨 GitHubApiClientSSLBypass is compiled and ready"
echo "🚀 Testing with new search patterns..."
echo ""

# Test the compiled Java client
java -cp target/classes GitHubApiClientSSLBypass "$GITHUB_TOKEN"

echo ""
echo "🎯 **What to look for:**"
echo "   ✅ Should show: 'Searching for story issues in: louisburroughs/durion'"
echo "   ✅ Should show: 'Trying search: repo:louisburroughs/durion type:issue state:open label:\"type:story\"'"
echo "   ✅ Should show: 'Request URL: https://api.github.com/search/issues?q=...'"
echo "   ❌ Should NOT show: 'https://api.github.com/repos/.../issues?labels=...'"

echo ""
echo "🏁 Test complete!"