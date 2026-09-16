---
type: Runbook
title: NLTI Runbook — AuthZ Outage
domain: general
status: draft
tags: [mcp-server, general]
---

# NLTI Runbook — AuthZ Outage

> **Status: draft skeleton (2026-03).** Triggers and metric names are reconciled in
> [NLTI alert rules](../alerts/nlti-alerts.md), which is authoritative. As of 2026-09-16 only the Loki rules in
> `observability/loki/rules/nlti-alerts.yml` are provisioned; `observability/prometheus.yml` loads no rule files, so
> the Prometheus-triggered alerts named below (`HighNLTIErrorRate`, `AuditWriteFailuresDetected`,
> `NLTIPlanningOrExecutionLatencyAnomaly`) do not fire. `nlt.planning.latency` and `nlt.execution.latency` are
> registered but never sampled. Paths in backticks refer to `durion-positivity-backend`.

## Purpose

Runbook for handling an authorization (AuthZ) service outage that causes the NLTI flow to fail-closed and return HTTP 503.

## Symptoms

- NLTI requests returning HTTP 503 with gateway/authorization errors
- `nlt.error.count` increase correlated with 503 responses
- Logs show AuthZ timeouts or 5xx responses from auth service

## Detection

- Alert: `HighNLTIErrorRate` firing
- Grafana panel: `nlt.request.count` with 503 status tag spike
- Check logs for `AuthZ` timeout or `403->503` mapping

## Immediate Actions

1. Confirm scope: query `/health` of AuthZ and check recent deploys.
2. If AuthZ is degraded, switch to tolerant/maintenance mode if supported by feature flag.
3. If fail-closed configuration must remain, notify on-call and advise users of degraded functionality.
4. Capture logs and request IDs for affected requests for post-mortem.

## Escalation

- If outage > 5 minutes or sensitive operations blocked, escalate to Security/AuthZ team and SRE on-call.
- Provide request samples, correlation IDs, and timestamps.

## Rollback / Recovery

- If a recent AuthZ deploy caused regression, coordinate rollback with AuthZ owners.
- If temporary whitelist or fallback auth token is available, use per-org emergency overrides following policy.

## Post-Incident Notes

- Record duration, root cause, mitigation steps taken, and any changes to runbook.
- Add new observability checks if detection was slow.
