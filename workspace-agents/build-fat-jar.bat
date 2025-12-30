@echo off
REM Script to build a fat JAR with all dependencies included

echo Building fat JAR with all dependencies...

REM Navigate to the workspace-agents directory
cd /d "%~dp0"

REM Clean and package the project with all dependencies
echo Packaging project...
mvn clean package

echo Fat JAR created: target/missing-issues-audit.jar
echo Run with: java -jar target/missing-issues-audit.jar [arguments]