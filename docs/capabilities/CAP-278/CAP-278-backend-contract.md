# CAP-278 Backend Contract Guide Update

This document proposes an OpenAPI-aligned update to the Accounting backend contract guide for CAP-278 coordination.

## Section 1: Plan
1. Parse CAP-278 capability manifest and extract backend child issues.
2. Parse authoritative OpenAPI spec and collect valid `paths`.
3. Scan the existing contract guide for non-gateway URLs and endpoint paths not in OpenAPI.
4. Propose minimal edits: (a) normalize endpoint URLs to gateway format, (b) add CAP-278 implementation links.
5. Validate that all updated endpoints exist in OpenAPI and that no non-gateway URLs remain.
6. Provide an apply_patch-ready patch for human review.

## Section 2: Human Checklist
- Confirm the guide contains no URLs outside `http://localhost:8080/v1/accounting/*`.
- Confirm the six corrected endpoints match OpenAPI `paths` exactly.
- Confirm CAP-278 backend child issue links resolve and match the manifest.
- After merge, run relevant provider contract tests (e.g., `*ContractBehaviorIT`) that cover reporting + invoice routes.

## Section 3: JSON Summary
```json
{
  "capability_id": "CAP:278",
  "applied": true,
  "manifest_path": "durion/docs/capabilities/CAP-278/CAPABILITY_MANIFEST.yaml",
  "backend_issues": [
    "https://github.com/louisburroughs/durion-positivity-backend/issues/472",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/473",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/474",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/475",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/476",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/477",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/478"
  ],
  "updated_files": [
    "durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md"
  ],
  "openapi_changes": {
    "added": [],
    "changed": [
      "/v1/accounting/reports/financial/income-statement",
      "/v1/accounting/reports/financial/drilldown/journal-lines/{accountId}",
      "/v1/accounting/reports/financial/drilldown/accounts/{statementLineCode}",
      "/v1/accounting/reports/financial/balance-sheet",
      "/v1/accounting/invoice/invoices",
      "/v1/accounting/invoice/rules/{customerId}"
    ],
    "removed": []
  },
  "confidence": 97
}
```

## Section 4: Patch
```text
*** Begin Patch
*** Update File: durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md
@@
-**Version:** 1.3  
+**Version:** 1.4  
 **Audience:** Backend developers, Frontend developers, API consumers  
-**Last Updated:** 2026-02-09  
+**Last Updated:** 2026-02-10  
 **OpenAPI Source:** `durion-positivity-backend/pos-accounting/openapi.json`
@@
 ## Implementation Links
 
 - Capability: https://github.com/louisburroughs/durion/issues/55
 - Backend child issue: https://github.com/louisburroughs/durion-positivity-backend/issues/122
+- Capability (CAP-278): https://github.com/louisburroughs/durion/issues/278
+- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/472
+- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/473
+- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/474
+- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/475
+- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/476
+- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/477
+- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/478
@@
-| GET | `http://localhost:8080/api/v1/reports/financial/income-statement` | Generate Income Statement |
-| GET | `http://localhost:8080/api/v1/reports/financial/drilldown/journal-lines/{accountId}` | Drilldown to Journal Lines |
-| GET | `http://localhost:8080/api/v1/reports/financial/drilldown/accounts/{statementLineCode}` | Drilldown to Accounts |
-| GET | `http://localhost:8080/api/v1/reports/financial/balance-sheet` | Generate Balance Sheet |
+| GET | `http://localhost:8080/v1/accounting/reports/financial/income-statement` | Generate Income Statement |
+| GET | `http://localhost:8080/v1/accounting/reports/financial/drilldown/journal-lines/{accountId}` | Drilldown to Journal Lines |
+| GET | `http://localhost:8080/v1/accounting/reports/financial/drilldown/accounts/{statementLineCode}` | Drilldown to Accounts |
+| GET | `http://localhost:8080/v1/accounting/reports/financial/balance-sheet` | Generate Balance Sheet |
 | GET | `http://localhost:8080/v1/accounting/traceability/{journalEntryId}` | Get journal traceability |
-| POST | `http://localhost:8080/v1/invoice/invoices` | Regenerate invoice from workorder |
-| GET | `http://localhost:8080/v1/invoice/rules/{customerId}` | Get billing rules |
+| POST | `http://localhost:8080/v1/accounting/invoice/invoices` | Regenerate invoice from workorder |
+| GET | `http://localhost:8080/v1/accounting/invoice/rules/{customerId}` | Get billing rules |
@@
-#### GET <http://localhost:8080/api/v1/reports/financial/income-statement>
+#### GET <http://localhost:8080/v1/accounting/reports/financial/income-statement>
@@
-- Exercise `GET /api/v1/reports/financial/income-statement` with required parameters populated; assert status codes per OpenAPI.
+- Exercise `GET /v1/accounting/reports/financial/income-statement` with required parameters populated; assert status codes per OpenAPI.
@@
-#### GET <http://localhost:8080/api/v1/reports/financial/drilldown/journal-lines/{accountId}>
+#### GET <http://localhost:8080/v1/accounting/reports/financial/drilldown/journal-lines/{accountId}>
@@
-- Exercise `GET /api/v1/reports/financial/drilldown/journal-lines/{accountId}` with required parameters populated; assert status codes per OpenAPI.
+- Exercise `GET /v1/accounting/reports/financial/drilldown/journal-lines/{accountId}` with required parameters populated; assert status codes per OpenAPI.
@@
-#### GET <http://localhost:8080/api/v1/reports/financial/drilldown/accounts/{statementLineCode}>
+#### GET <http://localhost:8080/v1/accounting/reports/financial/drilldown/accounts/{statementLineCode}>
@@
-- Exercise `GET /api/v1/reports/financial/drilldown/accounts/{statementLineCode}` with required parameters populated; assert status codes per OpenAPI.
+- Exercise `GET /v1/accounting/reports/financial/drilldown/accounts/{statementLineCode}` with required parameters populated; assert status codes per OpenAPI.
@@
-#### GET <http://localhost:8080/api/v1/reports/financial/balance-sheet>
+#### GET <http://localhost:8080/v1/accounting/reports/financial/balance-sheet>
@@
-- Exercise `GET /api/v1/reports/financial/balance-sheet` with required parameters populated; assert status codes per OpenAPI.
+- Exercise `GET /v1/accounting/reports/financial/balance-sheet` with required parameters populated; assert status codes per OpenAPI.
@@
-#### POST <http://localhost:8080/v1/invoice/invoices>
+#### POST <http://localhost:8080/v1/accounting/invoice/invoices>
@@
-#### GET <http://localhost:8080/v1/invoice/rules/{customerId}>
+#### GET <http://localhost:8080/v1/accounting/invoice/rules/{customerId}>
@@
-POST http://localhost:8080/v1/invoice/invoices
+POST http://localhost:8080/v1/accounting/invoice/invoices
@@
 | 1.1 | 2026-02-08 | Added Credit Memo endpoints and contracts (CAP-052) |
 | 1.2 | 2026-02-08 | Added AP Payment endpoints and contracts (CAP-053) |
 | 1.3 | 2026-02-09 | Synced Endpoint Summary with OpenAPI (AP endpoints) + added CAP-054 coordination links |
+| 1.4 | 2026-02-10 | Normalized gateway URL paths (reports + invoice routes) + added CAP-278 coordination links |
@@
 ### CAP-054: Period Close, Adjustments, and Reporting
@@
 - No CAP-054-specific Accounting OpenAPI paths detected in `pos-accounting/openapi.json` as of 2026-02-09; this section tracks implementation coordination.
+
+---
+
+### CAP-278: Posting Rule Engine
+
+**Parent Issue:** [durion#278](https://github.com/louisburroughs/durion/issues/278)  
+**Backend Implementation:**
+
+- [durion-positivity-backend#472](https://github.com/louisburroughs/durion-positivity-backend/issues/472)
+- [durion-positivity-backend#473](https://github.com/louisburroughs/durion-positivity-backend/issues/473)
+- [durion-positivity-backend#474](https://github.com/louisburroughs/durion-positivity-backend/issues/474)
+- [durion-positivity-backend#475](https://github.com/louisburroughs/durion-positivity-backend/issues/475)
+- [durion-positivity-backend#476](https://github.com/louisburroughs/durion-positivity-backend/issues/476)
+- [durion-positivity-backend#477](https://github.com/louisburroughs/durion-positivity-backend/issues/477)
+- [durion-positivity-backend#478](https://github.com/louisburroughs/durion-positivity-backend/issues/478)
+
+**Contract Note:**
+
+- Coordination links only. Any API changes introduced by CAP-278 must be reflected in `pos-accounting/openapi.json` and then synced into this guide.
@@
-**Generated:** 2026-02-08 00:00:00 UTC  
+**Generated:** 2026-02-10 00:00:00 UTC  
 **Tool:** `scripts/generate_backend_contract_guides.py`
*** End Patch
```

## Section 5: Self-Critique
- This is a minimal, OpenAPI-driven correction focused on gateway path compliance; it does not attempt to regenerate the full guide content from OpenAPI.
- If CAP-278 introduces new endpoints or schema changes later, those must be driven from updated OpenAPI and then re-synced into the guide.

Next steps: review the patch, then apply it to `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` and re-run any provider contract behavior tests that cover reporting + invoice routes.
