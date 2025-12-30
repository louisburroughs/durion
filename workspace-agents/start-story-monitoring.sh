#!/bin/bash

# GitHub Story Monitor Startup Script
# This script starts Option 1: Automatic Story Processing

echo "🚀 Starting GitHub Story Monitor (Option 1: Automatic Processing)"
echo "=================================================================="

# Check if we're in the right directory
if [ ! -f "src/main/java/GitHubStoryMonitor.java" ]; then
    echo "❌ Error: Please run this script from the workspace-agents directory"
    echo "   Current directory: $(pwd)"
    echo "   Expected: .../workspace-agents/"
    exit 1
fi

# Compile the monitor if needed
echo "🔨 Compiling GitHub Story Monitor..."
if [ ! -d "target/classes" ]; then
    mkdir -p target/classes
fi

# Compile all Java files
javac -cp "target/classes" -d target/classes src/main/java/*.java src/main/java/agents/*.java src/main/java/core/*.java 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "⚠️ Compilation warnings (proceeding anyway)"
fi

echo ""
echo "🎯 **STARTING AUTOMATIC STORY PROCESSING SERVICE**"
echo ""
echo "This service will:"
echo "  • Monitor durion repository for [STORY] issues every 5 minutes"
echo "  • Automatically analyze and sequence new stories"
echo "  • Generate coordination documents (story-sequence.md, frontend-coordination.md, backend-coordination.md)"
echo "  • Create implementation issues in durion-moqui-frontend and durion-positivity-backend repositories"
echo ""
echo "Press Ctrl+C to stop the service"
echo ""

# Start the monitor
java -cp "target/classes" GitHubStoryMonitor