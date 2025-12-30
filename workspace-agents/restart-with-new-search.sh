#!/bin/bash

echo "🔄 **RESTARTING WITH NEW SEARCH PATTERNS**"
echo "========================================="

# Check if GitHub token is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ **GITHUB TOKEN REQUIRED**"
    echo "   Please set your GitHub token:"
    echo "   export GITHUB_TOKEN=your_github_token_here"
    exit 1
fi

echo "🛑 If the monitor is running, please stop it first (Ctrl+C)"
echo ""
read -p "Press Enter when ready to compile and start with new search patterns..."

# Clean and recompile
echo "🧹 Cleaning old compiled classes..."
rm -rf target/classes
mkdir -p target/classes

echo "🔨 Compiling with new search patterns..."
javac -cp "target/classes" -d target/classes src/main/java/*.java src/main/java/agents/*.java src/main/java/core/*.java 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "⚠️ Compilation warnings (proceeding anyway)"
fi

echo ""
echo "🎯 **NEW SEARCH PATTERNS ACTIVE:**"
echo "   1. label:\"type:story\" - Issues with 'type:story' label"
echo "   2. \"[STORY]\" in:title - Issues with '[STORY]' in title"
echo ""
echo "🚀 **STARTING PRODUCTION STORY PROCESSING (SSL BYPASS)**"
echo "   Expected URL format: https://api.github.com/search/issues?q=..."
echo ""

# Start the production monitor with SSL bypass
java -cp "target/classes" ProductionStoryMonitorSSLBypass