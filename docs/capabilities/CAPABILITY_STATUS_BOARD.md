---
title: "Capability Status Board — Durion Positivity Frontend"
updated_utc: "2026-03-27"
generated_by: "Orchestrator"
source_prd: "durion-positivity-frontend/docs/PRD-multistage-capability-frontend-build.md"
---

# Capability Status Board

This document is the canonical study reference for the Multi-Stage Capability Crawl program.
It records every `CAP-*` capability, its Angular domain target, implementation status, and known blockers.

Legend for **Status** column:

| Symbol | Meaning |
| --- | --- |
| ✅ DONE | Merged to `master`; run artifact exists |
| 🔄 IN PROGRESS | Branch open; PR created or in review |
| 🟢 READY | Stories, wireframes, and contracts sufficient for immediate execution |
| 🟡 NORMALIZE | Workset metadata incomplete; repairable before execution |
| 🔴 BLOCKED | True dependency missing; requires human decision or external prerequisite |
| ⬜ BACKLOG | Empty workset; no frontend stories authored yet |

---

## Summary Counts

| Status | Count |
| --- | --- |
| ✅ DONE | 13 capabilities across 4 domains |
| 🔄 IN PROGRESS / MERGED | 7 (Wave D — PR #6 merged) |
| 🟢 READY | 1 (CAP-118) |
| 🟡 NORMALIZE | 1 (CAP-094; operation_id gap on story #156) |
| 🔴 BLOCKED | 1 (CAP-053; no wireframe; design fallback required) |
| ⬜ BACKLOG | 6 capabilities with no frontend stories |

**Total in portfolio: 58 capabilities**

---

## Completed — Merged to `master`

| CAP | Name | Domain | PR | Stories | Notes |
| --- | --- | --- | --- | --- | --- |
| CAP-002 | Workorder estimate creation | `workexec` | #3 | 236–239 | 7 operation_ids; 4 wireframes |
| CAP-003 | Customer approval capture | `workexec` | #3 | 269–271, 233 | 2/4 operation_ids; 4 wireframes |
| CAP-004 | Estimate promotion | `workexec` | #4 | 228–231 | 1/4 operation_ids; 4 wireframes |
| CAP-005 | Workorder execution (parts & labor) | `workexec` | #4 | 222–225 + | 1/5 operation_ids; 5+ wireframes |
| CAP-006 | Workorder completion | `workexec` | #5 | 215–218 | 2/4 operation_ids; 4 wireframes |
| CAP-007 | Invoice generation | `workexec` / `billing` | #5 | 209–213 | 0 operation_ids (resolved via OpenAPI); 2/5 wireframes |
| CAP-049 | Accounting event ingestion | `accounting` | #6 | 177–185, 205–208 | 12 stories; full wireframes |
| CAP-050 | Posting rule set configuration | `accounting` | #6 | 202 | 1 story |
| CAP-051 | AR payment application | `accounting` | #6 | 178 | `applyPayment` wired |
| CAP-052 | Credit memo issuance | `accounting` | #6 | 195 | `createCreditMemo` wired |
| CAP-053 | AP vendor payment | `accounting` | #6 | 192 | Design fallback used (no wireframe) |
| CAP-054 | Operational cost display (cross-domain) | `workexec` | #6 | 123 | No API calls; cross-domain read-only |
| CAP-055 | Failed / quarantined event routing | `accounting` | #6 | 186 | Retry + reprocessing wired |
| CAP-089 | Party management (CRM) | `crm` | #1 | 173–176 | 8 operation_ids; 4 wireframes |
| CAP-090 | Contact management | `crm` | #1 | 170–172 + | 4/4 operation_ids; 4 wireframes |
| CAP-091 | Vehicle management | `crm` | #2 | 165–169 | 7 operation_ids; 5 wireframes |
| CAP-092 | Customer preferences / billing rules | `crm` | #2 (partial) | 162–164 | 3/3 operation_ids; 3 wireframes |

---

## In-Progress / Next Execution Ready

### ✅ DONE — CAP-275: Login & Token Handling (ADR-0011)

| Field | Value |
| --- | --- |
| **Domain** | `security` |
| **Angular feature** | `src/app/features/auth/` + `src/app/features/security/` |
| **Frontend story** | #280 in `durion-moqui-frontend` |
| **Story MD** | `docs/capabilities/CAP-275/stories/frontend/CAP_275.280.frontend.md` |
| **Wireframe** | `domains/security/.ui/frontend-story-security-login-token-handling-280.wf.md` ✅ created |
| **Contract guide** | `domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md` |
| **Contract status** | `draft` |
| **operation_ids** | `enable` (JWT assertion enable/disable toggle) |
| **OpenAPI** | `pos-security-service/openapi.yaml` |
| **Blockers** | Contract status is `draft`; login page already exists in `auth/` — wave scope is token validation + assertion admin UI |

**What the Angular frontend needs (Wave E scope):**

1. Wire `validateToken` on app bootstrap / session resume (currently not called — AuthService uses JWT decode only)
2. Add HTTP interceptor 401 handler that redirects to `/login?returnUrl=...`
3. Security admin page: enable/disable JWT assertion issuance via `enable` operation
4. Optional: token configuration admin form (issuer, audience, TTL display)

---

### 🟢 READY — CAP-118: Identity Orchestration (People domain)

| Field | Value |
| --- | --- |
| **Domain** | `people` |
| **Angular feature** | `src/app/features/people/` (stub routes only) |
| **Frontend story** | #153 |
| **Story MD** | Present |
| **Wireframe** | Present |
| **operation_ids** | Populated (`getRoles`, `getAssignments_1`, `createAssignment_1`, `revokeAssignment`) |
| **Action required** | Execution-ready |

---

### 🟡 NORMALIZE — CAP-094: CRM & Workorder Integration

| Field | Value |
| --- | --- |
| **Domain** | `crm` (with `workexec` integration surfaces) |
| **Angular feature** | `src/app/features/crm/` + `src/app/features/workexec/` |
| **Frontend stories** | #156 and #157 in `durion-moqui-frontend` |
| **Story MD** | `docs/capabilities/CAP-094/stories/frontend/CAP_094.156.frontend.md` and `docs/capabilities/CAP-094/stories/frontend/CAP_094.157.frontend.md` |
| **Wireframes** | `domains/crm/.ui/frontend-story-integration-inbound-event-handler-f-156.wf.md`; `domains/workexec/.ui/frontend-story-integration-emit-crm-reference-ids-157.wf.md` |
| **operation_ids** | Story #157 populated; Story #156 still empty pending endpoint confirmation |
| **Action required** | Confirm ProcessingLog/Suspense backend read operations in canonical OpenAPI/service contract, then populate story #156 `operation_ids` |

---

## Blocked

### 🔴 BLOCKED — CAP-053 Follow-up Wireframe

CAP-053 (AP vendor payment, story 192) was implemented in Wave D using a design fallback because no wireframe existed.
Implementation is complete and merged, but the wireframe artifact gap should be closed.

| Field | Value |
| --- | --- |
| **Action** | Designer to produce `domains/accounting/.ui/cap053-vendor-payment-new-192.wf.md` |
| **Impact** | Documentation gap only; functionality is shipped |

---

## Backlog — No Frontend Stories Authored

These capabilities have empty `AGENT_WORKSET.yaml` story lists. Frontend work **cannot begin** until stories are elaborated and worksets populated.

### `people` / HR Domain

No capabilities in this section currently have empty `AGENT_WORKSET.yaml` story lists.

### `inventory` Domain

No capabilities in this section currently have empty `AGENT_WORKSET.yaml` story lists.

### `product` / Pricing Domain

No capabilities in this section currently have empty `AGENT_WORKSET.yaml` story lists.

### `shopmgmt` / Scheduling Domain

No capabilities in this section currently have empty `AGENT_WORKSET.yaml` story lists.

### `order` Domain

No capabilities in this section currently have empty `AGENT_WORKSET.yaml` story lists.

### `location` Domain

No capabilities in this section currently have empty `AGENT_WORKSET.yaml` story lists.

### `billing` Additions

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-250 | Billing — TBD | Empty workset |
| CAP-251 | Billing / accounting additional | Empty workset |
| CAP-278 | Accounting — TBD | Backend-only likely; empty workset |
| CAP-248 | Billing / shop / workexec integration | Empty workset |

### `crm` Deferred

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-252 | CRM — TBD | Empty workset |

### `security` (aside from CAP-275)

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-172 | Security — TBD | Empty workset |

---

## Implemented Angular Domains — Current State

| Domain | Feature Dir | Status | Capabilities Delivered |
| --- | --- | --- | --- |
| `workexec` | `src/app/features/workexec/` | ✅ Full | CAP-002–007 |
| `crm` | `src/app/features/crm/` | ✅ Full (partial on CAP-092) | CAP-089–092 |
| `billing` | `src/app/features/billing/` | ✅ Full | CAP-007 |
| `accounting` | `src/app/features/accounting/` | ✅ Full | CAP-049–055 |
| `auth` | `src/app/features/auth/` | 🟡 Partial | Login page exists; no token refresh or validate wiring |
| `security` | `src/app/features/security/` | 🔴 Stub | Routes file only; no pages |
| `inventory` | `src/app/features/inventory/` | ⬜ Stub | Routes file only |
| `people` | `src/app/features/people/` | ⬜ Stub | Routes file only |
| `product` | `src/app/features/product/` | ⬜ Stub | Routes file only |
| `order` | `src/app/features/order/` | ⬜ Stub | Routes file only |
| `location` | `src/app/features/location/` | ⬜ Stub | Routes file only |
| `admin` | `src/app/features/admin/` | ⬜ Stub | Minimal |

---

## Program Velocity Reference

| Wave | Branch | PR | Capabilities | Stories | Tests at Close |
| --- | --- | --- | --- | --- | --- |
| Wave A (CRM) | `cap/crm-domain-wave-a` | #1, #2 | 4 | ~16 | — |
| Wave B (Workexec) | `cap/workexec-wave-b` | #3 | 2 | 8 | — |
| Wave B-cont | `cap/workexec-wave-b-cont` | #4 | 2 | ~8 | — |
| Wave C (Completion) | `cap/workexec-wave-c` | #5 | 2 | 5 | 172 |
| Wave D (Accounting) | `cap/accounting-wave-d` | #6 | 7 | 18 | 187 |

---

## Recommended Execution Sequence — Next Waves

### Wave E — Security Foundation (CAP-275 + CAP-253)

**What:** Wire the Angular auth layer properly; build the Security admin UI for roles, permissions, and token control.

| CAP | Status | Scope |
| --- | --- | --- |
| CAP-275 | ✅ DONE | Auth wire-up + JWT assertion admin toggle (`security` + `auth` domains) |
| CAP-253 | ✅ DONE | Roles, permissions, audit log admin (`security` domain) |

**Pre-condition for CAP-253:** Validate deferred story operation wiring (`CAP_253.65`) during execution and keep deferral rationale in run artifacts if unchanged.

### Wave F — Shopmgmt + Location Execution

**What:** Shopmgmt and location worksets are populated; execute those capabilities while moving story elaboration to remaining empty-workset domains.

**Targets (in priority order):**

1. Execute CAP-136, CAP-137, CAP-138, CAP-139, CAP-140, CAP-141, CAP-142, CAP-249
2. Elaborate remaining backlog capabilities: CAP-250, CAP-251, CAP-248, CAP-172, CAP-252, CAP-278

### Wave G — People / HR + Location

**What:** Execute CAP-118, then elaborate remaining `people` stories.

---

## Story Elaboration Backlog Priority

These are the highest-impact capabilities to story-elaborate next:

| Priority | CAP | Domain | Why |
| --- | --- | --- | --- |
| 1 | CAP-250 | `billing` | Billing follow-up capabilities remain unscoped in frontend |
| 2 | CAP-251 | `billing` | Additional billing/accounting flows are still unelaborated |
| 3 | CAP-248 | `billing` / `shop` / `workexec` | Cross-domain billing integration remains unelaborated |
| 4 | CAP-172 | `security` | Security backlog still has no authored frontend stories |
| 5 | CAP-252 | `crm` | CRM backlog still has no authored frontend stories |
