repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Intent Model + Clarification State Machine (parse + slot fill)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - agent:story-authoring
  - capability:natural-language
---

## Story Intent (strengthened)
Convert freeform user requests into a deterministic, versioned `IntentV1` with slot extraction, confidence scoring, and a clarification state machine that can persist transient clarification state per session until the intent reaches `READY` or is cancelled.

## Key Behavior
- Intent classification (`QUERY|ACTION|UNKNOWN`) with confidence.
- Slot extraction with per-slot confidence (high/medium/low) and canonicalization.
- Clarification state machine enabling multi-turn slot filling: store pending clarification in session until answered or expired.
- Risk classification (`LOW|MEDIUM|HIGH`) produced with rationale for downstream gating.

## Acceptance Criteria (explicit)
- Given a clear read-only request, parser returns `intentType=QUERY`, `status=READY`, and slots with confidence.
- Given ambiguous request, parser returns `status=NEEDS_CLARIFICATION` with suggested clarifying questions and at least two options when feasible.
- Given user answers clarification, the system transitions to `READY` and fills missing slots, or remains in `PENDING_CLARIFICATION` if ambiguous.
- Risk detection: destructive or bulk intents are marked `HIGH` and flagged for confirmation.

## Test Scenarios
- Unit: slot extraction accuracy with sample utterances and confidence thresholds.
- Integration: clarification flow end-to-end (ASK → USER REPLY → READY).

## Security & Privacy
- Do not persist raw user prompt unless allowed by audit policy; store minimal intent + hashed prompt for deduplication if needed.

## Observability
- Emit `nlt.intent.parse.count`, `nlt.intent.clarification.count`, and histogram for slot confidences.

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Intent Model + Clarification State Machine (parse + slot fill)"
labels:
  - type:story
  - agent:story-authoring

/* Original details summarized for traceability; see repository for full original. */
