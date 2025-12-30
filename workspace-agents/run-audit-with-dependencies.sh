#!/bin/bash

# Script to run the Missing Issues Audit with proper classpath including all dependencies

echo "Building project and running Missing Issues Audit..."

# Navigate to the workspace-agents directory
cd "$(dirname "$0")"

# Clean and compile the project
echo "Compiling project..."
mvn clean compile

# Run the application using Maven exec plugin (includes all dependencies)
echo "Running Missing Issues Audit..."
mvn exec:java -Dexec.mainClass="com.durion.audit.MissingIssuesAuditMain" -Dexec.args="$*"