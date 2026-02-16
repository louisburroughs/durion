---
repo: louisburroughs/durion
title: "[STORY] NLTI Execution Orchestrator (Safe Step Runner + Idempotency)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want NLTI to safely execute approved plans across multiple tools so that my goals are completed reliably and transparently.

## Functional Behavior
- Execute ordered plan steps
- Propagate correlationId
- Support idempotency and retries
- Handle partial failures and report results

## Acceptance Criteria
- Given an approved plan, when execution begins, then steps execute in order.
- Given a transient failure, when retryable, then the system retries per policy.
- Given partial failure, when execution completes, then a structured result summary is returned.