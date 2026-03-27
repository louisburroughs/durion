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
| 🟢 READY | 1 (CAP-275) |
| 🟡 NORMALIZE | 1 (CAP-118; has single story but empty operation_ids) |
| 🔴 BLOCKED | 1 (CAP-053; no wireframe; design fallback required) |
| ⬜ BACKLOG | 35 capabilities with no frontend stories |

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

### 🟢 READY — CAP-275: Login & Token Handling (ADR-0011)

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

### 🟡 NORMALIZE — CAP-118: Identity Orchestration (People domain)

| Field | Value |
| --- | --- |
| **Domain** | `people` |
| **Angular feature** | `src/app/features/people/` (stub routes only) |
| **Frontend story** | #153 |
| **Story MD** | Present |
| **Wireframe** | Present |
| **operation_ids** | Empty — needs OpenAPI inspection |
| **Action required** | Inspect `pos-people-service/openapi.yaml`; populate operation_ids; then READY |

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

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-117 | People — TBD | Empty workset |
| CAP-119 | People — TBD | Empty workset |
| CAP-120 | People — TBD | Empty workset |
| CAP-121 | People — TBD | Empty workset |
| CAP-214 | Location management (people-linked) | Empty workset |
| CAP-253 | Roles, Permissions, and Audit Controls | `contract_status: stable-for-ui`; empty workset — **high-priority to elaborate** |

### `inventory` Domain (8 capabilities — none started)

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-215 | Inventory receiving | Empty workset |
| CAP-216 | Put-away | Empty workset |
| CAP-217 | Picking | Empty workset |
| CAP-218 | Cycle counts | Empty workset |
| CAP-219 | Inventory reservations | Empty workset |
| CAP-220 | Inventory adjustments | Empty workset |
| CAP-221 | Inventory transfers | Empty workset |
| CAP-315 | TBD | Empty workset |

### `product` / Pricing Domain (9 capabilities — none started)

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-165 | Product master data | Empty workset |
| CAP-166 | Product cost management | Empty workset |
| CAP-167 | MSRP & list price | Empty workset |
| CAP-168 | Location-based pricing | Empty workset |
| CAP-169 | Labor pricing / workexec pricing | Empty workset |
| CAP-170 | Product / inventory linkage | Empty workset |
| CAP-171 | Pricing substitution rules | Empty workset |
| CAP-247 | Product catalog search | Empty workset |
| CAP-093 | CRM / pricing integration | Empty workset |

### `shopmgmt` / Scheduling Domain (6 capabilities)

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-137 | Shop scheduling | Empty workset |
| CAP-138 | Dispatch management | Empty workset |
| CAP-139 | Shop capacity planning | Empty workset |
| CAP-140 | Timekeeping integration | Empty workset |
| CAP-141 | Security / shop access control | Empty workset |
| CAP-142 | Technician assignment | Empty workset |
| CAP-249 | Shop manager dashboard | Empty workset |

### `order` Domain

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-246 | POS sales order / cart | Empty workset — Angular domain stub only |

### `location` Domain

| CAP | Name | Notes |
| --- | --- | --- |
| CAP-136 | Shop / bay management | Empty workset |

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
| CAP-094 | CRM — TBD | Empty workset |
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
| CAP-275 | 🟢 READY | Auth wire-up + JWT assertion admin toggle (`security` + `auth` domains) |
| CAP-253 | 🟡 NORMALIZE (stories in `.ui/` wireframes exist) | Roles, permissions, audit log admin (`security` domain) |

**Pre-condition for CAP-253:** Elaborate `AGENT_WORKSET.yaml` — wireframes in `.ui/` exist already; operation_ids can be resolved from `BACKEND_CONTRACT_GUIDE.md`.

### Wave F — Inventory & Product Core (story elaboration first)

**What:** Author frontend stories for the 8 inventory and 9 product capabilities via Story Authoring Agent, then execute implementation wave.

**Story elaboration targets (in priority order):**

1. CAP-165, CAP-166, CAP-167 — Product master data fundamentals
2. CAP-215, CAP-216, CAP-217 — Inventory receive / put-away / pick
3. CAP-246 — Sales order / cart (order domain)
4. CAP-253 — Security roles/permissions (if not done in Wave E)

### Wave G — People / HR + Location

**What:** CAP-118 (normalize first), then elaborate remaining `people` stories.

---

## Story Elaboration Backlog Priority

These are the highest-impact capabilities to story-elaborate before Wave F can begin:

| Priority | CAP | Domain | Why |
| --- | --- | --- | --- |
| 1 | CAP-253 | `security` | Stable-for-ui contract; wireframes exist — only AGENT_WORKSET needs updating |
| 2 | CAP-165 | `product` | Foundation for all inventory/catalog work |
| 3 | CAP-215, 216 | `inventory` | Receiving and put-away are entry points for all inventory flows |
| 4 | CAP-246 | `order` | POS cart is a customer-facing entry point |
| 5 | CAP-118 | `people` | Story exists; needs operation_ids only |
| 6 | CAP-249 | `shopmgmt` | Shop manager dashboard (cross-domain view) |
