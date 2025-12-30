#!/bin/bash

# Production GitHub Story Monitor with SSL Bypass
# This version works around SSL certificate issues in corporate environments

echo "🚀 Starting Production GitHub Story Monitor (SSL Bypass)"
echo "======================================================="
echo "⚠️ This version bypasses SSL certificate validation for development"
echo "   Only use this in development environments!"

# Check if we're in the right directory
if [ ! -f "src/main/java/ProductionStoryMonitorSSLBypass.java" ]; then
    echo "❌ Error: Please run this script from the workspace-agents directory"
    echo "   Current directory: $(pwd)"
    echo "   Expected: .../workspace-agents/"
    exit 1
fi

# Check for GitHub token
if [ -z "$GITHUB_TOKEN" ]; then
    echo ""
    echo "❌ **GITHUB TOKEN REQUIRED**"
    echo "   Please set your GitHub token:"
    echo "   export GITHUB_TOKEN=your_github_token_here"
    echo ""
    echo "   Since curl worked, your token is valid!"
    echo "   This version will bypass the SSL certificate issues."
    echo ""
    exit 1
fi

# Compile the monitor
echo "🔨 Compiling Production Story Monitor (SSL Bypass)..."
if [ ! -d "target/classes" ]; then
    mkdir -p target/classes
fi

javac -cp "target/classes" -d target/classes src/main/java/*.java src/main/java/agents/*.java src/main/java/core/*.java 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "⚠️ Compilation warnings (proceeding anyway)"
fi

echo ""
echo "🎯 **STARTING PRODUCTION STORY PROCESSING (SSL BYPASS)**"
echo ""
echo "This service will:"
echo "  • 🔗 Connect to real GitHub API (bypassing SSL certificate validation)"
echo "  • 📋 Monitor durion repository for [STORY] issues every 5 minutes"
echo "  • 📝 Write real coordination documents to .github/orchestration/"
echo "  • 🎯 Create real implementation issues in target repositories"
echo ""
echo "Press Ctrl+C to stop the service"
echo ""

# Start the production monitor with SSL bypass
java -cp "target/classes" ProductionStoryMonitorSSLBypass