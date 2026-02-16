## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:positivity
- status:draft

### Recommended
- agent:sre
- agent:backend
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As the NLTI platform operator, I want standardized metrics, tracing, alerting, and runbooks so that NLTI can be operated reliably in production.

## Actors & Stakeholders
- **Primary actor:** SRE / Operations.
- **Secondary:** Developers responding to incidents.
- **Stakeholders:** Security, product leadership.

## Preconditions
- CorrelationId exists for all NLTI requests.
- Logging baseline exists.

## Functional Behavior
1. **Metrics**
   - Emit metrics for:
     - request_count
     - request_latency_ms
     - error_rate
     - planning_latency_ms
     - execution_latency_ms
     - tool_call_latency_ms (per actionId)
2. **Tracing**
   - Ensure correlationId is present in traces and propagated into downstream calls.
3. **Dashboards**
   - Provide dashboards showing:
     - volume, latency percentiles, error rate
     - top failing actions
     - retry counts
4. **Alerts**
   - Define alerts for:
     - high error rate over window
     - sustained latency increase
     - audit write failures
5. **Runbooks**
   - Document runbooks for top 5 failure modes:
     - authz outage
     - downstream tool timeouts
     - audit storage failure
     - elevated parsing failures
     - confirmation gate mismatch

## Alternate / Error Flows
- Telemetry backend unavailable → degrade gracefully; do not crash NLTI.

## Business Rules
- Observability must not leak PII or secrets.
- Alerts must include correlation and actionable links/queries.

## Data Requirements
- Standard labels/tags: service name, environment, tenantId (if applicable), actionId.

## Acceptance Criteria
- **Given** NLTI traffic, **when** dashboards are viewed, **then** they show volume/latency/error rate.
- **Given** an error spike, **when** alert thresholds are crossed, **then** an alert fires with relevant context.
- **Given** a common incident type, **when** runbook is followed, **then** it contains steps to isolate and mitigate.

## Audit & Observability
- This story primarily defines observability; also audit changes to alert configs if stored.

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Observability & Operations (Metrics, Tracing, Alerts, Runbooks)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As an operator, I want NLTI to be observable and supportable so that failures can be detected quickly and resolved with clear runbooks.

## Functional Behavior
- Collect metrics and traces (correlationId)
- Dashboard for NLTI KPIs
- Alerts for failure rates and latency
- Document runbooks for common failure scenarios

## Acceptance Criteria
- Given NLTI traffic, when observed, then dashboards show request volume, latency, and error rate.
- Given failure spikes, when thresholds are breached, then alerts fire with actionable context.