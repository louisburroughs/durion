#!/bin/bash

# Test URL encoding for [STORY] label

echo "🧪 **TESTING URL ENCODING FOR [STORY] LABEL**"
echo "============================================="

echo ""
echo "📋 **URL Encoding Reference:**"
echo "   Raw label: [STORY]"
echo "   URL encoded: %5BSTORY%5D"
echo "   [ = %5B"
echo "   ] = %5D"

echo ""
echo "🌐 **Expected GitHub API URL:**"
echo "   https://api.github.com/repos/louisburroughs/durion/issues?labels=%5BSTORY%5D&state=open"

echo ""
echo "🔍 **Testing with curl (if token is set):**"

if [ -n "$GITHUB_TOKEN" ]; then
    echo "   Token found, testing API call..."
    
    # Test the URL encoding with curl
    REPO="louisburroughs/durion"
    URL="https://api.github.com/repos/$REPO/issues?labels=%5BSTORY%5D&state=open"
    
    echo "   URL: $URL"
    echo ""
    
    curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
         -H "Accept: application/vnd.github.v3+json" \
         -H "User-Agent: Durion-Test-Agent/1.0" \
         "$URL" | head -20
    
    echo ""
    echo "✅ API call completed (check output above for results)"
else
    echo "   No GITHUB_TOKEN set, skipping API test"
    echo "   To test: export GITHUB_TOKEN=your_token_here"
fi

echo ""
echo "🎯 **What to look for:**"
echo "   - Issues with '[STORY]' label should be returned"
echo "   - Empty array [] means no issues with that label exist"
echo "   - Error response means URL encoding or authentication issue"

echo ""
echo "🏁 URL encoding test complete!"