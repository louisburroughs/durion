# 🚀 Production Story Processing - Complete Setup Guide

## 🎯 **REAL GitHub Integration - No More Simulation!**

This guide shows you how to set up **real GitHub API integration** that:
- ✅ **Actually connects** to GitHub repositories
- ✅ **Actually writes** coordination files to `.github/orchestration/`
- ✅ **Actually creates** implementation issues in target repositories
- ✅ **Actually monitors** for new [STORY] issues

## 📋 **Prerequisites**

### 1. GitHub Personal Access Token

You need a GitHub token with the following permissions:
- `repo` (Full control of private repositories)
- `issues` (Read and write issues)

#### How to Create a GitHub Token:

1. **Go to GitHub Settings**:
   - Click your profile picture → Settings
   - Go to "Developer settings" → "Personal access tokens" → "Tokens (classic)"

2. **Generate New Token**:
   - Click "Generate new token (classic)"
   - Give it a descriptive name: "Durion Workspace Agent"
   - Select scopes:
     - ✅ `repo` (Full control of private repositories)
     - ✅ `public_repo` (Access public repositories) 
   - Click "Generate token"

3. **Copy the Token**:
   - **IMPORTANT**: Copy the token immediately - you won't see it again!
   - Save it securely

### 2. Repository Access

Ensure your GitHub token has access to:
- `louisburroughs/durion` (source repository for [STORY] issues)
- `louisburroughs/durion-moqui-frontend` (target for frontend issues)
- `louisburroughs/durion-positivity-backend` (target for backend issues)

## 🚀 **Setup Instructions**

### Method 1: Using Environment Variable (Recommended)

#### Linux/Mac:
```bash
# Set your GitHub token
export GITHUB_TOKEN=your_github_token_here

# Navigate to workspace-agents directory
cd workspace-agents

# Start production monitoring
./start-production-monitoring.sh
```

#### Windows:
```cmd
# Set your GitHub token
set GITHUB_TOKEN=your_github_token_here

# Navigate to workspace-agents directory
cd workspace-agents

# Start production monitoring
start-production-monitoring.bat
```

### Method 2: Direct Java Execution

```bash
cd workspace-agents

# Compile (if needed)
javac -cp "target/classes" -d target/classes src/main/java/*.java src/main/java/agents/*.java src/main/java/core/*.java

# Run with token as argument
java -cp "target/classes" ProductionStoryMonitor your_github_token_here
```

## 📊 **What You'll See When Running**

```
🎯 **PRODUCTION GITHUB STORY MONITOR**
=====================================
This is the PRODUCTION version with real GitHub API integration

🔗 Testing GitHub API connection...
✅ GitHub API connection successful

🚀 **STARTING PRODUCTION STORY PROCESSING**
==========================================
📋 Monitoring: louisburroughs/durion
⏱️ Polling Interval: 5 minutes
🎯 Looking for: [STORY] labeled issues
📁 Writing files to: .github/orchestration/
🔗 Creating issues in: louisburroughs/durion-moqui-frontend, louisburroughs/durion-positivity-backend

📁 Created orchestration directory: /path/to/.github/orchestration

✅ **PRODUCTION STORY PROCESSING STARTED**
   The system will now:
   1. 🔍 Monitor louisburroughs/durion for [STORY] issues via GitHub API
   2. 📊 Analyze and sequence new stories
   3. 📝 Write coordination documents to .github/orchestration/
   4. 🎯 Create real implementation issues in target repositories

📊 **MONITORING STATUS: ACTIVE** 🟢

🔍 [2024-12-24 10:30:00] Fetching [STORY] issues from GitHub...
   📋 Found 3 [STORY] issues in repository
   🆕 1 new stories detected! Processing...
      • #123: Customer Payment Processing
   🔄 Processing story #123: Customer Payment Processing
      ✅ Created implementation issues:
         🎨 Frontend: https://github.com/louisburroughs/durion-moqui-frontend/issues/456
         ⚙️ Backend: https://github.com/louisburroughs/durion-positivity-backend/issues/789
   📝 Updating coordination documents...
      ✅ Updated story-sequence.md
      ✅ Updated frontend-coordination.md
      ✅ Updated backend-coordination.md
   ✅ Story processing complete!
   📝 Updated coordination documents in .github/orchestration/
   🎯 Created implementation issues for 1 stories
```

## 📁 **Generated Files**

The production system creates real files in your workspace:

### Coordination Documents:
- `.github/orchestration/story-sequence.md` - Master story orchestration
- `.github/orchestration/frontend-coordination.md` - Frontend development readiness
- `.github/orchestration/backend-coordination.md` - Backend development priorities

### GitHub Issues Created:
- **Frontend Issues**: In `durion-moqui-frontend` with Vue.js/TypeScript implementation details
- **Backend Issues**: In `durion-positivity-backend` with Spring Boot/Java implementation details

## 🎯 **How to Use the Production System**

### Step 1: Start the Production Monitor

Use one of the startup methods above. The system will:
1. Test GitHub API connection
2. Create the `.github/orchestration/` directory
3. Start monitoring every 5 minutes

### Step 2: Create [STORY] Issues

In the durion repository (`https://github.com/louisburroughs/durion`):

1. **Create a new issue**
2. **Add the `[STORY]` label** 
3. **Write your story**:

```markdown
# Customer Payment Processing

As a customer, I want to process payments securely, so that I can complete my purchase.

## Description
The system should handle credit card validation, payment authorization, receipt generation, and order confirmation with proper error handling and security measures.

## Requirements
- Must integrate with existing payment gateway
- Should support multiple payment methods
- Requires PCI compliance
```

### Step 3: Wait for Processing

Within 5 minutes, the system will:

1. **Detect** your new STORY issue via GitHub API
2. **Analyze** the requirements using the Requirements Decomposition Agent
3. **Create** real implementation issues in target repositories
4. **Update** coordination documents with current status

### Step 4: Check the Results

#### In Your Workspace:
- Check `.github/orchestration/` for updated coordination documents
- Files are written to your local filesystem

#### In GitHub:
- Check `durion-moqui-frontend` repository for new frontend issues
- Check `durion-positivity-backend` repository for new backend issues
- Issues will have proper labels and detailed implementation guidance

## 🔧 **Configuration**

You can modify the production monitor by editing `ProductionStoryMonitor.java`:

```java
// Monitoring configuration
private static final int POLLING_INTERVAL_MINUTES = 5;  // Change frequency
private static final String DURION_REPO = "louisburroughs/durion";  // Source repo
private static final String FRONTEND_REPO = "louisburroughs/durion-moqui-frontend";  // Frontend target
private static final String BACKEND_REPO = "louisburroughs/durion-positivity-backend";  // Backend target
```

## 🛑 **Stopping the Service**

Press `Ctrl+C` in the terminal. You'll see:

```
🛑 Shutting down production story monitor...
🛑 **STOPPING PRODUCTION STORY PROCESSING**
📊 **MONITORING STATUS: STOPPED** 🔴
✅ Production story monitor stopped gracefully
```

## 🔍 **Troubleshooting**

### GitHub API Connection Failed

```
❌ GitHub API connection failed!
   Please check your GitHub token and network connection.
```

**Solutions:**
1. Verify your GitHub token is correct
2. Check token permissions (needs `repo` scope)
3. Ensure network connectivity to GitHub
4. Try regenerating the token

### Permission Denied

```
❌ GitHub API error: 403 - Forbidden
```

**Solutions:**
1. Check if token has access to the repositories
2. Verify token hasn't expired
3. Ensure repositories exist and are accessible

### File Writing Errors

```
⚠️ Warning: Could not create orchestration directory
```

**Solutions:**
1. Check file system permissions
2. Ensure you're in the correct directory
3. Verify disk space availability

## ✅ **Verification Checklist**

Before using the production system:

- [ ] GitHub token created with `repo` permissions
- [ ] Token has access to all three repositories
- [ ] `GITHUB_TOKEN` environment variable set
- [ ] In the `workspace-agents` directory
- [ ] Network connectivity to GitHub

## 🎊 **You're Ready for Production!**

The production story processing system is now fully configured and ready to:

- ✅ **Monitor** real GitHub repositories
- ✅ **Process** actual [STORY] issues
- ✅ **Write** real coordination files
- ✅ **Create** real implementation issues
- ✅ **Coordinate** actual development work

**Start the production monitor and begin creating STORY issues - the system will handle everything else with real GitHub integration! 🚀**