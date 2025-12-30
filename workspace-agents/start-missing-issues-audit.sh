#!/bin/bash

# Production Missing Issues Audit System Startup Script
# This script starts the missing issues audit system for production use

echo "🔍 Starting Missing Issues Audit System"
echo "======================================="
echo "This system identifies missing implementation issues in frontend and backend repositories"

# Check if we're in the right directory
if [ ! -f "src/main/java/com/durion/audit/MissingIssuesAuditMain.java" ]; then
    echo "❌ Error: Please run this script from the workspace-agents directory"
    echo "   Current directory: $(pwd)"
    echo "   Expected files: src/main/java/com/durion/audit/MissingIssuesAuditMain.java"
    exit 1
fi

# Check for GitHub token
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ Error: GitHub token not found"
    echo ""
    echo "Please set your GitHub token using one of these methods:"
    echo "   1. Environment variable: export GITHUB_TOKEN=your_token_here"
    echo "   2. Command line argument: --token your_token_here"
    echo ""
    echo "Your token needs 'repo' permissions to access repository issues"
    echo "Get a token at: https://github.com/settings/tokens"
    exit 1
fi

# Compile the audit system
echo "🔨 Compiling Missing Issues Audit System..."

# Use Maven for proper compilation with dependencies
mvn compile -q
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"

echo ""
echo "🎯 **STARTING MISSING ISSUES AUDIT SYSTEM**"
echo ""
echo "This system will:"
echo "   • Read processed issues from .github/orchestration/processed-issues.txt"
echo "   • Scan frontend repository: louisburroughs/durion-moqui-frontend"
echo "   • Scan backend repository: louisburroughs/durion-positivity-backend"
echo "   • Generate reports in .github/orchestration/missing-issues/"
echo "   • Optionally create missing implementation issues"
echo ""

# Start the audit system using Maven exec plugin (includes all dependencies)
mvn exec:java -Dexec.mainClass="com.durion.audit.MissingIssuesAuditMain" -Dexec.args="$*" -q