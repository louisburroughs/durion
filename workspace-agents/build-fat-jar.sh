#!/bin/bash

# Script to build a fat JAR with all dependencies included

echo "Building fat JAR with all dependencies..."

# Navigate to the workspace-agents directory
cd "$(dirname "$0")"

# Clean and package the project with all dependencies
echo "Packaging project..."
mvn clean package

echo "Fat JAR created: target/missing-issues-audit.jar"
echo "Run with: java -jar target/missing-issues-audit.jar [arguments]"