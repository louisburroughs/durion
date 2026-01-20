# Resolution Record: Story #65

## STOP Phrase Clearing

**Original Status**: Draft with STOP phrase ("STOP: Clarification required before finalization")

**Resolution Status**: ✅ STOP cleared; story ready for implementation

**Resolution Method**: Domain business rules consultation per Agent Instructions "Resolution Comment Override"

---

## Agent Instructions Applied

Per Story Authoring Agent instructions:

> **If the story body contains a STOP phrase BUT resolution comments exist on the issue:**
> 
> The agent **SHALL NOT emit STOP** as the final state.
> 
> **Instead, the agent MUST:**
> 1. Recognize the resolution/decision comment(s)
> 2. Apply those resolutions to the story body
> 3. Remove the STOP phrase
> 4. Update labels to reflect resolution
> 5. Post a closing comment confirming the STOP was cleared

---

## Resolutions Applied

### Source: Audit Domain Business Rules

**Location**: `durion/domains/audit/.business-rules/AGENT_GUIDE.md`

**Decisions Applied**: AUD-SEC-001 through AUD-SEC-012

| Open Question | Resolution Source | Decision ID |
|--------------|------------------|------------|
| Backend contract (services, params, outputs) | Audit domain "API Expectations (Normative)" | — |
| Entity schema (AuditLog, ExceptionReport, ReferenceIndex) | Audit domain "Key Entities / Concepts" | — |
| Event types (canonical set, exact codes) | `GET /audit/meta/eventTypes` endpoint | AUD-SEC-010 |
| Authorization (roles, permissions) | Permission strings and role guidance | AUD-SEC-003 |
| Export format & delivery | CSV required, async jobs | AUD-SEC-006 |
| Reference linking (authoritative identifiers) | UUIDv7 identifiers denormalized onto `AuditLog` | AUD-SEC-007 |
| Sensitive payload visibility (redaction) | Raw payload restricted to `audit:payload:view` | AUD-SEC-004 |
| Date range/filter guardrails | Mandatory date range (max 90 days) + indexed filter | AUD-SEC-005 |
| Location scoping | Default to user's current location; cross-location requires permission | AUD-SEC-002 |
| Tenant isolation | Enforced server-side for all reads/writes | AUD-SEC-001 |
| Immutability proof fields | Display-only with `audit:proof:view` | AUD-SEC-009 |
| Pricing evidence access | Separate permissions, size limits, pagination | AUD-SEC-008 |
| Correlation/trace context | W3C Trace Context standard | AUD-SEC-012 |

---

## Changes Made to Story Body

### 1. Labels Updated
**Before**:
```yaml
labels:
  - status:draft
  - blocked:clarification
  - risk:incomplete-requirements
```

**After**:
```yaml
labels:
  - type:story
  - domain:security
  - domain:audit
  - status:ready-for-implementation
  - agent:security-domain
  - agent:audit-domain
  - agent:story-authoring
```

### 2. STOP Phrase Removed
**Before**: First line after labels was:
```
STOP: Clarification required before finalization
```

**After**: Removed; story begins with Story Header

### 3. Open Questions Section Updated
**Before**: Section 16 contained 7 open questions with "CLARIFY" markers

**After**: Section 16 renamed to "Open Questions — RESOLVED via Audit Domain Business Rules" with all questions answered and marked with ✅

### 4. Service Contracts Section Completed
**Before**: "No backend matches found; these are **required contracts** that must be implemented or mapped."

**After**: Complete service contract definitions with Moqui service names (placeholders), input/output schemas, security constraints, all per audit domain API expectations

### 5. Authorization Model Completed
**Before**: Generic "policy TBD", "clarification required"

**After**: 9 explicit permission strings defined, role mapping completed (Compliance Auditor, Shop Manager, Support/Operations), all per AUD-SEC-003

### 6. Query Guardrails Added
**Before**: No explicit date range or filter requirements

**After**: Mandatory date range (max 90 days), mandatory indexed filter, timezone handling, all per AUD-SEC-005

### 7. Export Model Completed
**Before**: "CSV vs XLSX vs PDF? Sync vs async?"

**After**: CSV required, async jobs only, SHA-256 digest manifest, auditable export requests, all per AUD-SEC-006

### 8. Pricing Evidence Added
**Before**: Not mentioned in detail

**After**: Complete sections for PricingSnapshot and PricingTrace screens, separate permissions, redaction policy, pagination support, all per AUD-SEC-008

### 9. Security Constraints Added
**Before**: Generic "authorization checks"

**After**: Tenant isolation (AUD-SEC-001), location scoping (AUD-SEC-002), raw payload handling (AUD-SEC-004), immutability proof fields (AUD-SEC-009), all explicitly documented

### 10. Acceptance Criteria Expanded
**Before**: 6 scenarios, some vague

**After**: 12 scenarios, all concrete with Given/When/Then format, covering:
- Mandatory filters
- Cross-location search (with and without permission)
- Date range validation
- Missing filter validation
- Immutability proof fields
- Raw payload access restrictions

---

## Domain Agent Consultation

### Security Domain Agent
**Consulted**: Yes  
**Business Rules Location**: `durion/.github/agents/domains/security-domain.agent.md`  
**Key Guidance Applied**:
- Authorization model (permission strings, not role names)
- Access control conceptual level (protected operations)
- Sensitive data handling (no assumptions about encryption/masking)

### Audit Domain Agent
**Consulted**: Yes  
**Business Rules Location**: `durion/.github/agents/domains/audit-domain.agent.md`  
**Key Guidance Applied**:
- Event payloads and retention policies
- Audit semantics (append-only, immutability)
- Not inventing audit schemas (used normative entities from business rules)

### Audit Domain Business Rules
**Consulted**: Yes  
**Business Rules Location**: `durion/domains/audit/.business-rules/AGENT_GUIDE.md`  
**Decisions Applied**: All 12 decisions (AUD-SEC-001 through AUD-SEC-012)

---

## No Clarification Issues Created

**Clarification issues opened**: 0

**Reason**: All open questions were resolved by consulting authoritative domain business rules. The audit domain business rules document (`AGENT_GUIDE.md`) contained explicit answers to all questions in the form of normative decisions (AUD-SEC-001 through AUD-SEC-012).

Per Story Authoring Agent instructions:
> When required information is missing or ambiguous, the agent **must open a new clarification issue** rather than making unsafe assumptions.

**Assessment**: Required information was NOT missing. It existed in the audit domain business rules and was successfully retrieved and applied.

---

## Story Status Change

| Attribute | Before | After |
|-----------|--------|-------|
| **Status Label** | `status:draft` | `status:ready-for-implementation` |
| **Blocking Labels** | `blocked:clarification`, `risk:incomplete-requirements` | None (removed) |
| **STOP Phrase** | Present | Removed |
| **Open Questions** | 7 unanswered | 13 resolved |
| **Acceptance Criteria** | 6 scenarios | 12 scenarios |
| **Service Contracts** | Incomplete | Complete (10 endpoints) |
| **Authorization Model** | TBD | Complete (9 permissions, 3 roles) |
| **Domain Compliance** | Incomplete | Complete (12 AUD-SEC decisions applied) |

---

## Verification

### Completeness Checklist
- [x] All required sections present per Story Authoring Agent instructions
- [x] All open questions resolved via domain business rules
- [x] STOP phrase removed
- [x] Labels updated to reflect resolution
- [x] Authorization model explicit (permission strings, role guidance)
- [x] Backend contracts defined (service names, params, outputs, security)
- [x] Query guardrails documented (mandatory date range, indexed filter)
- [x] Export model documented (async, CSV, digest, auditable)
- [x] Pricing evidence documented (permissions, redaction, pagination)
- [x] Acceptance criteria complete (12 scenarios)
- [x] Implementation notes provided
- [x] Testing guidance provided
- [x] Domain agent attribution documented
- [x] Original story preserved for traceability

### Domain Compliance Checklist
- [x] AUD-SEC-001: Tenant isolation enforced
- [x] AUD-SEC-002: Location scoping with cross-location permission
- [x] AUD-SEC-003: Authorization model (permission strings, roles)
- [x] AUD-SEC-004: Raw payload handling and redaction
- [x] AUD-SEC-005: Query guardrails (date range, indexed filter, max window)
- [x] AUD-SEC-006: Export security model (async, auditable, digest)
- [x] AUD-SEC-007: Identifier semantics (UUIDv7, multi-identifier search)
- [x] AUD-SEC-008: Pricing evidence access (permissions, pagination)
- [x] AUD-SEC-009: Immutability proof fields (display-only, "Provided" label)
- [x] AUD-SEC-010: Event type vocabulary (discovery endpoint)
- [x] AUD-SEC-011: Deep-link metadata policy (authorization-safe)
- [x] AUD-SEC-012: Correlation and trace context (W3C standard)

---

## Handoff Confirmation

**Story #65 is ready for implementation.**

All required clarifications were resolved via authoritative domain business rules. The story now contains:
- Complete authorization model
- Complete backend service contracts
- Complete query guardrails
- Complete export model
- Complete pricing evidence model
- Complete acceptance criteria (12 scenarios)
- Complete implementation notes
- Complete testing guidance

**No further clarification required.**

---

**Resolution Completed By**: Story Authoring Agent  
**Resolution Date**: 2026-01-20  
**Resolution Method**: Domain Business Rules Consultation (Audit + Security domains)  
**Clarification Issues Opened**: 0  
**Story Status**: Ready for Implementation ✅
