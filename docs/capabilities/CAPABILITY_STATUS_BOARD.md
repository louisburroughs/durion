---
title: "Capability Status Board — Durion Positivity Frontend"
updated_utc: "2026-04-02T16:30:00Z"
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
| ✅ DONE | 54 capabilities across 11 domains (Waves A–H + Wave I-a + Wave I-b + Wave I-b Deferred + Wave I-c — all merged to `master`) |
| 🔄 IN PROGRESS | 0 |
| 🟢 READY | 0 |
| 🟡 NORMALIZE | 0 |
| 🔴 BLOCKED | 0 |
| ⬜ BACKLOG | 0 |

**Total in portfolio: 54 capabilities** — **Program complete. All capabilities merged to `master`.**

---

## In-Progress

No capabilities are currently in progress. All 54 capabilities are merged to `master`.

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
| CAP-215 | Inventory Ledger & On-hand/ATP | `inventory` | #13 | #100, #101 | Wave I-b — merged PR #13 |
| CAP-217 | Put-away & Replenishment | `inventory` | #13 | #96, #95, #94 | Wave I-b — merged PR #13 |
| CAP-216 | Receiving (PO/ASN/Direct) | `inventory` | #15 | #98, #97 | Wave I-b Deferred — merged PR #15 |
| CAP-218 | Picking, Issuing, and Workorder Fulfillment | `inventory` | #15 | #93, #92, #242, #243, #244 | Wave I-b Deferred — merged PR #15 |
| CAP-219 | Cycle Counts & Adjustments | `inventory` | #15 | #91, #90, #241 | Wave I-b Deferred — merged PR #15 |
| CAP-220 | Reservations, Allocations, and Substitutions | `inventory` | #15 | #88, #89 | Wave I-b Deferred — merged PR #15 |
| CAP-221 | Roles, Permissions, and Audit Controls (Inventory) | `security` / `inventory` | #15 | #86, #87 | Wave I-b Deferred — merged PR #15 |
| CAP-315 | Procure-to-Receive Lifecycle (PO + ASN + Accrual) | `inventory` | #15 | #572, #571 | Wave I-b Deferred — merged PR #15 |
| CAP-246 | POS Sales Order & Cart | `order` | #14 | #83, #84, #85 | Wave I-c — merged PR #14 |
| CAP-248 | Estimate, WIP, and Invoice Visibility | `billing` | #14 | billing stories | Wave I-c — merged PR #14 |
| CAP-250 | Payments (Card Acceptance via Payment Service) | `billing` | #14 | payment stories | Wave I-c — merged PR #14 |
| CAP-251 | Invoice Payment Status Sync (Accounting Coordination) | `accounting` | #14 | sync stories | Wave I-c — merged PR #14 |
| CAP-252 | Customer Context (CRM Snapshot) | `crm` | #14 | #67, #68 | Wave I-c — merged PR #14 |

---

## Blocked

No capabilities are currently blocked.

---

## Backlog

No capabilities are in the backlog. All frontend-relevant capabilities are implemented.

Backend-only exclusion:

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-278 | Accounting — Posting Rule Engine | Backend-only capability; excluded from frontend scope |

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
| `inventory` | `src/app/features/inventory/` | ✅ Full | CAP-215–221, CAP-315 (Waves I-b + I-b Deferred) |
| `people` | `src/app/features/people/` | ✅ Full | CAP-117–121, CAP-139, CAP-140, CAP-249 |
| `product` | `src/app/features/product/` | ✅ Full | CAP-165–168, CAP-170 (Wave I-a) |
| `order` | `src/app/features/order/` | ✅ Full | CAP-246 (Wave I-c) |
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
| Wave I-a (Product) | `cap/product-wave-i-a` | #12 merged | 5 | ~10 stories | — |
| Wave I-b (Inventory) | `cap/inventory-wave-i-b` | #13 merged | 2 | #100, #101, #96, #95, #94 | 218 |
| Wave I-b Deferred | `cap/inventory-wave-i-b-deferred` | #15 merged | 6 | 9 deferred stories | — |
| Wave I-c (Order+Billing+CRM) | `cap/order-billing-crm-wave-i-c` | #14 merged | 5 | order/billing/crm stories | — |

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

### ✅ Wave I — All Sub-waves Complete (MERGED)

| Sub-wave | PR | Capabilities | Date Merged |
| --- | --- | --- | --- |
| Wave I-a (Product) | #12 | CAP-165/166/167/168/170 | 2026-03/04 |
| Wave I-b (Inventory core) | #13 | CAP-215, CAP-217 | 2026-04-01 |
| Wave I-c (Order+Billing+CRM) | #14 | CAP-246/248/250/251/252 | 2026-04-02 |
| Wave I-b Deferred | #15 | CAP-216/218/219/220/221/315 | 2026-04-02 |

All Wave I capabilities are merged to `master`. **The multi-stage capability crawl program is complete.**

---

## Story Elaboration Backlog Priority

No frontend capabilities currently require story elaboration.

Backend-only note:
CAP-278 remains intentionally backend-only for the current frontend program scope.
