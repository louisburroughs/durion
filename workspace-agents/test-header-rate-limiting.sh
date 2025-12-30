#!/bin/bash

echo "🧪 **HEADER-BASED RATE LIMITING TEST**"
echo "====================================="
echo ""

# Check if GITHUB_TOKEN is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ GITHUB_TOKEN environment variable is not set"
    echo "   Please set it with: export GITHUB_TOKEN=your_token_here"
    exit 1
fi

echo "🔧 Compiling with header-based rate limiting..."
mvn compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"
echo ""

echo "🧪 Testing header-based rate limiting..."
echo "This will make a few API calls and show the rate limit headers"
echo ""

# Test with a simple search that will show rate limit headers
java -cp target/classes GitHubApiClientSSLBypass $GITHUB_TOKEN --test-search 2>&1 | grep -E "(📊|Rate limit|Remaining|Reset time|Limit:|Used:|Resource:)" | head -20

echo ""
echo "🏁 Header-based rate limiting test complete!"
echo ""
echo "💡 **What to look for:**"
echo "   • 'Rate limit status (from headers)' messages"
echo "   • Detailed breakdown: Limit, Remaining, Used, Reset time, Resource"
echo "   • Smart delays based on actual remaining requests"
echo "   • More accurate timing than fixed delays"