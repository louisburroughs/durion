repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Observability & Operations (Metrics, Tracing, Alerts, Runbooks)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - agent:sre
  - agent:backend
  - agent:story-authoring
---

## Story Intent (strengthened)
Establish a baseline observability contract for NLTI: required metrics, tracing attributes, dashboards and runbooks so the platform can be operated reliably in production.

## Metrics & Traces (required)
- Metrics: `nlt.request.count`, `nlt.request.latency_ms`, `nlt.planning.latency_ms`, `nlt.execution.latency_ms`, `nlt.error.count`, `nlt.audit.write_failures`.
- Traces must include `correlationId`, `requestId`, `userId`, `actionId` (when applicable), and `planId`/`executionId` span attributes.

## Dashboards & Alerts
- Dashboards should show p50/p95/p99 latency, error rates, top failing actionIds, and audit write failures.
- Alerts: sustained high error rate, sustained latency regression, audit write failure spike, confirmation gate failures.

## Runbooks
- Provide step-by-step remediation for top failure modes: AuthZ outage, downstream tool timeouts, audit storage failure, planning failures, confirmation gate mismatches.

## Acceptance Criteria
- Dashboards and alerts configured and verified in staging.
- Runbooks written and validated by SRE on tabletop exercises.

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Observability & Operations (Metrics, Tracing, Alerts, Runbooks)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language

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

