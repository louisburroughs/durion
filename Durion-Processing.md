# Durion Processing — Wave C: CAP-006 + CAP-007

**Status: COMPLETED**
**Branch:** `cap/workexec-wave-c`
**Base:** `master` (177ea8c)
**Target repo:** `durion-positivity-frontend`
**PR target:** PR #5

---

## Domain Ownership

| Domain | Capability | Stories | Angular Feature |
|--------|-----------|---------|-----------------|
| `workexec` | CAP-006 | 218, 217, 216, 215, 214 | `src/app/features/workexec/` |
| `billing` | CAP-007 | 213, 212, 211, 210, 209 | `src/app/features/billing/` |

## Resolved Operation IDs

| Story | Operation ID | Path |
|-------|-------------|------|
| 218 | (checklist via CR state + complete response) | local + `POST /v1/workorders/{workorderId}/complete` |
| 217 | getChangeRequestsByWorkorder | `GET /v1/workorders/{workorderId}/changeRequests` |
| 216 | (finalize endpoint TBD) | TBD from contract guide |
| 215 | completeWorkorder | `POST /v1/workorders/{workorderId}/complete` |
| 214 | reopenWorkorder | `POST /v1/workorders/{workorderId}/reopen` |
| 213 | generateInvoice | `POST /v1/workorders/{workorderId}/generate-invoice` |
| 212 | (read invoice detail) | `GET /v1/workorders/{workorderId}/invoice` |
| 211 | (read traceability) | invoice detail response |
| 210 | (invoice adjustments) | invoice detail response |
| 209 | (issue invoice) | TBD |

---

## Steps

- [x] Step 1: Read source materials (PRDs, manifests, stories, wireframes, OpenAPI)
- [x] Step 2: Designer first-pass — design brief
- [x] Step 3: Create branch `cap/workexec-wave-c`
- [x] Step 4: Implement CAP-006 — TypeScript (models, service, component logic)
- [x] Step 5: Implement CAP-006 — HTML/CSS (templates, styles)
- [x] Step 6: Implement CAP-007 — TypeScript (billing models, service, invoice pages)
- [x] Step 7: Implement CAP-007 — HTML/CSS (billing templates, styles)
- [x] Step 8: Register new routes (workexec + billing)
- [x] Step 9: Build verification (`npm run build`)
- [x] Step 10: Test verification (`npm test -- --watch=false`)
- [x] Step 11: Fix any build/test failures
- [x] Step 12: Update run artifacts (CAP-006/runs/latest.md, CAP-007/runs/latest.md)
- [x] Step 13: Commit all changes
- [x] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh`

