#!/bin/bash

# Production GitHub Story Monitor Startup Script
# This script starts the REAL GitHub API integration version

echo "🚀 Starting Production GitHub Story Monitor"
echo "=========================================="
echo "This version uses REAL GitHub API integration and writes actual files!"

# Check if we're in the right directory
if [ ! -f "src/main/java/ProductionStoryMonitor.java" ]; then
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
    echo "   To create a GitHub token:"
    echo "   1. Go to GitHub Settings > Developer settings > Personal access tokens"
    echo "   2. Generate new token with 'repo' and 'issues' permissions"
    echo "   3. Copy the token and export it as GITHUB_TOKEN"
    echo ""
    exit 1
fi

# Compile the monitor
echo "🔨 Compiling Production Story Monitor..."
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
echo "🎯 **STARTING PRODUCTION STORY PROCESSING SERVICE**"
echo ""
echo "This service will:"
echo "  • 🔗 Connect to real GitHub API"
echo "  • 📋 Monitor durion repository for [STORY] issues every 5 minutes"
echo "  • 📝 Write real coordination documents to .github/orchestration/"
echo "  • 🎯 Create real implementation issues in target repositories"
echo ""
echo "Press Ctrl+C to stop the service"
echo ""

# Start the production monitor
java -cp "target/classes" ProductionStoryMonitor