# CAP:007 — Backend Contract Summary

**Capability:** CAP:007 — Convert Workorder to Invoice
**Domain:** workexec
**Producer OpenAPI:** pos-workorder (pos-workorder/target/openapi.json)

---

## Section A — Short Plan

1. Validate `CAPABILITY_MANIFEST.yaml`, `openapi.json`, and `BACKEND_CONTRACT_GUIDE.md` exist and parse.
2. Extract backend child issues and construct handoff payloads for each story.
3. Compute OpenAPI → Gateway path transformations and list delta (added/changed/removed).
4. Update `BACKEND_CONTRACT_GUIDE.md` Implementation Links and add a short OpenAPI delta summary.
5. Produce machine-readable JSON summary and handoff blocks; create this document under `docs/capabilities/CAP-007/`.
6. Run lightweight validations: JSON/YAML fragments parse, issue URLs are validly formed, gateway path compliance.

## Section B — OpenAPI Delta (authoritative)

Notes:
- Source of truth: `/home/louisb/Projects/durion-positivity-backend/pos-workorder/openapi.json` (`openapi: 3.1.0`, `info.version: v1`).
- Domain inferred from manifest: `workexec`.
- Gateway format applied to OpenAPI paths by ensuring `http://localhost:8080/v{version}/{domain}/{resource}`.

OpenAPI changes (short):

- Added (present in OpenAPI, not documented in-depth in this guide):
  - `POST/GET/PUT` http://localhost:8080/v1/workexec/workorders/{workorderId}/technician
  - `PUT` http://localhost:8080/v1/workexec/workorders/{workorderId}/labor/{entryId}/adjust
  - `POST` http://localhost:8080/v1/workexec/workorders/{workorderId}/services/{serviceId}/labor/start
  - `POST/PUT` http://localhost:8080/v1/workexec/workorders/{workorderId}/parts/issue
  - `POST` http://localhost:8080/v1/workexec/workorders/{workorderId}/parts/return
  - `POST` http://localhost:8080/v1/workexec/workorders/{workorderId}/parts/consume
  - `POST` http://localhost:8080/v1/workexec/workorders/{workorderId}/parts/correct
  - `GET` http://localhost:8080/v1/workexec/approvalConfigurations/{approvalId}
  - `GET` http://localhost:8080/v1/workexec/approvalConfigurations/applicable

- Changed (gateway mapping applied — OpenAPI lacks explicit gateway host/domain prefix):
  - OpenAPI `/v1/workorders` → Gateway `http://localhost:8080/v1/workexec/workorders`
  - OpenAPI `/v1/workorders/estimates` → Gateway `http://localhost:8080/v1/workexec/estimates`
  - OpenAPI `/v1/workorders/{workorderId}/transitions` → Gateway `http://localhost:8080/v1/workexec/workorders/{workorderId}/transitions`
  - OpenAPI `/v1/workorders/{workorderId}/snapshots` → Gateway `http://localhost:8080/v1/workexec/workorders/{workorderId}/snapshots`

- Removed: none detected in this verification pass. If a guide-only endpoint is not present in OpenAPI, mark it deprecated and track with a backend issue.

## Section C — Validation Summary

Validations performed:
- `openapi.json` parsed successfully (OpenAPI object with `paths` found).
- `CAPABILITY_MANIFEST.yaml` parsed and contains `stories[].children.backend` entries (issues: 149,148,147,146,145).
- `BACKEND_CONTRACT_GUIDE.md` exists and updated in-place (implementation links and delta summary inserted).
- Gateway path compliance: every example path in the guide uses `http://localhost:8080/v1/workexec/...` format.
- JSON/YAML fragments included in this document are minimal and valid JSON where used.

## Section D — Machine-readable JSON Summary

```json
{
  "capability_id": "CAP:007",
  "manifest_path": "docs/capabilities/CAP-007/CAPABILITY_MANIFEST.yaml",
  "backend_issues": [
    "https://github.com/louisburroughs/durion-positivity-backend/issues/149",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/148",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/147",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/146",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/145"
  ],
  "updated_files": [
    "domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md",
    "docs/capabilities/CAP-007/CAP-007-backend-contract.md"
  ],
  "openapi_changes": {
    "added": [
      "/v1/workexec/workorders/{workorderId}/technician",
      "/v1/workexec/workorders/{workorderId}/labor/{entryId}/adjust",
      "/v1/workexec/workorders/{workorderId}/services/{serviceId}/labor/start",
      "/v1/workexec/workorders/{workorderId}/parts/issue",
      "/v1/workexec/workorders/{workorderId}/parts/return",
      "/v1/workexec/workorders/{workorderId}/parts/consume",
      "/v1/workexec/workorders/{workorderId}/parts/correct",
      "/v1/workexec/approvalConfigurations/{approvalId}",
      "/v1/workexec/approvalConfigurations/applicable"
    ],
    "changed": [
      "/v1/workexec/workorders",
      "/v1/workexec/estimates",
      "/v1/workexec/workorders/{workorderId}/transitions",
      "/v1/workexec/workorders/{workorderId}/snapshots"
    ],
    "removed": []
  },
  "confidence": 96
}
```

## Section E — Handoff Payloads (for `.github/prompts/backend-story-fulfillment.prompt.md`)

Below are compact payloads (one per parent story) for the backend-story-fulfillment prompt. Replace placeholders if needed when pasting.

- Story: Issue 43 — Generate Invoice Draft from Completed Workorder

```yaml
capability_label: CAP:007
capability_id: CAP:007
domain: workexec
parent_capability_number: 7
parent_capability_url: https://github.com/louisburroughs/durion/issues/7
parent_capability_title: "[CAP] Convert Workorder to Invoice"
parent_stories_list:
  - "43: [STORY] Invoicing: Generate Invoice Draft from Completed Workorder"
backend_child_issues:
  - "https://github.com/louisburroughs/durion-positivity-backend/issues/149"
backend_repo_slug: louisburroughs/durion-positivity-backend
```

- Story: Issue 44 — Calculate Taxes, Fees, and Totals on Invoice

```yaml
capability_label: CAP:007
capability_id: CAP:007
domain: workexec
parent_capability_number: 7
parent_capability_url: https://github.com/louisburroughs/durion/issues/7
parent_capability_title: "[CAP] Convert Workorder to Invoice"
parent_stories_list:
  - "44: [STORY] Invoicing: Calculate Taxes, Fees, and Totals on Invoice"
backend_child_issues:
  - "https://github.com/louisburroughs/durion-positivity-backend/issues/148"
backend_repo_slug: louisburroughs/durion-positivity-backend
```

- Story: Issue 45 — Preserve Traceability Links (Estimate/Approval/Workorder)

```yaml
capability_label: CAP:007
capability_id: CAP:007
domain: workexec
parent_capability_number: 7
parent_capability_url: https://github.com/louisburroughs/durion/issues/7
parent_capability_title: "[CAP] Convert Workorder to Invoice"
parent_stories_list:
  - "45: [STORY] Invoicing: Preserve Traceability Links (Estimate/Approval/Workorder)"
backend_child_issues:
  - "https://github.com/louisburroughs/durion-positivity-backend/issues/147"
backend_repo_slug: louisburroughs/durion-positivity-backend
```

- Story: Issue 46 — Support Authorized Invoice Adjustments

```yaml
capability_label: CAP:007
capability_id: CAP:007
domain: workexec
parent_capability_number: 7
parent_capability_url: https://github.com/louisburroughs/durion/issues/7
parent_capability_title: "[CAP] Convert Workorder to Invoice"
parent_stories_list:
  - "46: [STORY] Invoicing: Support Authorized Invoice Adjustments"
backend_child_issues:
  - "https://github.com/louisburroughs/durion-positivity-backend/issues/146"
backend_repo_slug: louisburroughs/durion-positivity-backend
```

- Story: Issue 47 — Finalize and Issue Invoice

```yaml
capability_label: CAP:007
capability_id: CAP:007
domain: workexec
parent_capability_number: 7
parent_capability_url: https://github.com/louisburroughs/durion/issues/7
parent_capability_title: "[CAP] Convert Workorder to Invoice"
parent_stories_list:
  - "47: [STORY] Invoicing: Finalize and Issue Invoice"
backend_child_issues:
  - "https://github.com/louisburroughs/durion-positivity-backend/issues/145"
backend_repo_slug: louisburroughs/durion-positivity-backend
```

---

## Section F — Notes & Next Steps

- Reviewers: confirm the representative `added` list above matches implementation priorities; if other endpoints should be surfaced to UI, add them to the guide and link backend issues.
- If any OpenAPI examples are incomplete, add `TODO` items on the corresponding backend issue so the producer can add richer examples in `openapi.json`.

