---
type: Runbook
title: NLTI Runbook — Confirmation Gate Mismatch
domain: general
status: draft
tags: [mcp-server, general]
---

# NLTI Runbook — Confirmation Gate Mismatch

> **Status: draft skeleton (2026-03).** Triggers and metric names are reconciled in
> [NLTI alert rules](../alerts/nlti-alerts.md), which is authoritative. As of 2026-09-16 only the Loki rules in
> `observability/loki/rules/nlti-alerts.yml` are provisioned; `observability/prometheus.yml` loads no rule files, so
> the Prometheus-triggered alerts named below (`HighNLTIErrorRate`, `AuditWriteFailuresDetected`,
> `NLTIPlanningOrExecutionLatencyAnomaly`) do not fire. `nlt.planning.latency` and `nlt.execution.latency` are
> registered but never sampled. Paths in backticks refer to `durion-positivity-backend`.

## Purpose

Runbook for handling confirmation token mismatches or cross-user confirmation attempts resulting in HTTP 403.

## Symptoms

- Requests failing with HTTP 403 at confirmation endpoints
- Logs show `confirmation token mismatch`, `user mismatch`, or `invalid confirmation` entries
- Potential security event if cross-user confirmations are attempted

## Detection

- Application logs with confirmation validation failures and correlation IDs
- HTTP 403s on the confirmation endpoints (`http_server_requests_seconds_count{status="403"}`), correlated with the
  validation-failure log lines above (`nlt.error.count` is untagged and cannot isolate confirmation failures)
- Possible security alerts for suspicious cross-user attempts

## Immediate Actions

1. Collect request IDs, correlation IDs, session IDs and user IDs. Never copy raw confirmation or bearer tokens into an
   incident record; when correlation needs a token, use an approved fingerprint (for example a truncated hash).
2. Verify if token expiration is expected behavior or indicates misrouted confirmation.
3. If suspicious activity detected, block offending sessions and escalate to Security.
4. Inform user-facing support teams with safe guidance to re-initiate confirmation flow.

## Escalation

- Escalate to Security team for potential account compromise or malicious activity.
- Provide redacted evidence, timestamps, and affected user IDs.

## Rollback / Recovery

- If a code change introduced a token validation regression, revert the change and reissue confirmation tokens as needed.
- Offer manual confirmation processes for high-priority cases following security review.

## Post-Incident Notes

- Record whether issue was user error, race-condition, or security incident.
- Update confirmation flow documentation and add tests to prevent regressions.
