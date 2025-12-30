@echo off
REM Script to run the Missing Issues Audit with proper classpath including all dependencies

echo Building project and running Missing Issues Audit...

REM Navigate to the workspace-agents directory
cd /d "%~dp0"

REM Clean and compile the project
echo Compiling project...
mvn clean compile

REM Run the application using Maven exec plugin (includes all dependencies)
echo Running Missing Issues Audit...
mvn exec:java -Dexec.mainClass="com.durion.audit.MissingIssuesAuditMain" -Dexec.args="%*"