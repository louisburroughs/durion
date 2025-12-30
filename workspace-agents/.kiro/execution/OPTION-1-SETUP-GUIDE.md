# 🚀 Option 1: Automatic Story Processing - Setup Guide

## Overview

Option 1 provides **fully automatic story processing** that monitors your durion repository and processes [STORY] issues without manual intervention.

## ✅ What Option 1 Does Automatically

1. **Monitors GitHub Repository**: Checks `https://github.com/louisburroughs/durion.git` every 5 minutes
2. **Detects New Stories**: Finds issues labeled `[STORY]`
3. **Analyzes & Sequences**: Determines Backend-First, Frontend-First, or Parallel classification
4. **Generates Coordination Documents**:
   - `story-sequence.md` - Master orchestration sequence
   - `frontend-coordination.md` - Frontend development readiness
   - `backend-coordination.md` - Backend development priorities
5. **Creates Implementation Issues**: Automatically creates issues in:
   - `durion-moqui-frontend` repository (frontend work)
   - `durion-positivity-backend` repository (backend work)

## 🎯 How to Execute Option 1

### Method 1: Using the Startup Script (Recommended)

#### On Linux/Mac:
```bash
cd workspace-agents
./start-story-monitoring.sh
```

#### On Windows:
```cmd
cd workspace-agents
start-story-monitoring.bat
```

### Method 2: Direct Java Execution

```bash
cd workspace-agents

# Compile (if needed)
javac -cp "target/classes" -d target/classes src/main/java/*.java src/main/java/agents/*.java src/main/java/core/*.java

# Run the monitor
java -cp "target/classes" GitHubStoryMonitor
```

## 📊 What You'll See When Running

```
🚀 **STARTING AUTOMATIC STORY PROCESSING**
==========================================
📋 Monitoring: louisburroughs/durion
⏱️ Polling Interval: 5 minutes
🎯 Looking for: [STORY] labeled issues

✅ **AUTOMATIC STORY PROCESSING STARTED**
   The system will now automatically:
   1. Monitor durion repository for [STORY] issues
   2. Analyze and sequence new stories
   3. Generate coordination documents
   4. Create implementation issues in target repositories

📊 **MONITORING STATUS: ACTIVE** 🟢

🔍 [2024-12-24 10:30:00] Checking for new [STORY] issues...
   ℹ️ No new stories detected

📊 Status: 🟢 ACTIVE - Monitoring louisburroughs/durion every 5 minutes
```

## 🎯 How to Use Option 1

### Step 1: Start the Monitoring Service

Run one of the startup methods above. The service will start monitoring immediately.

### Step 2: Create [STORY] Issues

In your durion repository (`https://github.com/louisburroughs/durion.git`):

1. **Create a new issue**
2. **Add the `[STORY]` label**
3. **Write your story** using this format:

```markdown
# [STORY] Customer Payment Processing

As a customer, I want to process payments securely, so that I can complete my purchase.

## Description
The system should handle credit card validation, payment authorization, receipt generation, and order confirmation with proper error handling and security measures.

## Domain
payment

## Additional Requirements
- Must integrate with existing payment gateway
- Should support multiple payment methods
- Requires PCI compliance
```

### Step 3: Wait for Automatic Processing

Within 5 minutes, the system will:

1. **Detect** your new [STORY] issue
2. **Analyze** the requirements and classify as Backend-First, Frontend-First, or Parallel
3. **Update** coordination documents in `.github/orchestration/`
4. **Create** implementation issues in target repositories

### Step 4: Check the Results

After processing, you'll find:

#### Generated Coordination Documents:
- `.github/orchestration/story-sequence.md` - Updated with your story
- `.github/orchestration/frontend-coordination.md` - Shows frontend readiness
- `.github/orchestration/backend-coordination.md` - Shows backend priorities

#### Created Implementation Issues:
- **Frontend Issue**: In `durion-moqui-frontend` repository with Vue.js/TypeScript details
- **Backend Issue**: In `durion-positivity-backend` repository with Spring Boot/Java details

## 🛑 How to Stop Option 1

Press `Ctrl+C` in the terminal where the monitor is running. You'll see:

```
🛑 Shutting down story monitor...
✅ Story monitor stopped gracefully
```

## 🔧 Configuration Options

The monitor can be configured by editing `GitHubStoryMonitor.java`:

```java
// Monitoring configuration
private static final int POLLING_INTERVAL_MINUTES = 5;  // Change polling frequency
private static final String DURION_REPO = "louisburroughs/durion";  // Change repository
```

## 📋 Monitoring Status

While running, the service shows status every 30 seconds:

- 🟢 **ACTIVE** - Monitoring and processing stories
- 🔴 **STOPPED** - Not monitoring

## 🎯 Example Workflow

1. **You create**: `[STORY] User Authentication` issue in durion repository
2. **System detects**: New story within 5 minutes
3. **System analyzes**: Determines it's "Backend-First" (needs API before UI)
4. **System generates**: Updated coordination documents
5. **System creates**: 
   - Backend issue in `durion-positivity-backend` for JWT authentication API
   - Frontend issue in `durion-moqui-frontend` for login UI (marked as blocked until backend is ready)

## ✅ Benefits of Option 1

- **Zero Manual Work**: Completely automated story processing
- **Consistent Processing**: Every story gets the same analysis and sequencing
- **Real-time Coordination**: Documents stay up-to-date automatically
- **Cross-Repository Integration**: Issues created in the right repositories
- **Dependency Management**: Frontend stories properly blocked until backend is ready

## 🚀 You're Ready!

Option 1 is now set up and ready to use. Simply start the monitoring service and begin creating [STORY] issues in your durion repository. The system will handle everything else automatically!

**The future of automated story processing is here! 🎊**