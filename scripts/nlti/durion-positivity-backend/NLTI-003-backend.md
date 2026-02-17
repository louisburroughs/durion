repo: louisburroughs/durion-positivity-backend
title: "[STORY] Authorized Tool Registry API (tool descriptors + RBAC filtering)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - agent:story-authoring
  - agent:architecture
  - agent:security
---

## Story Intent (strengthened)
Provide a versioned, queryable tool/action registry (`ToolDescriptorV1`) that NLTI planner and executor can query. Registry responses must be filtered by the requesting subject's authorization so planning and execution only reference permitted actions and avoid leaking capability existence.

## Core Requirements
- Registry model includes `actionId`, `description`, `inputSchema`, `outputSchema`, `riskLevel`, `requiredPermissions[]`, and `version`.
- Discovery API: `GET /nlt/v1/tools?service=workexec` returns only actions the subject is authorized to call.
- Authorization check must be applied both at discovery time and at invocation time (defense in depth).

## Acceptance Criteria
- Given a user with permission set A, discovery returns only actions allowed by A.
- If AuthZ service is unavailable, discovery fails closed (return 503) and logs correlationId.
- Unauthorized invocation attempts return `NOT_AUTHORIZED` with correlationId and do not call downstream.

## Test Scenarios & Contract Tests
- Contract tests validate input/output schemas for each adapter action.
- Integration tests validate that discovery respects RBAC mappings using a test AuthZ stub.

## Observability & Audit
- Log discovery with correlationId, subjectId, and count of returned actions.
- Metric: `nlt.registry.discovery.latency_ms` and `nlt.registry.discovery.denial_count`.

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Authorized Tool Registry + Discovery"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language

## Story Intent
As a Positivity user, I want NLTI to only use tools and actions I am authorized 
to access so that requests are executed safely and I do not see or invoke restricted operations.

## Actors & Stakeholders
- Primary: Authenticated Positivity user
- Stakeholders: Security/AuthZ team, Platform engineering, Domain service owners

## Functional Behavior
- Maintain a registry of tool/action descriptors (name, purpose, inputs/outputs,
 risk flags)
- Filter tools/actions returned to NLTI by the user’s permissions
- Provide a consistent “not authorized” message and optional “request access” guidance

## Acceptance Criteria
- Given a user with limited permissions, when NLTI requests available tools, then
  only authorized tools/actions are returned.
- Given a user attempts a restricted action, when evaluated, then NLTI blocks the action.

