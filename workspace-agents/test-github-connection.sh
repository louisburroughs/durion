#!/bin/bash

# GitHub Connection Test Script
# This script helps debug GitHub API connection issues

echo "🧪 GitHub API Connection Test"
echo "============================="

# Check if token is provided
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ GITHUB_TOKEN environment variable not set"
    echo ""
    echo "Please set your GitHub token:"
    echo "export GITHUB_TOKEN=your_github_token_here"
    echo ""
    echo "Or run with token as argument:"
    echo "./test-github-connection.sh your_github_token_here"
    exit 1
fi

# Use token from environment or argument
TOKEN=${1:-$GITHUB_TOKEN}

echo "🔍 Testing GitHub API connection with your token..."
echo ""

# Compile if needed
javac -cp "target/classes" -d target/classes src/main/java/GitHubApiClient.java 2>/dev/null

# Run the test
java -cp "target/classes" GitHubApiClient "$TOKEN"