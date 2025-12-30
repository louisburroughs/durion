@echo off

REM Production Missing Issues Audit System Startup Script
REM This script starts the missing issues audit system for production use

echo 🔍 Starting Missing Issues Audit System
echo =======================================
echo This system identifies missing implementation issues in frontend and backend repositories

REM Check if we're in the right directory
if not exist "src\main\java\com\durion\audit\MissingIssuesAuditMain.java" (
    echo ❌ Error: Please run this script from the workspace-agents directory
    echo    Current directory: %CD%
    echo    Expected files: src\main\java\com\durion\audit\MissingIssuesAuditMain.java
    exit /b 1
)

REM Check for GitHub token
if "%GITHUB_TOKEN%"=="" (
    echo ❌ Error: GitHub token not found
    echo.
    echo Please set your GitHub token using one of these methods:
    echo    1. Environment variable: set GITHUB_TOKEN=your_token_here
    echo    2. Command line argument: --token your_token_here
    echo.
    echo Your token needs 'repo' permissions to access repository issues
    echo Get a token at: https://github.com/settings/tokens
    exit /b 1
)

REM Compile the audit system
echo 🔨 Compiling Missing Issues Audit System...

REM Use Maven for proper compilation with dependencies
call mvn compile -q
if %ERRORLEVEL% neq 0 (
    echo ❌ Compilation failed
    exit /b 1
)

echo ✅ Compilation successful

echo.
echo 🎯 **STARTING MISSING ISSUES AUDIT SYSTEM**
echo.
echo This system will:
echo    • Read processed issues from .github/orchestration/processed-issues.txt
echo    • Scan frontend repository: louisburroughs/durion-moqui-frontend
echo    • Scan backend repository: louisburroughs/durion-positivity-backend
echo    • Generate reports in .github/orchestration/missing-issues/
echo    • Optionally create missing implementation issues
echo.

REM Start the audit system using Maven exec plugin (includes all dependencies)
call mvn exec:java -Dexec.mainClass="com.durion.audit.MissingIssuesAuditMain" -Dexec.args="%*" -q