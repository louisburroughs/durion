---
title: "Capability Status Board — Durion Positivity Frontend"
updated_utc: "2026-04-01T00:00:00Z"
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
| ✅ DONE | 41 capabilities across 9 domains (Waves A–H merged + Wave I-a) |
| 🔄 IN PROGRESS | 8 (Wave I-b — `cap/inventory-wave-i-b`; 12/21 stories done, 9 deferred on branch — **all unblocked** as of 2026-04-01; backend complete, `openapi.yaml` and SDK up to date) |
| 🟢 READY | 5 capabilities (Wave I-c queue) |
| 🟡 NORMALIZE | 0 |
| 🔴 BLOCKED | 0 |
| ⬜ BACKLOG | 0 frontend capabilities with no frontend stories |

**Total in portfolio: 54 capabilities**

---

## In-Progress — Wave I-b Inventory Domain

### 🔄 IN PROGRESS — Wave I-b (CAP-215/216/217/218/219/220/221/315)

**Branch**: `cap/inventory-wave-i-b` | **PR**: pending | **Tests**: 218/218 | **Status**: READY FOR PR

| CAP | Name | Domain | Stories Done | Stories Deferred |
| --- | --- | --- | --- | --- |
| CAP-215 | Inventory Ledger & On-hand/ATP | `inventory` | #100, #101 | — |
| CAP-216 | Receiving (PO/ASN/Direct) | `inventory` | #98 | #97 — **UNBLOCKED** (backend complete; `openapi.yaml` and SDK up to date) |
| CAP-217 | Put-away & Replenishment | `inventory` | #96, #95, #94 | — |
| CAP-218 | Picking, Issuing, and Workorder Fulfillment | `inventory` | #93 | #92, #242, #243, #244 — **UNBLOCKED** (backend complete; WorkExec pick facade implemented; `openapi.yaml` and SDK up to date) |
| CAP-219 | Cycle Counts & Adjustments | `inventory` | #91, #90 | #241 — **UNBLOCKED** (backend complete; `openapi.yaml` and SDK up to date) |
| CAP-220 | Reservations, Allocations, and Substitutions | `inventory` | #88 | #89 — **UNBLOCKED** (backend complete; `resolveShortage` and `queryLeadTime` contracts in `openapi.yaml` and SDK) |
| CAP-221 | Roles, Permissions, and Audit Controls (Inventory) | `security` / `inventory` | #86 | #87 — **UNBLOCKED** (backend complete; permission catalog and gating matrix implemented; JWT `authorities` claim source confirmed) |
| CAP-315 | Procure-to-Receive Lifecycle (PO + ASN + Accrual) | `inventory` | #572 | #571 — **UNBLOCKED** (backend complete; `openapi.yaml` and SDK up to date) |

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
| CAP-136 | Location & Bay Management | `location` | #8 | #140–142 | Wave F — merged to master |
| CAP-137 | Appointment Scheduling | `shopmgmt` | #8 | #137–139 | Wave F — merged to master |
| CAP-138 | Appointment Dispatch & Conflict | `shopmgmt` | #8 | #133–136 | Wave F — merged to master |
| CAP-139 | Time Sessions (Mechanic) | `shopmgmt` / `people` | #8 | #130–132 | Wave F — merged to master |
| CAP-140 | Time Export & Operational Context | `shopmgmt` / `people` | #8 | #122, 127–129 | Wave F — merged to master |
| CAP-141 | Security Audit List (Shopmgmt) | `security` | #8 | #125–126 | Wave F — merged to master |
| CAP-142 | Appointment Reschedule | `shopmgmt` | #8 | #124 | Wave F — merged to master |
| CAP-249 | Mechanic Roster & Availability | `shopmgmt` / `people` | #8 | #74–76 | Wave F — merged to master |
| CAP-118 | Identity Orchestration (People RBAC) | `people` | — | #153 | Wave G — merged to master |
| CAP-094 | CRM & Workorder Integration | `crm` / `workexec` | — | #156, #157 | Wave G — merged to master |
| CAP-117 | People Profile Management | `people` | — | #152, #154, #155 | Wave H — merged to master |
| CAP-119 | Location Assignment (People) | `people` | — | #150, #151 | Wave H — merged to master |
| CAP-120 | Timekeeping | `people` | — | #143, #147, #148, #149 | Wave H — merged to master |
| CAP-121 | Job Time Integration | `people` | — | #144, #145, #146 | Wave H — merged to master |
| CAP-214 | Location Topology | `location` | — | #102, #103, #104 | Wave H — merged to master |
| CAP-165 | Product Master Data (Parts & Tires) | `product` | cap/product-wave-i-a | 119, 120, 121 | Wave I-a — merged; build PASS; run artifact added |
| CAP-166 | Cost Management (Acquisition & Cost Models) | `product` | cap/product-wave-i-a | 260, 261 | Wave I-a — merged; build PASS; run artifact added |
| CAP-167 | MSRP & Base Pricing Policies | `product` | cap/product-wave-i-a | 118, 259 | Wave I-a — merged; build PASS; run artifact added |
| CAP-168 | Location Store Pricing (Overrides by Location) | `product` | cap/product-wave-i-a | 116, 117 | Wave I-a — merged; build PASS; run artifact added |
| CAP-170 | Availability & Inventory Visibility (Internal + External) | `product` | cap/product-wave-i-a | 110, 111, 112 | Wave I-a — merged; build PASS; run artifact added |

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
| `inventory` | `src/app/features/inventory/` | � IN PROGRESS | Wave I-b partial (12/21 stories on `cap/inventory-wave-i-b`) |
| `people` | `src/app/features/people/` | ✅ Full | CAP-117–121, CAP-139, CAP-140, CAP-249 |
| `product` | `src/app/features/product/` | 🟢 READY | Wave I-a queued |
| `order` | `src/app/features/order/` | 🟢 READY | Wave I-c queued |
| `location` | `src/app/features/location/` | ✅ Full | CAP-136, CAP-214 |
| `shopmgmt` | `src/app/features/shopmgmt/` | ✅ Full | CAP-137, CAP-138, CAP-139, CAP-140, CAP-141, CAP-142, CAP-249 |
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
| Wave G (People RBAC + CRM-Workexec) | `cap/people-rbac-wave-g` | — | 2 | 3 stories | — |
| Wave H (People Profile + Location Topology) | `cap/people-location-wave-h` | — | 5 | 16 stories | 699 |
| Wave I-a (Product) | `cap/product-wave-i-a` | merged | 5 | ~10 stories | — |
| Wave I-b (Inventory) | `cap/inventory-wave-i-b` | pending PR | 8 | 12 done / 9 deferred (all unblocked 2026-04-01) | 218 |

---

## Recommended Execution Sequence — Next Waves

### ✅ Wave E — Security Foundation (CAP-275 + CAP-253) — MERGED PR #7

**Merged:** 2026-03-27 | Branch: `cap/security-wave-e` | PR: #7 | Tests: 279/279

| CAP | Status | Scope |
| --- | --- | --- |
| CAP-275 | ✅ DONE | Auth session wiring, interceptor 401 redirect, validateSessionOnResume (mockAuth-safe) |
| CAP-253 | ✅ DONE | Roles list/detail, permissions registry, audit log placeholder, SecurityService (7 ops) |

### ✅ Wave F — Shopmgmt + Location Execution (PR #8 MERGED)

**Branch:** `cap/shopmgmt-location-wave-f` | **PR:** #8 | **Tests:** 488/488 | **Status:** MERGED to master

All 8 capabilities (CAP-136/137/138/139/140/141/142/249) delivered and merged.

### ✅ Wave G/H — People RBAC + Profile + Location Topology (MERGED)

CAP-118, CAP-094 (Wave G) and CAP-117, CAP-119, CAP-120, CAP-121, CAP-214 (Wave H) all merged to master. Final test count at Wave H close: 699/699.

### 🟢 Wave I — Final Capabilities (Next Up)

Execution sequence (see Wave I section above for full capability breakdown):

1. **Wave I-a** — `product` domain: branch `cap/product-wave-i-a`
2. **Wave I-b** — `inventory` domain: branch `cap/inventory-wave-i-b`
3. **Wave I-c** — `order`/`billing`/`crm` additions: branch `cap/order-billing-crm-wave-i-c`

---

## Story Elaboration Backlog Priority

No frontend capabilities currently require story elaboration.

Backend-only note:
CAP-278 remains intentionally backend-only for the current frontend program scope.
