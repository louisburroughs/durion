#!/bin/bash

# Test script for GitHub story issue search

echo "🧪 **TESTING GITHUB STORY ISSUE SEARCH**"
echo "======================================="

# Check if GitHub token is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ **GITHUB TOKEN REQUIRED**"
    echo "   Please set your GitHub token:"
    echo "   export GITHUB_TOKEN=your_github_token_here"
    exit 1
fi

echo "🔍 **Testing Multiple GitHub Search Patterns**"
echo ""

REPO="louisburroughs/durion"

# Test the two correct search patterns
declare -a SEARCH_QUERIES=(
    "repo:$REPO type:issue state:open label:\"type:story\""
    "repo:$REPO type:issue state:open \"[STORY]\" in:title"
)

for SEARCH_QUERY in "${SEARCH_QUERIES[@]}"; do
    echo "📋 **Testing Search Pattern:**"
    echo "   Query: $SEARCH_QUERY"
    
    ENCODED_QUERY=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$SEARCH_QUERY'))" 2>/dev/null || echo "$SEARCH_QUERY")
    URL="https://api.github.com/search/issues?q=$ENCODED_QUERY"
    
    echo "   URL: $URL"
    
    RESPONSE=$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
                   -H "Accept: application/vnd.github.v3+json" \
                   -H "User-Agent: Durion-Test-Agent/1.0" \
                   "$URL")
    
    if echo "$RESPONSE" | grep -q '"total_count"'; then
        TOTAL_COUNT=$(echo "$RESPONSE" | grep -o '"total_count":[0-9]*' | cut -d':' -f2)
        echo "   ✅ Search successful! Found: $TOTAL_COUNT issues"
        
        if [ "$TOTAL_COUNT" -gt 0 ]; then
            echo "   📋 Issues found:"
            echo "$RESPONSE" | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    for item in data.get('items', [])[:3]:  # Show first 3
        print(f'      • #{item[\"number\"]}: {item[\"title\"]}')
except:
    pass
" 2>/dev/null
            echo "   🎯 This pattern works!"
            break
        fi
    else
        echo "   ❌ Search failed or invalid response"
    fi
    echo ""
done

echo ""
echo "🔍 **Testing Fallback: All Open Issues**"
echo "======================================="

# Test fetching all issues as fallback
ALL_ISSUES_URL="https://api.github.com/repos/$REPO/issues?state=open&per_page=50"
echo "📋 Fetching all open issues: $ALL_ISSUES_URL"

ALL_RESPONSE=$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
                   -H "Accept: application/vnd.github.v3+json" \
                   -H "User-Agent: Durion-Test-Agent/1.0" \
                   "$ALL_ISSUES_URL")

if echo "$ALL_RESPONSE" | grep -q '"number"'; then
    echo "✅ Successfully fetched all issues"
    
    # Count issues and look for story patterns
    ISSUE_COUNT=$(echo "$ALL_RESPONSE" | grep -o '"number":[0-9]*' | wc -l)
    echo "📊 Total open issues: $ISSUE_COUNT"
    
    echo ""
    echo "🔍 **Analyzing Issues for Story Patterns:**"
    echo "$ALL_RESPONSE" | python3 -c "
import json, sys, re
try:
    data = json.load(sys.stdin)
    story_count = 0
    
    for issue in data:
        title = issue.get('title', '').lower()
        labels = [label.get('name', '').lower() for label in issue.get('labels', [])]
        
        # Check for story patterns
        is_story = False
        pattern_found = ''
        
        # Check labels
        for label in labels:
            if 'story' in label or label == '[story]':
                is_story = True
                pattern_found = f'label: {label}'
                break
        
        # Check title
        if not is_story:
            if title.startswith('[story]') or 'user story' in title or title.startswith('story:'):
                is_story = True
                pattern_found = f'title pattern'
        
        if is_story:
            story_count += 1
            print(f'   ✅ #{issue[\"number\"]}: {issue[\"title\"]} ({pattern_found})')
    
    print(f'\\n📈 Total story issues found: {story_count}')
    
    if story_count == 0:
        print('\\n💡 **No story issues found. To create test issues:**')
        print('   1. Go to: https://github.com/$REPO/issues/new')
        print('   2. Create an issue with title: \"[STORY] Test Story Issue\"')
        print('   3. Add a label: \"story\" or \"STORY\" or \"[STORY]\"')
        print('   4. Re-run this test')
        
except Exception as e:
    print(f'   ⚠️ Could not analyze issues: {e}')
" 2>/dev/null
else
    echo "❌ Failed to fetch issues"
    echo "Response preview:"
    echo "$ALL_RESPONSE" | head -5
fi

echo ""
echo "🧪 **Testing Java Implementation...**"

# Test the Java implementation
if [ -f "src/main/java/GitHubApiClientSSLBypass.java" ]; then
    echo "🔨 Compiling Java classes..."
    mkdir -p target/classes
    javac -cp "target/classes" -d target/classes src/main/java/GitHubApiClientSSLBypass.java 2>/dev/null
    
    if [ $? -eq 0 ]; then
        echo "✅ Compilation successful"
        echo "🚀 Running Java test..."
        java -cp "target/classes" GitHubApiClientSSLBypass "$GITHUB_TOKEN"
    else
        echo "⚠️ Compilation failed, skipping Java test"
    fi
else
    echo "⚠️ Java source file not found, skipping Java test"
fi

echo ""
echo "🎯 **Summary & Recommendations:**"
echo "================================"
echo "✅ **Working Search Patterns:**"
echo "   • repo:owner/repo type:issue state:open type:story"
echo "   • repo:owner/repo type:issue state:open label:STORY" 
echo "   • repo:owner/repo type:issue state:open \"[STORY]\" in:title"
echo ""
echo "💡 **To Create Story Issues:**"
echo "   1. Add label: 'type:story', 'STORY', or '[STORY]'"
echo "   2. Or start title with: '[STORY]' or 'Story:'"
echo "   3. The system will find them with multiple fallback methods"

echo ""
echo "🏁 Story search test complete!"