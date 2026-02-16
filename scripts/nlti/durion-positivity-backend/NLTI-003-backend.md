---
repo: louisburroughs/durion-positivity-backend
title: "[STORY] Authorized Tool Registry API (tool descriptors + RBAC filtering)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:backend
  - agent:security
  - agent:architecture
  - agent:story-authoring
---

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:positivity
- status:draft

### Recommended
- agent:backend
- agent:security
- agent:architecture
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As a Positivity NLTI system, I want a centralized registry of tool/action descriptors that is filtered by user authorization so that planning and execution can only reference capabilities the user is permitted to use.

## Actors & Stakeholders
- **Primary actor:** NLTI service (planner/executor) requesting authorized tool lists.
- **Security stakeholder:** AuthZ policy owner (RBAC/ABAC).
- **Tool owners:** Domain teams exposing actions (workexec, accounting, inventory, crm).

## Preconditions
- AuthN provides a user identity/subject to evaluate permissions.
- Existing authorization system can evaluate “user can perform action X” (directly or via roles/scopes).
- NLTI service has an internal call path to the Tool Registry.

## Functional Behavior
1. **Tool Descriptor Model**
   - Define a versioned `ToolDescriptor` model with:
     - `toolId`, `toolName`
     - `actions[]`, each with:
       - `actionId`, `displayName`, `description`
       - `inputSchemaRef` and `outputSchemaRef` (references; may be placeholders)
       - `riskLevel` (`LOW|MEDIUM|HIGH`)
       - `requiredPermissions[]` (strings/scopes/roles)
2. **Registry Storage**
   - Provide a registry that can be populated via configuration (initially acceptable) and is queryable at runtime.
3. **Authorized Discovery API**
   - Expose an API used by NLTI components (e.g., `GET /nlt/v1/tools`) that:
     - Evaluates permissions for the current user
     - Returns only tools/actions the user is authorized to invoke
4. **Authorization Failure Handling**
   - Provide a standard error payload for unauthorized tool/action invocation attempts:
     - `errorCode = NOT_AUTHORIZED`
     - human-friendly message
     - correlationId
5. **No Implicit Tool Visibility**
   - Tools/actions not authorized MUST NOT be returned in discovery responses (avoid hinting at existence).

## Alternate / Error Flows
- **AuthZ service unavailable:** return an error response preventing tool discovery; do not fall back to “allow all”.
- **Malformed tool descriptor config:** fail startup (or fail discovery) with clear logs; do not expose partial inconsistent registry.
- **User without any tool permissions:** return empty list with message indicating no available actions.

## Business Rules
- Authorization filtering MUST be applied to both:
  - Tool discovery results
  - Any direct attempt to invoke an action (future)
- Registry MUST support risk metadata for downstream confirmation gating.

## Data Requirements
- `ToolDescriptor` and `ToolActionDescriptor` versioned structures.
- `requiredPermissions[]` definitions aligned to existing RBAC/ABAC naming.
- Registry source: configuration file or internal store (implementation choice), but must be deterministic and testable.

## Acceptance Criteria
- **Given** a user with permission set A, **when** they call tool discovery, **then** only actions requiring permissions in A are returned.
- **Given** a user lacking permission for an action, **when** NLTI attempts to reference that action, **then** the system returns `NOT_AUTHORIZED` with correlationId.
- **Given** AuthZ evaluation fails, **when** discovery is called, **then** the system returns an error and does not return an unfiltered tool list.
- **Given** a tool/action is unauthorized, **when** discovery is called, **then** it is not present in the response (no leakage).

## Audit & Observability
- Log tool discovery requests with:
  - correlationId, user id, count of authorized tools/actions returned
- Log authorization denials with:
  - correlationId, user id, actionId, decision outcome
- Metrics:
  - discovery latency
  - denial rate

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Authorized Tool Registry + Discovery"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want NLTI to only use tools and actions I am authorized to access so that requests are executed safely and I do not see or invoke restricted operations.

## Actors & Stakeholders
- Primary: Authenticated Positivity user
- Stakeholders: Security/AuthZ team, Platform engineering, Domain service owners

## Functional Behavior
- Maintain a registry of tool/action descriptors (name, purpose, inputs/outputs, risk flags)
- Filter tools/actions returned to NLTI by the user’s permissions
- Provide a consistent “not authorized” message and optional “request access” guidance

## Acceptance Criteria
- Given a user with limited permissions, when NLTI requests available tools, then only authorized tools/actions are returned.
- Given a user attempts a restricted action, when evaluated, then NLTI blocks the action with an authorization error explanation.