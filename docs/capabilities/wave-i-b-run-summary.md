---
wave: "I-b"
branch: "cap/inventory-wave-i-b"
date_utc: "2026-03-30T00:00:00Z"
status: "READY FOR PR"
---

# Wave I-b Execution Summary — Inventory Domain

**Date:** 2026-03-30
**Branch:** `cap/inventory-wave-i-b`
**Base:** `master` (Wave I-a `cap/product-wave-i-a` merged)
**Target repo:** `durion-positivity-frontend`
**Status:** ✅ READY FOR PR

---

## Overview

Wave I-b implemented the Inventory domain frontend across 8 capabilities, 12 of 21 planned stories.
Nine stories remain deferred on the branch; five now have documented contract follow-through and four still need contract or architecture decisions.

| Metric | Value |
| --- | --- |
| Capabilities covered | 8 |
| Stories planned | 21 |
| Stories implemented | 12 |
| Stories deferred | 9 |
| Tests passing | 218/218 |
| Spec files | 24 |
| Build | clean — no errors |
| New Angular feature dir | `src/app/features/inventory/` |

---

## Capabilities

| CAP | Name | Implemented Stories | Deferred Stories | Status |
| --- | --- | --- | --- | --- |
| CAP-215 | Inventory Ledger & On-hand/ATP | #100, #101 | — | ✅ Complete |
| CAP-216 | Receiving (PO/ASN/Direct) | #98 | #97 | 🔄 Partial |
| CAP-217 | Put-away & Replenishment | #94, #95, #96 | — | ✅ Complete |
| CAP-218 | Picking, Issuing, and Workorder Fulfillment | #93 | #92, #242, #243, #244 | 🔄 Partial |
| CAP-219 | Cycle Counts & Adjustments | #90, #91 | #241 | 🔄 Partial |
| CAP-220 | Reservations, Allocations, and Substitutions | #88 | #89 | 🔄 Partial |
| CAP-221 | Roles, Permissions, and Audit Controls (Inventory) | #86 | #87 | 🔄 Partial |
| CAP-315 | Procure-to-Receive Lifecycle (PO + ASN + Accrual) | #572 | #571 | 🔄 Partial |

---

## Implemented Stories (12)

| Story # | Capability | Description | Page Component(s) |
| --- | --- | --- | --- |
| #100 | CAP-215 | Inventory Availability Lookup | `availability` |
| #101 | CAP-215 | View Inventory Ledger / Record Stock Movements | `ledger-list`, `ledger-detail` |
| #572 | CAP-315 | Purchase Order List, Detail, Create, Edit | `po-list`, `po-detail`, `po-form` |
| #98 | CAP-216 | Receive Goods into Staging | `receive-into-staging` |
| #96 | CAP-217 | Generate Put-away Tasks from Receipts | `putaway-task-list` |
| #95 | CAP-217 | View Inventory Ledger (Execute Put-away) | `putaway-execute` |
| #94 | CAP-217 | Inventory Ledger Entry Detail (Replenishment) | `replenishment-task-list` |
| #88 | CAP-220 | Putaway Task List and Execute | `putaway-task-list`, `putaway-execute` |
| #93 | CAP-218 | Replenishment Task List (empty state) | `replenishment-task-list` |
| #91 | CAP-219 | Execute Cycle Count | `count-execute` |
| #90 | CAP-219 | Approve/Reject Inventory Adjustments | `adjustment-approvals` |
| #86 | CAP-221 | Audit Log Search and Detail | `audit-logs` (security feature) |

---

## Deferred Stories (9)

| Story # | Capability | Description | Blocker |
| --- | --- | --- | --- |
| #97 | CAP-216 | Cross-dock Receiving (Direct-to-Workorder) | Contract note documented; implementation still deferred |
| #92 | CAP-218 | Create Pick List and Pick Tasks | API contract TBD for `createPickList`/`releasePickList` |
| #242 | CAP-218 | Return Unused Items to Stock | Contract note documented; implementation still deferred |
| #243 | CAP-218 | Issue / Consume Picked Items | Backend ownership note documented; frontend should target workorder facade over inventory consumption |
| #244 | CAP-218 | Mechanic Executes Picking Workflow | WorkExec ownership is documented, but frontend still depends on the workorder-facing pick scaffold from `#92` |
| #241 | CAP-219 | Plan Cycle Counts by Location | Contract addendum documented; implementation still deferred |
| #89 | CAP-220 | Shortage Resolution / Handle Shortages with Back-order | Recommendation contracts documented; submit-decision endpoint/facade still missing |
| #87 | CAP-221 | Inventory Security Admin (Permissions) | Security permission primitives exist; inventory permission catalog is normalized, the gating matrix is documented, and current-user permissions are available from JWT claims |
| #571 | CAP-315 | Create Receiving Session via ASN + PO | Receiving-path note documented; implementation still deferred |

---

## New Files Created

### `src/app/features/inventory/`

```text
inventory.routes.ts
inventory.component.ts
models/inventory.models.ts
services/
  inventory.service.ts
  inventory-receiving.service.ts
  inventory-cycle-count.service.ts
  inventory-purchase-order.service.ts
pages/
  availability/                      (CAP-215 #100)
  ledger-list/                        (CAP-215 #101)
  ledger-detail/                      (CAP-215 #101)
  receive-into-staging/               (CAP-216 #98)
  putaway-task-list/                  (CAP-217/CAP-220 #96/#88)
  putaway-execute/                    (CAP-217/CAP-220 #95/#88)
  replenishment-task-list/            (CAP-217/CAP-218 #94/#93)
  count-execute/                      (CAP-219 #91)
  adjustment-approvals/               (CAP-219 #90)
  po-list/                            (CAP-315 #572)
  po-detail/                          (CAP-315 #572)
  po-form/                            (CAP-315 #572)
```

### `src/app/features/security/` (cross-domain — CAP-221 #86)

```text
services/security-audit.service.ts
pages/audit-logs/
  audit-logs.component.ts
  audit-logs.component.html
  audit-logs.component.css
  audit-logs.component.spec.ts
```

---

## Validation

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend
npm run build   # → Application bundle generation complete. No errors.
npx ng test --no-watch  # → 218/218 tests passing | 24 spec files
```

---

## Run Artifacts

Per-capability run artifacts updated 2026-03-29:

- [docs/capabilities/CAP-215/runs/latest.md](CAP-215/runs/latest.md)
- [docs/capabilities/CAP-216/runs/latest.md](CAP-216/runs/latest.md)
- [docs/capabilities/CAP-217/runs/latest.md](CAP-217/runs/latest.md)
- [docs/capabilities/CAP-218/runs/latest.md](CAP-218/runs/latest.md)
- [docs/capabilities/CAP-219/runs/latest.md](CAP-219/runs/latest.md)
- [docs/capabilities/CAP-220/runs/latest.md](CAP-220/runs/latest.md)
- [docs/capabilities/CAP-221/runs/latest.md](CAP-221/runs/latest.md)
- [docs/capabilities/CAP-315/runs/latest.md](CAP-315/runs/latest.md)

---

## Next Step

Create PR via `durion/.github/hooks/pull-request-hook.sh`:

```bash
--repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend \
--story inventory-wave-i-b \
--base master \
--head cap/inventory-wave-i-b \
--title "feat(inventory): Wave I-b — Inventory Ledger, Receiving, Putaway, Picking, Cycle Counts & Audit (CAP-215, CAP-216, CAP-217, CAP-218, CAP-219, CAP-220, CAP-221, CAP-315)"
```
