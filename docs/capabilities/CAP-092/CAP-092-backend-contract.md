# CAP:092 — Backend Contract Update

**Capability:** CAP:092 — Preferences & Billing Rules
**Date:** 2026-02-05

**Local commit:** 2849f82

This document records the backend contract documentation changes made for CAP:092.

---

## Scope

- Add a Billing domain backend contract guide (pre-OpenAPI) based on CAP:092 story endpoint definitions.
- Update CRM and WorkExec backend contract guides with CAP:092 addenda.
- Normalize endpoint headings/examples to API Gateway format: `http://localhost:8080/...`.

---

## Key Contracts Added (Draft)

### Billing rules (Billing domain)

- `GET http://localhost:8080/v1/billing/rules/{partyId}`
- `PUT http://localhost:8080/v1/billing/rules/{partyId}` (idempotent upsert)

### CRM snapshot (CRM domain)

- `GET http://localhost:8080/v1/crm/snapshot`

Story-defined internal path: `GET /v1/crm-snapshot`.

### CRM billing rules facade (CRM domain)

- `GET http://localhost:8080/v1/crm/accounts/parties/{partyId}/billingRules`

---

## Files Changed

- Added: `domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- Updated: `domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- Updated: `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
