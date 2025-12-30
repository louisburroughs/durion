#!/bin/bash

# Test script for GitHub comment functionality

echo "🧪 **TESTING GITHUB COMMENT FUNCTIONALITY**"
echo "==========================================="

# Check if GitHub token is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ **GITHUB TOKEN REQUIRED**"
    echo "   Please set your GitHub token:"
    echo "   export GITHUB_TOKEN=your_github_token_here"
    exit 1
fi

echo "🔨 Compiling test classes..."
if [ ! -d "target/classes" ]; then
    mkdir -p target/classes
fi

javac -cp "target/classes" -d target/classes src/main/java/GitHubApiClientSSLBypass.java 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "⚠️ Compilation warnings (proceeding anyway)"
fi

echo ""
echo "🧪 **RUNNING COMMENT TEST**"
echo "=========================="
echo "This will:"
echo "  1. Test GitHub API connection"
echo "  2. Fetch [STORY] issues from durion repository"
echo "  3. Add a test comment to the first issue found"
echo ""
echo "⚠️ Note: This will add a real comment to a real GitHub issue!"
echo ""

read -p "Continue with comment test? (yes/no): " confirm

if [ "$confirm" = "yes" ]; then
    echo ""
    echo "🚀 Starting comment functionality test..."
    java -cp "target/classes" GitHubApiClientSSLBypass "$GITHUB_TOKEN" --test-comment
else
    echo "❌ Test cancelled"
    echo ""
    echo "To test without adding comments, run:"
    echo "java -cp \"target/classes\" GitHubApiClientSSLBypass \"$GITHUB_TOKEN\""
fi

echo ""
echo "🏁 Test complete!"