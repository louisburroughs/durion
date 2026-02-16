---
repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Intent Model + Clarification State Machine (parse + slot fill)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:backend
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
- agent:architecture
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As a Positivity NLTI user, I want my natural language request transformed into a versioned, structured intent and (when needed) a clarification dialogue so that downstream planning and execution can be deterministic and safe.

## Actors & Stakeholders
- **Primary actor:** Authenticated user issuing NLTI requests.
- **System:** NLTI service (intent parser + dialogue manager).
- **Stakeholders:** Security (risk gating), domain services (future tool callers), QA (testable behaviors).

## Preconditions
- NLTI request endpoint exists (Capability 01).
- Session ID and correlation ID are available on each request/response.
- No tool execution is required in this story.

## Functional Behavior
1. **Intent Classification**
   - For each NLTI request, classify into one of:
     - `QUERY` (informational)
     - `ACTION` (requires plan/execution)
     - `UNKNOWN` (cannot be determined safely; requires clarification)
2. **Entity / Slot Extraction**
   - Extract candidate slots (when present):
     - `timeRange` (e.g., today/yesterday/date)
     - `entityType` (e.g., invoice, work order, customer)
     - `entityRef` (id/name/number tokens)
     - `operation` (e.g., list, close, delete, reprocess)
   - Output must include confidence per slot (high/medium/low).
3. **Clarification Triggering**
   - If required slots are missing OR multiple plausible interpretations exist, return:
     - `intent.status = NEEDS_CLARIFICATION`
     - `clarification.question`
     - `clarification.options[]` when feasible (2+ options)
4. **Clarification State Machine**
   - Persist (in-memory or session-scoped) a “pending clarification” state for the session:
     - `PENDING_CLARIFICATION` → `CLARIFIED` (when user answers) → `READY_FOR_PLANNING`
   - User replies to a clarification question are processed to fill missing slots.
5. **Risk Classification (for downstream gating)**
   - Assign `riskLevel` to intent:
     - `LOW` (read-only queries)
     - `MEDIUM` (bounded updates)
     - `HIGH` (destructive/bulk/financial-impact)  
   - This story only produces `riskLevel`; confirmation UX is handled in a later capability.

## Alternate / Error Flows
- **Unparseable input:** return `UNKNOWN` with a clarifying question (“What are you trying to accomplish?”) and example prompts.
- **User answers clarification with irrelevant text:** remain in `PENDING_CLARIFICATION` and ask again with simplified options.
- **Session missing:** if sessionId is not present, treat as new session and proceed (Capability 01 behavior).

## Business Rules
- NLTI MUST avoid unsafe assumptions when confidence is low; prefer clarification.
- NLTI MUST not execute tool actions in this story.
- Intent schema MUST be versioned to support contract stability.

## Data Requirements
- `IntentV1` (versioned structure) including:
  - `intentType` (`QUERY|ACTION|UNKNOWN`)
  - `operation` (string)
  - `slots` map with values + confidence
  - `riskLevel` (`LOW|MEDIUM|HIGH`)
  - `status` (`READY|NEEDS_CLARIFICATION`)
  - `clarification` object when needed
- Session-scoped storage for:
  - pending clarification prompt
  - candidate options (if generated)
  - last intent draft

## Acceptance Criteria
- **Given** a clear read-only request (e.g., “Show unpaid invoices for today”), **when** NLTI parses it, **then** it returns `intentType=QUERY`, `status=READY`, and extracted slots with confidence.
- **Given** an ambiguous request (e.g., “Close those work orders”), **when** NLTI parses it, **then** it returns `status=NEEDS_CLARIFICATION` with a question and at least two options when feasible.
- **Given** a clarification is pending, **when** the user provides a valid answer (e.g., “Today’s completed ones”), **then** NLTI transitions to `READY` and fills the missing slots.
- **Given** a request appears destructive/bulk (e.g., “Delete all draft invoices”), **when** NLTI parses it, **then** it sets `riskLevel=HIGH`.

## Audit & Observability
- Emit structured logs per request with:
  - correlationId, sessionId
  - intentType, status, riskLevel
  - whether clarification was triggered
- Record clarification transitions (pending → clarified) as separate log events keyed by correlationId.

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Intent Parsing + Clarification Dialogue"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want NLTI to interpret my request into an actionable intent and ask clarifying questions when needed so that my goals are executed correctly without unsafe assumptions.

## Actors & Stakeholders
- Primary: Authenticated Positivity user
- Stakeholders: Platform engineering, Security, Domain teams, Support/Operations

## Functional Behavior
- Classify input as “question” vs “action”
- Extract key entities (dates, identifiers, customer/work order/invoice references)
- When intent is ambiguous or missing required details, ask targeted clarification questions and present selectable options

## Acceptance Criteria
- Given an ambiguous request, when NLTI cannot safely infer parameters, then it asks a clarification question.
- Given a clear request, when NLTI parses it, then it produces a structured intent representation.
- Given a request that appears risky, when parsed, then NLTI marks it as requiring confirmation before execution