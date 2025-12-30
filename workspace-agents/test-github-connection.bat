@echo off
REM GitHub Connection Test Script (Windows)
REM This script helps debug GitHub API connection issues

echo 🧪 GitHub API Connection Test
echo =============================

REM Check if token is provided
if "%GITHUB_TOKEN%"=="" (
    if "%1"=="" (
        echo ❌ GITHUB_TOKEN environment variable not set
        echo.
        echo Please set your GitHub token:
        echo set GITHUB_TOKEN=your_github_token_here
        echo.
        echo Or run with token as argument:
        echo test-github-connection.bat your_github_token_here
        pause
        exit /b 1
    )
)

REM Use token from environment or argument
if not "%1"=="" (
    set TOKEN=%1
) else (
    set TOKEN=%GITHUB_TOKEN%
)

echo 🔍 Testing GitHub API connection with your token...
echo.

REM Compile if needed
javac -cp "target/classes" -d target/classes src/main/java/GitHubApiClient.java 2>nul

REM Run the test
java -cp "target/classes" GitHubApiClient "%TOKEN%"