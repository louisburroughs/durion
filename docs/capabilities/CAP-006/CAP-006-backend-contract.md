# CAP:006 — Backend Contract Implementation Notes

**Capability:** CAP:006 — Complete Workorder
**Manifest:** docs/capabilities/CAP-006/CAPABILITY_MANIFEST.yaml
**Source OpenAPI:** durion-positivity-backend/pos-workorder/openapi.json
**Guide updated:** domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md

## Summary
- Updated gateway path examples to use `http://localhost:8080/v1/workexec/...` (API Gateway format).
- Reconciled Implementation Links to the backend child issues from the manifest.
- Recorded a short OpenAPI delta (added/changed paths) for reviewer context.

## OpenAPI delta (authoritative -> gateway-mapped)
- Added
  - `/v1/workexec/approvalConfigurations/{approvalId}` (present in OpenAPI under `/v1/workexec/approvalConfigurations/{approvalId}`)
- Changed (gateway-mapped from OpenAPI paths)
  - `/v1/workexec/estimates` (from OpenAPI `/v1/workorders/estimates`)
  - `/v1/workexec/estimates/{estimateId}`
  - `/v1/workexec/workorders` (from OpenAPI `/v1/workorders`)
  - `/v1/workexec/workorders/{workorderId}/start`
  - `/v1/workexec/workorders/{workorderId}/complete`
  - `/v1/workexec/workorders/{workorderId}/approval`
  - `/v1/workexec/workorders/{workorderId}/changeRequests`
  - `/v1/workexec/workorders/{workorderId}/transitions`
  - `/v1/workexec/workorders/{workorderId}/snapshots`
- Removed: []

## Files changed
- domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md (gateway paths normalized, implementation links updated)

## Backend child issues (from manifest)
- https://github.com/louisburroughs/durion-positivity-backend/issues/154
- https://github.com/louisburroughs/durion-positivity-backend/issues/153
- https://github.com/louisburroughs/durion-positivity-backend/issues/152
- https://github.com/louisburroughs/durion-positivity-backend/issues/151
- https://github.com/louisburroughs/durion-positivity-backend/issues/150

## Next steps for reviewers
- Verify provider ContractBehaviorIT tests reflect the gateway-mapped paths above.
- Confirm no remaining direct-service hostnames/ports exist in the guide or UI wiring.
- If additional OpenAPI endpoints should be documented, add them as follow-up edits referencing the backend child issues.

---

*(Generated automatically by Backend Contract Guide Updater agent)*
