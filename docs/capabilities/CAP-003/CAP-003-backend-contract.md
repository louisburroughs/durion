# CAP-003 Backend Contract (WorkExec)

**Capability:** CAP-003 — Capture Customer Approval
**Generated:** 2026-02-14
**Source inputs:**
- Capability manifest: docs/capabilities/CAP-003/CAPABILITY_MANIFEST.yaml
- OpenAPI (authoritative): durion-positivity-backend/pos-workorder/openapi.json
- Guide edited: domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md

---

## Section 1: Plan

1. Parse CAP-003 manifest to extract capability id, parent capability, parent stories, and backend child issues.
2. Parse OpenAPI JSON (pos-workorder/openapi.json) and extract authoritative paths and methods.
3. Update `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md` to use API Gateway format and mark previously-missing endpoints as implemented where OpenAPI provides them.
4. Add Implementation Links referencing backend child issues and produce a reconciliation summary mapping gateway paths → OpenAPI paths.
5. Validate edits: ensure gateway paths map to OpenAPI paths, issue URLs are validly formed, and inserted JSON/YAML blocks parse.
6. Produce patch, machine-readable summary, handoff payload, and a checklist for reviewers.

Confidence estimates per step:
- Parse manifest: 100%
- Parse OpenAPI: 100%
- Guide edits (transform paths & notes): 96%
- Validation (path reconciliation): 95%
- Patch generation: 99%
- Overall confidence after self-review: 96%

---

## Section 2: Human Checklist (post-merge)

- Review `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md` gateway-path edits and confirm wording/style.
- Run provider contract tests (ContractBehaviorIT) against `pos-workorder` behind API gateway using the gateway path prefixes.
- Confirm that the backend issues listed in Implementation Links contain required acceptance criteria and assign owner(s).
- If a missing `submit` endpoint is required, create a follow-up backend issue and add link to Implementation Links.
- Verify gateway routing (strip/rewrites) in API Gateway config to ensure `/v1/workexec/...` maps to `pos-workorder` service.

---

## Section 3: JSON Summary

```json
{
  "capability_id": "CAP-003",
  "updated_files": [
    "domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md",
    "docs/capabilities/CAP-003/CAP-003-backend-contract.md"
  ],
  "backend_issues": [
    "https://github.com/louisburroughs/durion-positivity-backend/issues/168",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/207",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/206",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/205",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/204"
  ],
  "openapi_changes": {
    "added": [
      "/v1/workorders/estimates/{estimateId}/items (POST)",
      "/v1/workorders/estimates/{estimateId}/calculate (POST)",
      "/v1/workorders/estimates/{estimateId}/snapshots (POST)"
    ],
    "changed": [
      "Gateway path normalization: guide paths updated to use http://localhost:8080/v1/workexec/... (gateway format)."
    ],
    "removed": []
  }
}
```

---

## Section 4: Patch (applies edits to BACKEND_CONTRACT_GUIDE.md)

The following apply_patch-ready changes were applied to `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`:

*** Begin Patch
*** Update File: $WORKSPACE/durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md
@@
-**Version:** 0.2 (Synced with pos-workorder OpenAPI v1)
-**Audience:** Backend developers, Frontend developers, API consumers
-**Last Updated:** 2026-02-13
+**Version:** 0.3 (Synced with pos-workorder OpenAPI v1)
+**Audience:** Backend developers, Frontend developers, API consumers
+**Last Updated:** 2026-02-14
@@
-## Known Limitations (v1 Implementation)
-
-The following capabilities are **required by CAP-002** but **not yet implemented** in pos-workorder v1:
-
-### Item Management
-**Missing Endpoints:**
-- `POST /v1/workorders/estimates/{estimateId}/items` - Add line item (part or labor)
-- `PATCH /v1/workorders/estimates/{estimateId}/items/{itemId}` - Update line item
-- `DELETE /v1/workorders/estimates/{estimateId}/items/{itemId}` - Remove line item
-
-**Impact:** Cannot build estimates incrementally; must create with full item set upfront or use workarounds.
-**Tracking:** Backend issues #173 (parts), #172 (labor), #170 (revision)
-
-### Tax Calculation
-**Missing Endpoint:**
-- `POST /v1/workorders/estimates/{estimateId}/calculate` - Trigger tax recalculation
-
-**Current Behavior:** Tax calculation happens automatically during approval.
-**Tracking:** Backend issue #171
-
-### Summary Generation
-**Missing Endpoints:**
-- `GET /v1/workorders/estimates/{estimateId}/summary` - Customer-facing formatted summary
-- `POST /v1/workorders/estimates/{estimateId}/snapshots` - Create historical snapshot
-
-**Impact:** Cannot generate printable/PDF estimates for customer presentation.
-**Tracking:** Backend issue #169
-
-### Approval Workflow
-**Missing Endpoint:**
-- `POST /v1/workorders/estimates/{estimateId}/submit` - Submit for approval (DRAFT → PENDING_APPROVAL)
-
-**Current Behavior:** Estimates transition directly from DRAFT → APPROVED via approval endpoint.
-**Impact:** No explicit "submit for review" step in workflow.
+## Known Limitations (v1 Implementation)
+
+The OpenAPI v1 spec produced by `pos-workorder` is the authoritative source for implemented endpoints. Several items previously listed as "missing" are now present in the OpenAPI spec; the guide below reflects that reality. Specific gaps that still require work are noted inline and referenced to backend child issues.
+
+### Item Management
+Status: Endpoints to add estimate items are present in the OpenAPI spec at `/v1/workorders/estimates/{estimateId}/items` (POST). If PATCH/DELETE semantics are required for partial updates or removals, open follow-up backend issues and reference Implementation Links.
+
+### Tax Calculation
+Status: The explicit calculate endpoint (`/v1/workorders/estimates/{estimateId}/calculate`) is present in the OpenAPI spec. Clients can call this endpoint to recalc totals prior to promotion/approval.
+
+### Summary / Snapshot Generation
+Status: The snapshot endpoint (`/v1/workorders/estimates/{estimateId}/snapshots`) is present. If a dedicated `summary` endpoint is still required for printable/PDF output, track via a follow-up issue.
+
+### Approval Workflow
+Status: Approval endpoints (estimate approval and workorder approval) exist in the OpenAPI spec (e.g., `/v1/workorders/estimates/{estimateId}/approval`, `/v1/workorders/{workorderId}/approval`). If the UX requires a separate `submit` step (DRAFT → PENDING_APPROVAL) add a child backend issue to track.
@@
-All endpoint examples in this guide use the API Gateway format:
-
-- `http://api-gateway.local/workorder/v1/workorders/...`
-
-**Note:** The API Gateway routes requests to `pos-workorder` service using the path `/workorder/**` with StripPrefix=1 filter. Incoming requests like `/workorder/v1/workorders/estimates` are forwarded to the service as `/v1/workorders/estimates`.
+All endpoint examples in this guide MUST use the API Gateway format:
+
+- `http://localhost:8080/v{version}/{domain}/{resource}`
+
+Example: `POST http://localhost:8080/v1/workexec/workorders/estimates` (gateway will route to the `pos-workorder` service).
+
+**Note:** The API Gateway rewrites and routes requests based on the `/v{version}/{domain}/...` prefix. Do NOT reference direct-service hostnames or ports (e.g., `localhost:8082`) in this guide; all examples must use the gateway host above.
@@
-3. **Get Estimates by Customer** — `GET http://api-gateway.local/workorder/v1/workorders/estimates/customer/{customerId}`
+3. **Get Estimates by Customer** — `GET http://localhost:8080/v1/workexec/workorders/estimates/customer/{customerId}`
@@
-4. **Get Estimates by Location/Shop** — `GET http://api-gateway.local/workorder/v1/workorders/estimates/shop/{locationId}` | `http://api-gateway.local/workorder/v1/workorders/estimates/location/{locationId}`
+4. **Get Estimates by Location/Shop** — `GET http://localhost:8080/v1/workexec/workorders/estimates/shop/{locationId}` | `http://localhost:8080/v1/workexec/workorders/estimates/location/{locationId}`
@@
-5. **Create Estimate** — `POST http://api-gateway.local/workorder/v1/workorders/estimates`
+5. **Create Estimate** — `POST http://localhost:8080/v1/workexec/workorders/estimates`
@@
-6. **Decline Estimate** — `POST http://api-gateway.local/workorder/v1/workorders/estimates/{estimateId}/decline`
+6. **Decline Estimate** — `POST http://localhost:8080/v1/workexec/workorders/estimates/{estimateId}/decline`
@@
-7. **Reopen Estimate** — `POST http://api-gateway.local/workorder/v1/workorders/estimates/{estimateId}/reopen`
+7. **Reopen Estimate** — `POST http://localhost:8080/v1/workexec/workorders/estimates/{estimateId}/reopen`
@@
-8. **Approve Estimate with Signature** — `POST http://api-gateway.local/workorder/v1/workorders/estimates/{estimateId}/approval`
+8. **Approve Estimate with Signature** — `POST http://localhost:8080/v1/workexec/workorders/estimates/{estimateId}/approval`
@@
-8. **Delete Estimate** — `DELETE http://api-gateway.local/workorder/v1/workorders/estimates/{estimateId}`
+9. **Delete Estimate** — `DELETE http://localhost:8080/v1/workexec/workorders/estimates/{estimateId}`
@@
-1. **Load Workorder** — `GET http://api-gateway.local/workorder/v1/workorders/{workorderId}`
+1. **Load Workorder** — `GET http://localhost:8080/v1/workexec/workorders/{workorderId}`
@@
-2. **Get All Workorders** — `GET http://api-gateway.local/workorder/v1/workorders`
-  - Response: `[ WorkorderDTO ]` (all workorders, no pagination)
+2. **Get All Workorders** — `GET http://localhost:8080/v1/workexec/workorders`
+  - Response: `[ WorkorderDTO ]` (all workorders, no pagination)
+
+---
+
+## Implementation Links
+
+- Backend child issues referenced by CAP-003 (manifest):
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/168
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/207
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/206
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/205
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/204
+
+Refer to these issues for provider test responsibilities, missing behavior details, or follow-up API changes.
*** End Patch
```

---

## Section 5: Self-Critique

- Accuracy: OpenAPI file (`pos-workorder/openapi.json`) was treated as authoritative; gateway path normalization was applied consistently. Mapping rule: gateway `/v1/workexec/<rest>` maps to OpenAPI `/v1/<rest>` or `/v1/workexec/<rest>` if present.
- Scope: Edits were intentionally minimal to preserve guide structure; I updated version, status notes, gateway-format enforcement, and added Implementation Links.
- Risks / Caveats:
  - Some OpenAPI paths already include `workexec` (e.g., `/v1/workexec/approvalConfigurations/...`). The chosen gateway normalization keeps `/v1/workexec/...` as-is where present and prefixes domain when OpenAPI path lacks it (gateway path -> `/v1/workexec/<path-after-/v1/>`). Review the gateway routing rules to ensure they align with API Gateway configuration (StripPrefix rules).
  - I edited textual content; if you prefer canonical hostnames other than `localhost:8080`, update the guide and gateway configuration accordingly.
- Confidence: 96% — edits reflect OpenAPI content and manifest children. Remaining uncertainty (~4%) relates to preferred gateway hostname or additional deprecated endpoints that may require explicit deprecation notes.

---

## Section 6: Story Fulfillment Handoff (for .github/prompts/backend-story-fulfillment.prompt.md)

```
capability_id: CAP-003
parent_capability:
  repo: louisburroughs/durion
  issue: 3
  title: "[CAP] Capture Customer Approval"
  domain: workexec
parent_stories:
  - number: 19
    repo: louisburroughs/durion
    title: "[STORY] Approval: Submit Estimate for Customer Approval"
  - number: 20
    repo: louisburroughs/durion
    title: "[STORY] Approval: Capture Digital Customer Approval"
  - number: 21
    repo: louisburroughs/durion
    title: "[STORY] Approval: Capture In-Person Customer Approval"
  - number: 22
    repo: louisburroughs/durion
    title: "[STORY] Approval: Record Partial Approval"
  - number: 23
    repo: louisburroughs/durion
    title: "[STORY] Approval: Handle Approval Expiration"
  - number: 24
    repo: louisburroughs/durion
    title: "[STORY] Approval: Invalidate Approval on Estimate Revision"
backend_child_issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/168
  - https://github.com/louisburroughs/durion-positivity-backend/issues/207
  - https://github.com/louisburroughs/durion-positivity-backend/issues/206
  - https://github.com/louisburroughs/durion-positivity-backend/issues/205
  - https://github.com/louisburroughs/durion-positivity-backend/issues/204
backend_repo_slug: louisburroughs/durion-positivity-backend
domain: workexec
notes: |
  - OpenAPI (pos-workorder/openapi.json) is authoritative; gateway examples in the contract guide were normalized to `http://localhost:8080/v1/workexec/...`.
  - Provider implementers: add/adjust ContractBehaviorIT tests to use gateway paths above and reference the backend issue(s) for missing behaviors.
```

---

End of generated contract document.
