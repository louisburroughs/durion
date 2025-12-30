#!/bin/bash

echo "🧹 **CLEAN COMPILE AND TEST**"
echo "============================"

# Clean old compiled classes
echo "🗑️ Cleaning old compiled classes..."
rm -rf target/classes
mkdir -p target/classes

# Compile the SSL bypass client
echo "🔨 Compiling GitHubApiClientSSLBypass..."
javac -d target/classes src/main/java/GitHubApiClientSSLBypass.java

if [ $? -eq 0 ]; then
    echo "✅ GitHubApiClientSSLBypass compiled successfully"
else
    echo "❌ GitHubApiClientSSLBypass compilation failed"
    exit 1
fi

# Test the SSL bypass client
if [ -n "$GITHUB_TOKEN" ]; then
    echo ""
    echo "🧪 **Testing GitHubApiClientSSLBypass**"
    echo "====================================="
    java -cp "target/classes" GitHubApiClientSSLBypass "$GITHUB_TOKEN"
else
    echo ""
    echo "⚠️ GITHUB_TOKEN not set, skipping test"
    echo "   To test: export GITHUB_TOKEN=your_token_here"
fi

echo ""
echo "🎯 **Expected URL Format:**"
echo "   Should use: https://api.github.com/search/issues?q=..."
echo "   NOT: https://api.github.com/repos/.../issues?labels=..."

echo ""
echo "🏁 Clean compile complete!"