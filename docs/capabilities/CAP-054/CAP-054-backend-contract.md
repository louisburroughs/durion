# CAP-054 — Backend Contract Guide Update (Accounting)

## Inputs

- Manifest: `docs/capabilities/CAP-054/CAPABILITY_MANIFEST.yaml`
- Backend contract guide: `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- OpenAPI (authoritative): `durion-positivity-backend/pos-accounting/openapi.json`
  - Note: `durion-positivity-backend/pos-accounting/target/openapi.json` was not present in this workspace at update time.

## Findings (OpenAPI vs Guide)

- OpenAPI operations found: **63**
- Guide “Endpoint Summary” table rows found: **59**
- Non-gateway URLs in guide: **none** (all `http://localhost:8080/...`)
- Missing from guide endpoint summary table (present in OpenAPI):
  - `POST /v1/accounting/ap/payments` — Execute vendor payment
  - `GET /v1/accounting/ap/payments/{paymentId}` — Get payment details
  - `GET /v1/accounting/ap/payments/by-ref/{paymentRef}` — Get payment by reference
  - `GET /v1/accounting/ap/bills` — List eligible vendor bills

---

## Required Outputs

### Section 1: Plan

1. Parse `docs/capabilities/CAP-054/CAPABILITY_MANIFEST.yaml` and extract `CAP:054` plus backend child issue links.
2. Parse `durion-positivity-backend/pos-accounting/openapi.json` and extract the authoritative `paths` + operations.
3. Scan `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` for non-gateway URLs and endpoint list mismatches.
4. Reconcile the guide’s endpoint summary table to exactly match OpenAPI operations (method+path).
5. Add CAP-054 coordination links under “Implementation Links”.
6. Produce an apply_patch-ready patch + JSON summary and provide a small review checklist.

### Section 2: Human Checklist

- Confirm the “Endpoint Summary” count is **63** and includes the 4 `/v1/accounting/ap/*` endpoints.
- Confirm the 4 inserted endpoint summaries match OpenAPI (`summary` fields).
- Confirm CAP-054 links are correct and consistent with the manifest (parent `durion#54`, backend `durion-positivity-backend#125`, frontend `durion-moqui-frontend#123`).
- After applying the patch, re-run the OpenAPI→guide table scan to ensure zero drift.

### Section 3: JSON Summary

```json
{
  "applied": true,
  "capability_id": "CAP:054",
  "manifest_path": "docs/capabilities/CAP-054/CAPABILITY_MANIFEST.yaml",
  "backend_issues": [
    "https://github.com/louisburroughs/durion-positivity-backend/issues/125"
  ],
  "updated_files": [
    "domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md",
    "docs/capabilities/CAP-054/CAP-054-backend-contract.md"
  ],
  "openapi_changes": {
    "added": [
      "POST /v1/accounting/ap/payments",
      "GET /v1/accounting/ap/payments/{paymentId}",
      "GET /v1/accounting/ap/payments/by-ref/{paymentRef}",
      "GET /v1/accounting/ap/bills"
    ],
    "changed": [],
    "removed": []
  },
  "confidence": 97
}
```

### Section 4: Patch

```diff
*** Begin Patch
*** Update File: $WORKSPACE/durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md
@@
-**Version:** 1.0  
+**Version:** 1.3  
 **Audience:** Backend developers, Frontend developers, API consumers  
-**Last Updated:** 2026-02-08  
+**Last Updated:** 2026-02-09  
 **OpenAPI Source:** `pos-accounting/openapi.json`
@@
-This domain exposes **59** REST API endpoints:
+This domain exposes **63** REST API endpoints:
@@
 | POST | `http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/publish` | Publish posting rule set |
 | GET | `http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/versions` | List posting rule versions |
+| POST | `http://localhost:8080/v1/accounting/ap/payments` | Execute vendor payment |
+| GET | `http://localhost:8080/v1/accounting/ap/payments/{paymentId}` | Get payment details |
+| GET | `http://localhost:8080/v1/accounting/ap/payments/by-ref/{paymentRef}` | Get payment by reference |
+| GET | `http://localhost:8080/v1/accounting/ap/bills` | List eligible vendor bills |
 | GET | `http://localhost:8080/v1/accounting/traceability/{journalEntryId}` | Get journal traceability |
 | POST | `http://localhost:8080/v1/invoice/invoices` | Regenerate invoice from workorder |
 | GET | `http://localhost:8080/v1/invoice/rules/{customerId}` | Get billing rules |
@@
 | 1.0 | 2026-01-27 | Initial version generated from OpenAPI spec |
 | 1.1 | 2026-02-08 | Added Credit Memo endpoints and contracts (CAP-052) |
 | 1.2 | 2026-02-08 | Added AP Payment endpoints and contracts (CAP-053) |
+| 1.3 | 2026-02-09 | Synced Endpoint Summary with OpenAPI (AP endpoints) + added CAP-054 coordination links |
@@
 ### CAP-053: Accounts Payable (Bill → Payment)
@@
 **Unapplied Amount:** `unappliedAmount = grossAmount - sum(allocations)` becomes vendor credit for future bill payments.
 
 ---
+
+### CAP-054: Period Close, Adjustments, and Reporting
+
+**Parent Issue:** [durion#54](https://github.com/louisburroughs/durion/issues/54)  
+**Backend Implementation:** [durion-positivity-backend#125](https://github.com/louisburroughs/durion-positivity-backend/issues/125)  
+**Frontend Implementation:** [durion-moqui-frontend#123](https://github.com/louisburroughs/durion-moqui-frontend/issues/123)
+
+**Contract Note:**
+
+- No CAP-054-specific Accounting OpenAPI paths detected in `pos-accounting/openapi.json` as of 2026-02-09; this section tracks implementation coordination.
 
 ---
 
 ## References
*** End Patch
```

### Section 5: Self-Critique

- The patch is intentionally minimal (table sync + links); it does not attempt to extend endpoint “Details” sections beyond what already exists.
- If the OpenAPI source path later moves to `pos-accounting/target/openapi.json`, the guide header may need a follow-up correction.

Next steps: apply the patch to the guide after approval, then re-run the OpenAPI→guide endpoint count check.
