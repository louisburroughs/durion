---
type: Runbook
title: NLTI Runbook — Planning Failure
domain: general
status: draft
tags: [mcp-server, general]
---

# NLTI Runbook — Planning Failure

> **Status: draft skeleton (2026-03).** Triggers and metric names are reconciled in
> [NLTI alert rules](../alerts/nlti-alerts.md), which is authoritative. As of 2026-09-16 only the Loki rules in
> `observability/loki/rules/nlti-alerts.yml` are provisioned; `observability/prometheus.yml` loads no rule files, so
> the Prometheus-triggered alerts named below (`HighNLTIErrorRate`, `AuditWriteFailuresDetected`,
> `NLTIPlanningOrExecutionLatencyAnomaly`) do not fire. `nlt.planning.latency` and `nlt.execution.latency` are
> registered but never sampled. Paths in backticks refer to `durion-positivity-backend`.

## Purpose

Runbook for NLTI planning engine failures or cases where the planner returns an empty plan.

## Symptoms

- Planner errors in the logs, or chat turns ending without a plan
- Increased `nlt.error.count`; it is untagged, so attribute the stage from logs and the `nlti.request.telemetry` stream
  (`nlt.planning.latency` is registered but never sampled)
- Responses indicating inability to generate plan or prompts for clarification repeatedly

## Detection

- Alert: the Gate 7 Loki rules in the alert guide that route here (`HighNLTIErrorRate` is not provisioned)
- Planner logs showing exceptions, model rate limits, or invalid input errors
- Telemetry: `nlt.intent.clarification.count` abnormal rise

## Immediate Actions

1. Capture planner logs, request payloads (redact PII) and model/service errors.
2. If model provider is degraded, switch to fallback model provider if configured.
3. If input parsing produced malformed intent, trigger intent clarification path rather than failing.
4. Restart planner service/component if it's a transient internal error.

## Escalation

- Escalate to ML/Planner service owner if planner service is unavailable or model provider reports outage.
- Provide payload samples, timestamps, and correlation IDs.

## Rollback / Recovery

- If a recent change to planner code caused regressions, roll back to last known-good version.
- Re-run any queued planning requests after recovery, ensuring idempotency.

## Post-Incident Notes

- Document root cause (model rate-limit, parsing bug, bad prompts), and any prompt/pipeline fixes.
- Add unit/integration tests for malformed inputs and fallback provider behavior.
