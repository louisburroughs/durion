# Missing Issues Audit System - Production Guide

## Overview

The Missing Issues Audit System is now ready for production use. This system identifies missing implementation issues in frontend and backend repositories by comparing the master processed issues list against actual GitHub implementation issues.

## 🚀 Quick Start

### Prerequisites

1. **GitHub Token** with `repo` permissions
   - Get a token at: https://github.com/settings/tokens
   - Required scopes: `repo` (full repository access)

2. **Java 11+** and **Maven** installed

3. **Processed Issues File** at `.github/orchestration/processed-issues.txt`

### Basic Usage

#### Linux/Mac (Standard)
```bash
export GITHUB_TOKEN=your_token_here
cd workspace-agents
./start-missing-issues-audit.sh --audit
```

#### Linux/Mac (SSL Bypass for Corporate Environments)
```bash
export GITHUB_TOKEN=your_token_here
cd workspace-agents
./start-missing-issues-audit-ssl-bypass.sh --audit
```

#### Windows (Standard)
```cmd
set GITHUB_TOKEN=your_token_here
cd workspace-agents
start-missing-issues-audit.bat --audit
```

#### Windows (SSL Bypass)
```cmd
set GITHUB_TOKEN=your_token_here
cd workspace-agents
start-missing-issues-audit-ssl-bypass.bat --audit
```

## 📋 Command Line Options

### Audit Operations

#### Basic Audit (Report Only)
```bash
./start-missing-issues-audit.sh --audit
```
- Generates reports without creating issues
- Safe to run anytime
- Creates reports in `.github/orchestration/missing-issues/`

#### Audit with Issue Creation
```bash
./start-missing-issues-audit.sh --audit --create-issues
```
- Performs audit and creates missing implementation issues
- Requires user confirmation before creating issues
- Updates reports to reflect newly created issues

#### Incremental Audit (Last 7 Days)
```bash
./start-missing-issues-audit.sh --audit --days 7
```
- Only checks stories processed in the last 7 days
- Faster execution for recent changes
- Useful for regular monitoring

#### Range-Based Audit
```bash
./start-missing-issues-audit.sh --audit --range 200-273
```
- Only checks specific story number range
- Useful for targeted audits
- Inclusive range (both start and end numbers included)

### Token Options

#### Environment Variable (Recommended)
```bash
export GITHUB_TOKEN=ghp_your_token_here
./start-missing-issues-audit.sh --audit
```

#### Command Line Argument
```bash
./start-missing-issues-audit.sh --token ghp_your_token_here --audit
```

### Advanced Options

#### Custom Output Directory
```bash
./start-missing-issues-audit.sh --audit --output-dir /custom/path/
```

#### Disable Caching
```bash
./start-missing-issues-audit.sh --audit --no-cache
```

#### Custom Rate Limiting
```bash
./start-missing-issues-audit.sh --audit --rate-limit-delay 5000 --batch-size 3
```

## 📊 Output Files

The audit system generates several types of reports:

### Report Directory Structure
```
.github/orchestration/missing-issues/
├── audit-2024-12-26-14-30-15.json          # Detailed audit results
├── missing-frontend-2024-12-26-14-30-15.csv # Missing frontend issues
├── missing-backend-2024-12-26-14-30-15.csv  # Missing backend issues
└── summary-2024-12-26-14-30-15.md          # Human-readable summary
```

### CSV Report Format
```csv
Story Number,Story Title,Story URL,Expected Title,Target Repository,Domain
273,"Security: Audit Trail for Price Overrides","https://github.com/louisburroughs/durion/issues/273","[FRONTEND] Security: Audit Trail for Price Overrides","louisburroughs/durion-moqui-frontend","payment"
```

### JSON Report Format
```json
{
  "auditTimestamp": "2024-12-26T14:30:15Z",
  "totalProcessedStories": 206,
  "missingFrontendIssues": [...],
  "missingBackendIssues": [...],
  "auditConfiguration": {...}
}
```

## 🔧 Production Deployment

### Integration with Operations Runbook

The audit system integrates with the existing operations runbook at `docs/OperationsRunbook.md`:

#### Pre-Deployment Checklist
1. Verify GitHub token has `repo` permissions
2. Ensure `.github/orchestration/processed-issues.txt` exists
3. Test audit system with `--audit` flag (no issue creation)
4. Review generated reports for accuracy

#### Deployment Sequence
```bash
# Step 1: Compile and test
cd workspace-agents
mvn clean compile
./start-missing-issues-audit.sh --audit --range 270-273  # Test with small range

# Step 2: Full audit (report only)
./start-missing-issues-audit.sh --audit

# Step 3: Review reports and create issues if needed
./start-missing-issues-audit.sh --audit --create-issues
```

### Monitoring and Alerting

#### Health Checks
```bash
# Verify audit system compilation
mvn compile -q && echo "✅ Audit system ready" || echo "❌ Compilation failed"

# Test GitHub API connectivity
./start-missing-issues-audit.sh --token $GITHUB_TOKEN --audit --range 273-273
```

#### Performance Monitoring
- **Expected runtime**: 15-30 minutes for full audit (206 stories)
- **API calls**: ~412 repository queries plus pagination
- **Rate limiting**: 2s between queries, 10s every 5 operations

### Kubernetes Deployment (Optional)

If deploying to Kubernetes, create a CronJob for regular audits:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: missing-issues-audit
spec:
  schedule: "0 2 * * 1"  # Weekly on Monday at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: audit
            image: workspace-agents:latest
            command: ["/bin/bash"]
            args: ["-c", "./start-missing-issues-audit.sh --audit"]
            env:
            - name: GITHUB_TOKEN
              valueFrom:
                secretKeyRef:
                  name: github-token
                  key: token
          restartPolicy: OnFailure
```

## 🚨 Troubleshooting

### Common Issues

#### "GitHub token not found"
```bash
# Solution: Set environment variable
export GITHUB_TOKEN=your_token_here

# Or use command line argument
./start-missing-issues-audit.sh --token your_token_here --audit
```

#### "Compilation failed"
```bash
# Solution: Use Maven instead of javac
mvn clean compile

# Verify Java version (requires Java 11+)
java -version
```

#### "SSL certificate validation failed"
```bash
# Solution: Use SSL bypass version
./start-missing-issues-audit-ssl-bypass.sh --audit
```

#### "Rate limit exceeded"
```bash
# Solution: The system handles this automatically
# Wait for the system to retry (60 seconds for secondary rate limits)
# Or use slower rate limiting:
./start-missing-issues-audit.sh --audit --rate-limit-delay 10000
```

#### "No processed issues found"
```bash
# Solution: Verify the processed issues file exists
ls -la .github/orchestration/processed-issues.txt

# If missing, run the main production monitor first:
./start-production-monitoring.sh
```

### Error Recovery

#### Interrupted Audit
The system supports resumption of interrupted operations:
```bash
# The system will automatically detect and resume from the last checkpoint
./start-missing-issues-audit.sh --audit
```

#### Corrupted Cache
```bash
# Clear cache and restart
rm -rf .github/orchestration/audit-cache/
./start-missing-issues-audit.sh --audit --no-cache
```

## 📈 Performance Optimization

### For Large Repositories
```bash
# Use incremental audits for regular monitoring
./start-missing-issues-audit.sh --audit --days 7

# Use range-based audits for specific investigations
./start-missing-issues-audit.sh --audit --range 250-273
```

### For Corporate Networks
```bash
# Use SSL bypass version
./start-missing-issues-audit-ssl-bypass.sh --audit

# Increase rate limiting delays if needed
./start-missing-issues-audit-ssl-bypass.sh --audit --rate-limit-delay 5000
```

## 🔐 Security Considerations

### Token Security
- Store GitHub tokens as environment variables or secure secrets
- Use tokens with minimal required permissions (`repo` scope only)
- Rotate tokens regularly according to security policy

### SSL Bypass Warning
- Only use SSL bypass versions in development/corporate environments
- SSL bypass versions display warnings about certificate validation
- Maintain security through token-based authentication

### Audit Trail
- All audit operations are logged with timestamps
- Issue creation attempts are tracked in reports
- API requests and responses are logged for debugging

## 📚 Integration Examples

### CI/CD Pipeline Integration
```yaml
# GitHub Actions example
- name: Run Missing Issues Audit
  run: |
    cd workspace-agents
    ./start-missing-issues-audit.sh --audit --days 1
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Scheduled Monitoring
```bash
# Crontab entry for weekly audits
0 2 * * 1 cd /path/to/workspace-agents && ./start-missing-issues-audit.sh --audit
```

### Manual Issue Creation Workflow
```bash
# 1. Run audit to generate reports
./start-missing-issues-audit.sh --audit

# 2. Review reports in .github/orchestration/missing-issues/

# 3. Create missing issues if needed
./start-missing-issues-audit.sh --audit --create-issues
```

## 🎯 Production Readiness Checklist

- [x] ✅ Core audit functionality implemented and tested
- [x] ✅ Property-based tests passing (16 properties verified)
- [x] ✅ Integration tests passing
- [x] ✅ Rate limiting and SSL bypass support
- [x] ✅ Production startup scripts created
- [x] ✅ Command-line interface with full options
- [x] ✅ Comprehensive error handling and logging
- [x] ✅ Report generation in multiple formats
- [x] ✅ Issue creation with proper formatting and labels
- [x] ✅ Caching and incremental audit capabilities
- [x] ✅ Documentation and troubleshooting guide

The Missing Issues Audit System is **production ready** and can be deployed immediately.