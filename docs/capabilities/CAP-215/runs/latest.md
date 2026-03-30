# Capability Run Artifact — CAP-215 Wave I-b

Use this run record with:

- Manifest: docs/capabilities/CAP-215/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-215/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 1. Run Metadata

- Capability: CAP-215
- Run Timestamp (UTC): 2026-03-29T00:00:00Z
- Agent/Operator: Orchestrator (Wave I-b)
- Branch(es): `cap/inventory-wave-i-b`
- Status: partial — 2/2 stories done; capability complete for Wave I-b scope

## 2. Inputs Used

- Manifest: docs/capabilities/CAP-215/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-215/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| Inventory Availability Lookup | #100 | #100 | done | On-hand/ATP dashboard, SKU search, location breakdown; availability page component |
| View Inventory Ledger / Record Stock Movements | #101 | #101 | done | Ledger list page + ledger detail page; stock movement log table |

## 4. Implementation Changes

### Frontend Files Changed

- `src/app/features/inventory/services/inventory.service.ts`
- `src/app/features/inventory/pages/availability/availability.component.ts`
- `src/app/features/inventory/pages/availability/availability.component.html`
- `src/app/features/inventory/pages/availability/availability.component.css`
- `src/app/features/inventory/pages/availability/availability.component.spec.ts`
- `src/app/features/inventory/pages/ledger-list/ledger-list.component.ts`
- `src/app/features/inventory/pages/ledger-list/ledger-list.component.html`
- `src/app/features/inventory/pages/ledger-list/ledger-list.component.css`
- `src/app/features/inventory/pages/ledger-list/ledger-list.component.spec.ts`
- `src/app/features/inventory/pages/ledger-detail/ledger-detail.component.ts`
- `src/app/features/inventory/pages/ledger-detail/ledger-detail.component.html`
- `src/app/features/inventory/pages/ledger-detail/ledger-detail.component.css`
- `src/app/features/inventory/pages/ledger-detail/ledger-detail.component.spec.ts`

### Behavior Implemented

- SKU/location availability query with ATP display (`queryAvailabilityBySku`, `queryInventoryAvailability`)
- Location-level on-hand inventory table with filter/pagination
- Inventory ledger list with date-range and location filters
- Ledger entry detail view with stock movement log (`getLocationInventory`)
- Empty/loading/error states on all pages per ADR-0031

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| #100 Availability Lookup | `queryAvailabilityBySku` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #100 Availability Lookup | `queryInventoryAvailability` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #101 Ledger / Stock Movements | `getLocationInventory` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |

## 6. Validation

### Commands Run

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend
npm run build
npx ng test --no-watch
```

### Results

- Build: pass
- Tests: pass (218/218 across 24 spec files)
- Lint: pass
- Typecheck: pass

## 7. Blockers and Decisions

No blockers for CAP-215. Both stories #100 and #101 implemented and passing.

## 8. Follow-Up Actions

- [ ] Merge `cap/inventory-wave-i-b` to `master` via PR

## 9. Completion Gate

- [x] All workset stories processed.
- [x] All required operations wired or explicitly blocked with reason.
- [x] Acceptance criteria verified against story markdown and wireframe.
- [ ] Validation commands executed and results recorded.
- [ ] runs/latest.md reflects final state.
