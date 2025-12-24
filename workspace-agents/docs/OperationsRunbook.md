# Workspace Agent Operations Runbook

## Overview

This runbook provides operational procedures for the durion workspace agent system and its coordination of the wider durion ecosystem. It is focused on the Java 21–based `workspace-agents` project in the durion repository and its cross-repository integrations with:

- durion-positivity-backend (Spring Boot microservices, Java 21)
- durion-moqui-frontend (Moqui Framework, Java 11/Groovy/Vue.js)

The workspace agents implement the workspace-level agent structure defined in `.kiro/specs/workspace-agent-structure/tasks.md` and surface behaviour via Java agents such as `RequirementsDecompositionAgent`, `FullStackIntegrationAgent`, `WorkspaceArchitectureAgent`, `UnifiedSecurityAgent`, `PerformanceCoordinationAgent` and the Story Orchestration Agent.

**Critical Performance Targets (workspace-agents):**
- Response Time: <5 seconds (95th percentile)
- Availability: 99.9% during business hours
- Concurrent Users: 100 without degradation
- RTO: 4 hours maximum
- RPO: 1 hour maximum

---

## How to Use This Runbook

Use this document as the primary operational reference for the workspace agents.

- **Before changes**: Follow section 1 (Deployment Procedures) and run the pre-deployment checks for `workspace-agents`, durion-positivity-backend, and durion-moqui-frontend.
- **During incidents**: Jump to section 3 (Incident Response Procedures) using the symptom-based subsections (service unavailable, performance, security, integration issues).
- **For DR / SRE work**: Use sections 2 (Monitoring and Alerting) and 4 (Disaster Recovery Procedures) when configuring dashboards, alerts, and recovery playbooks.
- **For regular operations**: Use sections 5–8 for weekly/monthly maintenance, optimisation, and security hardening.

**Repo-local runbooks (service-specific operations):**
- Backend services: `durion-positivity-backend/docs/OperationsRunbook.md`
- Moqui/frontend: `durion-moqui-frontend/docs/OperationsRunbook.md`

When in doubt, treat the `.kiro/specs` files and the `workspace-agents` README as the source of truth for architecture and agent capabilities, and use this runbook for the concrete commands and order of operations.

---

## 1. Deployment Procedures

### 1.1 Pre-Deployment Checklist

**Environment Validation (local / CI):**
```bash
# Verify Java versions
java -version              # Java 21 for workspace-agents and durion-positivity-backend
/opt/moqui/bin/java -version  # Java 11 for moqui runtime

# Verify workspace-agents Maven build
cd durion/workspace-agents
mvn -e -U clean test        # or use the VS Code task "maven: test"

# Verify backend build (durion-positivity-backend)
cd ../durion-positivity-backend
./mvnw -e -U -DskipTests=false -DfailIfNoTests=false clean test

# Verify moqui-frontend build (durion-moqui-frontend)
cd ../durion-moqui-frontend
./gradlew clean test
```

**Security Validation (configuration only – do not expose secrets):**
```bash
# Verify JWT configuration references (do NOT print actual secret values)
grep -R "jwt" durion-positivity-backend/src/main/resources/ || true
grep -R "jwt" durion-moqui-frontend/runtime || true

# Spot-check encryption configuration where applicable (Spring Boot, Moqui)
grep -R "AES" durion-positivity-backend/src/main/resources/ || true
```

### 1.2 Deployment Sequence

**Step 1: Build and package workspace-agents**
```bash
cd durion/workspace-agents
mvn -e -U -DskipTests=false -DfailIfNoTests=false clean package
```

**Step 2: Build and deploy durion-positivity-backend**
```bash
cd ../durion-positivity-backend
./mvnw -e -U -DskipTests=false -DfailIfNoTests=false clean package
# Deployment is environment-specific (e.g. Kubernetes, ECS). Apply your manifests or pipeline here.
```

**Step 3: Build and deploy durion-moqui-frontend**
```bash
cd ../durion-moqui-frontend
./gradlew clean build
# Deploy updated moqui runtime or Docker images as per your environment runbooks.
```

**Step 4: Deploy workspace-agents service**
```bash
cd ../durion/workspace-agents
# Apply deployment manifests or Helm chart for the workspace-agents service
kubectl apply -f k8s/workspace-agents.yaml
kubectl rollout status deployment/workspace-agents

# Verify agent pods and basic readiness
kubectl get pods -l app=workspace-agents
kubectl logs -l app=workspace-agents --tail=50
```

**Step 5: End-to-End Validation (Story orchestration + integration)**
```bash
cd durion/workspace-agents

# Run Maven tests, including property tests such as PerformanceCoordinationPropertyTest
mvn -e -U test

# (Optional) Run any dedicated integration or property tests via VS Code task "maven: runPropertyTests".

# Validate Story Orchestration outputs in the durion repo
cd ../durion
ls .github/orchestration
# Expected: story-sequence.md, frontend-coordination.md, backend-coordination.md (kept in sync by StoryOrchestrationAgent)
```

### 1.3 Rollback Procedures

**Emergency Rollback (5 minutes maximum):**
```bash
# Rollback positivity service
aws ecs update-service --cluster positivity-cluster \
  --service positivity-service \
  --task-definition positivity:previous

# Rollback workspace agents
kubectl rollout undo deployment/workspace-agents

# Rollback moqui to previous version
systemctl stop moqui-server
cp /opt/moqui/runtime/backup/previous/* /opt/moqui/runtime/
systemctl start moqui-server

# Verify rollback success
./scripts/verify-rollback.sh
```

---

## 2. Monitoring and Alerting

### 2.1 Key Performance Indicators

**Response Time Monitoring:**
```bash
# Workspace agent response times for RequirementsDecompositionAgent
curl -w "@curl-format.txt" -s -o /dev/null \
  http://workspace-agents:8080/api/requirements/decompose

# Cross-layer integration times via FullStackIntegrationAgent test endpoint (if exposed)
time curl -X POST http://workspace-agents:8080/api/integration/test \
  -H "Content-Type: application/json" \
  -d '{"layers": ["vue", "durion-positivity", "positivity"]}' || true
```

**Availability Monitoring:**
```bash
# Health check workspace-agents Kubernetes deployment
kubectl get deployment workspace-agents
kubectl get pods -l app=workspace-agents

# (Optional) Project-level health checks, if endpoints are available
curl -f http://positivity-api:8080/actuator/health || true
curl -f http://moqui-server:8080/rest/health || true
```

### 2.2 Alert Configurations

**Critical Alerts (Immediate Response):**

1. **Response Time > 5 seconds (95th percentile)**
   ```yaml
   alert: WorkspaceAgentResponseTime
   expr: histogram_quantile(0.95, workspace_agent_request_duration_seconds) > 5
   for: 2m
   labels:
     severity: critical
   annotations:
     summary: "Workspace agent response time exceeded 5 seconds"
     runbook: "Section 3.2 - Performance Issues"
   ```

2. **Availability < 99.9%**
   ```yaml
   alert: WorkspaceAgentAvailability
   expr: (up{job="workspace-agents"} * 100) < 99.9
   for: 1m
   labels:
     severity: critical
   annotations:
     summary: "Workspace agent availability below 99.9%"
     runbook: "Section 3.1 - Service Unavailable"
   ```

3. **JWT Token Inconsistency**
   ```yaml
   alert: JWTTokenInconsistency
   expr: jwt_validation_failures_total > 0
   for: 30s
   labels:
     severity: critical
   annotations:
     summary: "JWT token validation failures detected"
     runbook: "Section 3.3 - Security Issues"
   ```

**Warning Alerts (Monitor Closely):**

1. **Concurrent Users > 80**
   ```yaml
   alert: HighConcurrentUsers
   expr: workspace_agent_concurrent_users > 80
   for: 5m
   labels:
     severity: warning
   annotations:
     summary: "High concurrent user load detected"
     runbook: "Section 3.2 - Performance Issues"
   ```

2. **Cross-Layer Integration Errors**
   ```yaml
   alert: CrossLayerIntegrationErrors
   expr: rate(integration_errors_total[5m]) > 0.01
   for: 2m
   labels:
     severity: warning
   annotations:
     summary: "Cross-layer integration errors detected"
     runbook: "Section 3.4 - Integration Issues"
   ```

### 2.3 Monitoring Dashboards

**Grafana Dashboard Configuration:**
```json
{
  "dashboard": {
    "title": "Workspace Agent Operations",
    "panels": [
      {
        "title": "Response Time (95th percentile)",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, workspace_agent_request_duration_seconds)"
          }
        ]
      },
      {
        "title": "Availability %",
        "targets": [
          {
            "expr": "up{job=\"workspace-agents\"} * 100"
          }
        ]
      },
      {
        "title": "Concurrent Users",
        "targets": [
          {
            "expr": "workspace_agent_concurrent_users"
          }
        ]
      },
      {
        "title": "Cross-Layer Integration Health",
        "targets": [
          {
            "expr": "rate(integration_success_total[5m])"
          }
        ]
      }
    ]
  }
}
```

---

## 3. Incident Response Procedures

### 3.1 Service Unavailable (Critical)

**Symptoms:**
- Workspace agents returning 5xx errors
- Health checks failing
- Unable to process requirements

**Immediate Actions (5 minutes):**
```bash
# Check service status
kubectl get pods -l app=workspace-agents
kubectl describe pods -l app=workspace-agents

# Check logs for errors
kubectl logs -l app=workspace-agents --tail=100 | grep ERROR

# Restart if necessary
kubectl rollout restart deployment/workspace-agents
kubectl rollout status deployment/workspace-agents
```

**Root Cause Analysis:**
```bash
# Check resource usage
kubectl top pods -l app=workspace-agents

# Check dependencies
curl -f http://positivity-api:8080/actuator/health
curl -f http://moqui-server:8080/rest/health

# Check configuration
kubectl get configmap workspace-agent-config -o yaml
```

**Resolution Steps:**
1. Verify all dependencies are healthy
2. Check resource limits and scaling
3. Validate configuration consistency
4. Restart services in dependency order
5. Run integration tests to verify recovery

### 3.2 Performance Issues (Warning/Critical)

**Symptoms:**
- Response times > 5 seconds
- High CPU/memory usage
- Slow requirement decomposition

**Immediate Actions (10 minutes):**
```bash
# Check performance metrics
curl http://workspace-agents:8080/actuator/metrics/http.server.requests

# Check resource usage
kubectl top pods -l app=workspace-agents

# Scale up if needed
kubectl scale deployment workspace-agents --replicas=5
```

**Performance Optimization:**
```bash
# Enable caching
kubectl patch configmap workspace-agent-config \
  --patch '{"data":{"cache.enabled":"true"}}'

# Tune JVM settings
kubectl patch deployment workspace-agents \
  --patch '{"spec":{"template":{"spec":{"containers":[{"name":"workspace-agents","env":[{"name":"JAVA_OPTS","value":"-Xmx2g -XX:+UseG1GC"}]}]}}}}'

# Restart to apply changes
kubectl rollout restart deployment/workspace-agents
```

### 3.3 Security Issues (Critical)

**Symptoms:**
- JWT validation failures
- Authentication errors across layers
- Security audit failures

**Immediate Actions (5 minutes):**
```bash
# Check JWT configuration
kubectl get secret jwt-secret -o yaml

# Verify AES-256 encryption
kubectl exec -it deployment/workspace-agents -- \
  java -cp /app/lib/* durion.workspace.agents.security.EncryptionValidator

# Check audit logs
kubectl logs -l app=workspace-agents | grep SECURITY
```

**Security Incident Response:**
1. **Isolate affected services** (2 minutes)
2. **Rotate JWT secrets** (5 minutes)
3. **Verify encryption keys** (3 minutes)
4. **Audit all authentication flows** (10 minutes)
5. **Generate security report** (15 minutes)

### 3.4 Integration Issues (Warning)

**Symptoms:**
- Cross-layer communication failures
- API contract violations
- Data synchronization errors

**Diagnostic Commands:**
```bash
# Test integration points
curl -X POST http://workspace-agents:8080/api/integration/test

# Check API contracts
java -cp build/libs/workspace-agents.jar \
  durion.workspace.agents.validation.APIContractValidator

# Verify data consistency
java -cp build/libs/workspace-agents.jar \
  durion.workspace.agents.validation.DataConsistencyChecker
```

**Resolution Steps:**
1. Validate API contracts between layers
2. Check data synchronization status
3. Verify JWT token consistency
4. Test end-to-end workflows
5. Update integration configurations if needed

---

## 4. Disaster Recovery Procedures

### 4.1 Backup Procedures

**Daily Automated Backups:**
```bash
#!/bin/bash
# /opt/scripts/daily-backup.sh

# Backup workspace agent configuration
kubectl get configmaps -o yaml > /backup/$(date +%Y%m%d)/configmaps.yaml
kubectl get secrets -o yaml > /backup/$(date +%Y%m%d)/secrets.yaml

# Backup positivity database
pg_dump -h positivity-db -U postgres positivity > /backup/$(date +%Y%m%d)/positivity.sql

# Backup moqui database
pg_dump -h moqui-db -U postgres moqui > /backup/$(date +%Y%m%d)/moqui.sql

# Backup workspace agent state
kubectl exec deployment/workspace-agents -- \
  java -cp /app/lib/* durion.workspace.agents.backup.StateExporter \
  > /backup/$(date +%Y%m%d)/agent-state.json
```

**Backup Verification:**
```bash
# Verify backup integrity
gzip -t /backup/$(date +%Y%m%d)/*.gz
psql -h backup-db -f /backup/$(date +%Y%m%d)/positivity.sql --dry-run

# Test restore procedure (staging environment)
./scripts/test-restore.sh /backup/$(date +%Y%m%d)
```

### 4.2 Recovery Procedures

**RTO Target: 4 hours maximum**
**RPO Target: 1 hour maximum**

**Phase 1: Infrastructure Recovery (60 minutes)**
```bash
# Restore Kubernetes cluster
kubectl apply -f /backup/latest/configmaps.yaml
kubectl apply -f /backup/latest/secrets.yaml

# Restore databases
psql -h positivity-db -U postgres -f /backup/latest/positivity.sql
psql -h moqui-db -U postgres -f /backup/latest/moqui.sql

# Deploy workspace agents
kubectl apply -f k8s/workspace-agents.yaml
kubectl rollout status deployment/workspace-agents
```

**Phase 2: Service Recovery (90 minutes)**
```bash
# Restore positivity services
aws ecs update-service --cluster positivity-cluster \
  --service positivity-service --desired-count 3

# Restore moqui server
systemctl start moqui-server
sleep 60

# Restore workspace agent state
kubectl exec deployment/workspace-agents -- \
  java -cp /app/lib/* durion.workspace.agents.backup.StateImporter \
  /backup/latest/agent-state.json
```

**Phase 3: Validation and Testing (90 minutes)**
```bash
# Run comprehensive validation
cd workspace-agents
java -cp build/libs/workspace-agents.jar \
  durion.workspace.agents.validation.DisasterRecoveryValidator

# Test critical workflows
./scripts/test-critical-workflows.sh

# Verify performance targets
./scripts/performance-validation.sh
```

### 4.3 Failover Procedures

**Automatic Failover (5 minutes):**
```bash
# Kubernetes automatic failover
kubectl get pods -l app=workspace-agents -w

# AWS Fargate automatic failover
aws ecs describe-services --cluster positivity-cluster \
  --services positivity-service
```

**Manual Failover (15 minutes):**
```bash
# Switch to backup region
export AWS_DEFAULT_REGION=us-west-2
kubectl config use-context backup-cluster

# Update DNS to point to backup
aws route53 change-resource-record-sets \
  --hosted-zone-id Z123456789 \
  --change-batch file://failover-dns.json

# Verify failover success
./scripts/verify-failover.sh
```

---

## 5. Maintenance Procedures

### 5.1 Regular Maintenance Tasks

**Weekly Tasks:**
```bash
# Update workspace agent dependencies
cd workspace-agents
./gradlew dependencyUpdates
./gradlew build test

# Clean up old logs
kubectl delete pods -l app=workspace-agents --field-selector=status.phase=Succeeded

# Verify security configurations
java -cp build/libs/workspace-agents.jar \
  durion.workspace.agents.security.SecurityAuditor
```

**Monthly Tasks:**
```bash
# Performance optimization review
java -cp build/libs/workspace-agents.jar \
  durion.workspace.agents.performance.PerformanceAnalyzer

# Security vulnerability scan
./scripts/security-scan.sh

# Disaster recovery test
./scripts/dr-test.sh
```

### 5.2 Capacity Planning

**Scaling Triggers:**
- Concurrent users > 80: Scale to 5 replicas
- Response time > 4 seconds: Scale to 7 replicas
- CPU usage > 70%: Scale to 10 replicas

**Scaling Commands:**
```bash
# Manual scaling
kubectl scale deployment workspace-agents --replicas=5

# Horizontal Pod Autoscaler
kubectl autoscale deployment workspace-agents \
  --cpu-percent=70 --min=3 --max=10
```

---

## 6. Troubleshooting Guide

### 6.1 Common Issues

**Issue: Requirements Decomposition Agent Slow**
```bash
# Check RequirementsDecompositionAgent performance
curl -w "@curl-format.txt" -s -o /dev/null \
  http://workspace-agents:8080/api/requirements/decompose

# Optional: enable or tune decomposition caching via configmap (if supported)
kubectl patch configmap workspace-agent-config \
  --patch '{"data":{"decomposition.cache.size":"1000"}}' || true
```

**Issue: Cross-Layer Authentication Failures**
```bash
# Verify JWT consistency
kubectl exec deployment/workspace-agents -- \
  java -cp /app/lib/* durion.workspace.agents.security.JWTValidator

# Synchronize JWT secrets
kubectl create secret generic jwt-secret \
  --from-literal=key=$(openssl rand -base64 32) \
  --dry-run=client -o yaml | kubectl apply -f -
```

**Issue: API Contract Violations**
```bash
cd durion/workspace-agents

# Validate contracts via workspace-agents validation utilities (if present)
mvn -e -U -Dtest=*Contract* test || true

# If failures relate to cross-repo contracts, consult:
# - durion/.github/orchestration/story-sequence.md
# - durion/.github/orchestration/frontend-coordination.md
# - durion/.github/orchestration/backend-coordination.md
# and update the underlying API contracts in durion-positivity-backend and durion-moqui-frontend accordingly.
```

### 6.3 Kiro and Agent-Structure Orchestration

The durion workspace uses Kiro-based scripts to drive structured agent work based on `.kiro/specs` task plans.

**Workspace-level agent structure (durion repo):**
```bash
cd durion

# Run one Kiro step for the workspace agent structure plan
MAX_STEPS=1 ./kiro-run-agent-structure.zsh

# This consumes tasks from .kiro/specs/workspace-agent-structure/tasks.md
# and updates the associated HANDOFF.md file for human/agent handover.
```

**Project-level agent structures:**
```bash
# Backend agent structure (durion-positivity-backend)
cd durion-positivity-backend
MAX_STEPS=1 ./kiro-run-agent-structure.zsh

# Frontend agent structure (durion-moqui-frontend)
cd ../durion-moqui-frontend
MAX_STEPS=1 ./kiro-run-agent-structure.zsh

# Each run advances exactly one unchecked task from
# the corresponding .kiro/specs/agent-structure/tasks.md file.
```

### 6.2 Emergency Contacts

**On-Call Rotation:**
- Primary: DevOps Team (+1-555-0100)
- Secondary: Platform Team (+1-555-0200)
- Escalation: Engineering Manager (+1-555-0300)

**Escalation Matrix:**
- **Critical (P0)**: Immediate response, all hands
- **High (P1)**: 15-minute response, on-call team
- **Medium (P2)**: 1-hour response, business hours
- **Low (P3)**: Next business day

---

## 7. Performance Optimization

### 7.1 Caching Strategies

**Multi-Layer Caching:**
```bash
# Enable Spring Cache (positivity)
kubectl patch configmap positivity-config \
  --patch '{"data":{"spring.cache.type":"redis"}}'

# Enable Moqui Entity Cache
kubectl patch configmap moqui-config \
  --patch '{"data":{"entity_cache_size":"10000"}}'

# Enable Vue.js Component Caching
kubectl patch configmap frontend-config \
  --patch '{"data":{"vue.cache.enabled":"true"}}'
```

### 7.2 Database Optimization

**Connection Pool Tuning:**
```bash
# Positivity database
kubectl patch configmap positivity-config \
  --patch '{"data":{"spring.datasource.hikari.maximum-pool-size":"20"}}'

# Moqui database
kubectl patch configmap moqui-config \
  --patch '{"data":{"database_max_connections":"50"}}'
```

---

## 8. Security Hardening

### 8.1 Security Checklist

**Daily Security Checks:**
```bash
# Verify JWT token rotation
kubectl get secret jwt-secret -o jsonpath='{.metadata.creationTimestamp}'

# Check for security vulnerabilities
./scripts/security-scan.sh

# Verify AES-256 encryption
kubectl exec deployment/workspace-agents -- \
  java -cp /app/lib/* durion.workspace.agents.security.EncryptionValidator
```

### 8.2 Compliance Validation

**Audit Trail Verification:**
```bash
# Check audit logs completeness
kubectl logs -l app=workspace-agents | grep AUDIT | wc -l

# Verify RBAC compliance
kubectl auth can-i --list --as=system:serviceaccount:default:workspace-agent
```

---

This operations runbook provides comprehensive procedures for maintaining the workspace agent system with 99.9% availability, <5 second response times, and robust disaster recovery capabilities. All procedures are designed to meet the 4-hour RTO and 1-hour RPO requirements while maintaining security and performance standards across the three-tier architecture.
