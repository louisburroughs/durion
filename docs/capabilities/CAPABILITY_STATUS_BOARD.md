---
title: "Capability Status Board — Durion Positivity Frontend"
updated_utc: "2026-03-28T11:00:00Z"
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
| ✅ DONE | 19 capabilities across 6 domains |
| 🔄 IN PROGRESS | 8 (CAP-136/137/138/139/140/141/142/249 — Wave F, PR #8) |
| 🟢 READY | 2 (CAP-118, CAP-094) |
| 🟡 NORMALIZE | 0 |
| 🔴 BLOCKED | 0 |
| ⬜ BACKLOG | 0 frontend capabilities with no frontend stories |

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
| CAP-053 | AP vendor payment | `accounting` | #6 | 192 | Wireframe finalized (`frontend-story-ap-execute-payment-and-post-to-gl-192.wf.md`) |
| CAP-054 | Operational cost display (cross-domain) | `workexec` | #6 | 123 | No API calls; cross-domain read-only |
| CAP-055 | Failed / quarantined event routing | `accounting` | #6 | 186 | Retry + reprocessing wired |
| CAP-089 | Party management (CRM) | `crm` | #1 | 173–176 | 8 operation_ids; 4 wireframes |
| CAP-090 | Contact management | `crm` | #1 | 170–172 + | 4/4 operation_ids; 4 wireframes |
| CAP-091 | Vehicle management | `crm` | #2 | 165–169 | 7 operation_ids; 5 wireframes |
| CAP-092 | Customer preferences / billing rules | `crm` | #2 (partial) | 162–164 | 3/3 operation_ids; 3 wireframes |
| CAP-275 | Auth session wiring + JWT assertion admin | `security` / `auth` | #7 | 280 | validateSessionOnResume; logoutWithRedirect; interceptor 401 redirect |
| CAP-253 | Security RBAC admin UI | `security` | #7 | 66 | Roles, permissions, audit log pages; SecurityService (7 ops) |

---

## In-Progress / Next Execution Ready

### � IN PROGRESS — Wave F (CAP-136/137/138/139/140/141/142/249)

**Branch**: `cap/shopmgmt-location-wave-f` | **PR**: #8 | **Tests**: 488/488

| CAP | Name | Domain | Stories | Commits |
| --- | --- | --- | --- | --- |
| CAP-136 | Location & Bay Management | `location` | #140–142 | `09e91b1`, `69d1c2b` |
| CAP-137 | Appointment Scheduling | `shopmgmt` | #137–139 | `fb8b28c` |
| CAP-138 | Appointment Dispatch & Conflict | `shopmgmt` | #133–136 | `09e91b1`, `69d1c2b` |
| CAP-139 | Time Sessions (Mechanic) | `shopmgmt` / `people` | #130–132 | `09e91b1` |
| CAP-140 | Time Export & Operational Context | `shopmgmt` / `people` | #122, 127–129 | `09e91b1` |
| CAP-141 | Security Audit List (Shopmgmt) | `security` | #125–126 | `09e91b1`, `69d1c2b` |
| CAP-142 | Appointment Reschedule | `shopmgmt` | #124 | `3e7a6f8` |
| CAP-249 | Mechanic Roster & Availability | `shopmgmt` / `people` | #74–76 | `84e0272` |

---

### �🟢 READY — CAP-118: Identity Orchestration (People domain)

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

### 🟢 READY — CAP-094: CRM & Workorder Integration

| Field | Value |
| --- | --- |
| **Domain** | `crm` (with `workexec` integration surfaces) |
| **Angular feature** | `src/app/features/crm/` + `src/app/features/workexec/` |
| **Frontend stories** | #156 and #157 in `durion-moqui-frontend` |
| **Story MD** | `docs/capabilities/CAP-094/stories/frontend/CAP_094.156.frontend.md` and `docs/capabilities/CAP-094/stories/frontend/CAP_094.157.frontend.md` |
| **Wireframes** | `domains/crm/.ui/frontend-story-integration-inbound-event-handler-f-156.wf.md`; `domains/workexec/.ui/frontend-story-integration-emit-crm-reference-ids-157.wf.md` |
| **operation_ids** | Story #157: `createEstimate`, `getEstimateById`, `promoteEstimateToWorkorder`, `createWorkorder`, `getWorkorderById`; Story #156: `listEvents`, `getEvent`, `getEventProcessingLog`, `getReprocessingHistory` |
| **Action required** | Execution-ready |

---

## Blocked

No capabilities are currently blocked.

---

## Backlog — No Frontend Stories Authored

These frontend-scoped capabilities have empty `AGENT_WORKSET.yaml` story lists. Frontend work **cannot begin** until stories are elaborated and worksets populated.

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
| CAP-278 | Accounting — Posting Rule Engine | Backend-only capability; excluded from frontend backlog |

### `crm` Deferred

No capabilities in this section currently have empty `AGENT_WORKSET.yaml` story lists.

### `security` (aside from CAP-275)

No capabilities in this section currently have empty `AGENT_WORKSET.yaml` story lists.

---

## Implemented Angular Domains — Current State

| Domain | Feature Dir | Status | Capabilities Delivered |
| --- | --- | --- | --- |
| `workexec` | `src/app/features/workexec/` | ✅ Full | CAP-002–007 |
| `crm` | `src/app/features/crm/` | ✅ Full (partial on CAP-092) | CAP-089–092 |
| `billing` | `src/app/features/billing/` | ✅ Full | CAP-007 |
| `accounting` | `src/app/features/accounting/` | ✅ Full | CAP-049–055 |
| `auth` | `src/app/features/auth/` | ✅ Full | CAP-275: session wiring, interceptor, session-expired banner |
| `security` | `src/app/features/security/` | ✅ Full | CAP-275 + CAP-253: roles, permissions, audit log RBAC admin |
| `inventory` | `src/app/features/inventory/` | ⬜ Stub | Routes file only |
| `people` | `src/app/features/people/` | 🔄 IN PROGRESS | CAP-249/139/140: PeopleService, time-approval, time-export, work-session, travel-time pages (Wave F PR #8) |
| `product` | `src/app/features/product/` | ⬜ Stub | Routes file only |
| `order` | `src/app/features/order/` | ⬜ Stub | Routes file only |
| `location` | `src/app/features/location/` | 🔄 IN PROGRESS | CAP-136: LocationService, locations/bays/mobile-units pages (Wave F PR #8) |
| `shopmgmt` | `src/app/features/shopmgmt/` | 🔄 IN PROGRESS | CAP-137/138/139/140/141/142/249: AppointmentService, full scheduling/dispatch/roster pages (Wave F PR #8) |
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
| Wave E (Security) | `cap/security-wave-e` | #7 | 2 | ~4 | 279 |
| Wave F (Shopmgmt+Location+People) | `cap/shopmgmt-location-wave-f` | #8 | 8 | 21 stories | 488 |

---

## Recommended Execution Sequence — Next Waves

### ✅ Wave E — Security Foundation (CAP-275 + CAP-253) — MERGED PR #7

**Merged:** 2026-03-27 | Branch: `cap/security-wave-e` | PR: #7 | Tests: 279/279

| CAP | Status | Scope |
| --- | --- | --- |
| CAP-275 | ✅ DONE | Auth session wiring, interceptor 401 redirect, validateSessionOnResume (mockAuth-safe) |
| CAP-253 | ✅ DONE | Roles list/detail, permissions registry, audit log placeholder, SecurityService (7 ops) |

### � Wave F — Shopmgmt + Location Execution (PR #8 OPEN)

**Branch:** `cap/shopmgmt-location-wave-f` | **PR:** #8 | **Tests:** 488/488 | **Status:** Remediation complete, awaiting merge

**Delivery triage:**

| CAP | Domain | Stories | operation_ids | Status |
| --- | --- | --- | --- | --- |
| CAP-136 | `location` | 140–142 | All stories mapped (12 ops total) | 🔄 IN PROGRESS |
| CAP-137 | `shopmgmt` | 137–139 | All stories mapped (9 ops total) | 🔄 IN PROGRESS |
| CAP-138 | `shopmgmt` | 133–136 | All stories mapped (12 ops total) | 🔄 IN PROGRESS |
| CAP-139 | `shopmgmt` / `people` | 130–132 | All stories mapped (10 ops total) | 🔄 IN PROGRESS |
| CAP-140 | `shopmgmt` / `people` | 122, 127–129 | All stories mapped (12 ops total) | 🔄 IN PROGRESS |
| CAP-141 | `shopmgmt` / `security` | 125–126 | All stories mapped (7 ops total) | 🔄 IN PROGRESS |
| CAP-142 | `shopmgmt` | 124 | All stories mapped (3 ops total) | 🔄 IN PROGRESS |
| CAP-249 | `shopmgmt` | 74–76 | All stories mapped (9 ops total) | 🔄 IN PROGRESS |

**Review outcome:** PASS — 10 findings (F1–F10) resolved in commit `69d1c2b`. F9 (LOW: "work order" naming) deferred to cleanup PR.

### Wave G — People / HR

**What:** Execute People capabilities in sequence; story elaboration is already complete.

**Execution queue:** CAP-118 (READY) first, then CAP-117, CAP-119, CAP-120, and CAP-121.

---

## Story Elaboration Backlog Priority

No frontend capabilities currently require story elaboration.

Backend-only note:
CAP-278 remains intentionally backend-only for the current frontend program scope.
