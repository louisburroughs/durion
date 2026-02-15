## CAP:004 — Backend Contract Summary

**Capability:** Promote Estimate to Workorder
**Domain:** workexec
**OpenAPI source:** pos-workorder/target/openapi.json

### Summary

This document records the OpenAPI-authoritative contract changes for CAP:004 and maps producer endpoints to the API Gateway format used by consumers.

### Backend child issues (implementation)

- https://github.com/louisburroughs/durion-positivity-backend/issues/167
- https://github.com/louisburroughs/durion-positivity-backend/issues/166
- https://github.com/louisburroughs/durion-positivity-backend/issues/165
- https://github.com/louisburroughs/durion-positivity-backend/issues/164
- https://github.com/louisburroughs/durion-positivity-backend/issues/163
- https://github.com/louisburroughs/durion-positivity-backend/issues/162

### OpenAPI changes (computed)

- added: `/v1/workexec/approvalConfigurations/{approvalId}`, `/v1/workexec/approvalConfigurations` (new approval configuration APIs surfaced)
- changed: normalized workorders paths to include domain prefix `/v1/workexec/workorders` (OpenAPI produced `/v1/workorders` for some paths; gateway format uses domain)
- removed: []

### Gateway path mapping (authoritative mapping rules)

- API Gateway format: `http://localhost:8080/v{version}/{domain}/{resource}`
- Domain (inferred from manifest): `workexec`
- Transform rule applied: For OpenAPI paths that begin with `/v1/workorders` the gateway path is `http://localhost:8080/v1/workexec/workorders{...}`

### Key endpoints (OpenAPI-derived + gateway examples)

1) Create Estimate

POST http://localhost:8080/v1/workexec/workorders/estimates

Request example (matches `CreateEstimateRequest`):

```json
{
  "customerId": "550e8400-e29b-41d4-a716-446655440001",
  "vehicleId": "550e8400-e29b-41d4-a716-446655440002",
  "locationId": "550e8400-e29b-41d4-a716-446655440003"
}
```

Responses: 200 (EstimateResponse), 400, 403, 500

Provider test hint: Verify creation returns 200 and `estimateNumber` populated; assert `status == "DRAFT"` and `customerId` equals request.

2) Submit Estimate for Approval (creates immutable snapshot)

POST http://localhost:8080/v1/workexec/workorders/estimates/{estimateId}/submit-for-approval

Responses: 200 (EstimateResponse), 400, 404

Provider test hint: Submit a DRAFT estimate and assert returned `status == "PENDING_APPROVAL"` and that a snapshot exists (call snapshot endpoint).

3) Create Workorder (producer path normalized)

POST http://localhost:8080/v1/workexec/workorders

Request example (`CreateWorkorderRequest`):
```json
{
  "estimateId": "550e8400-e29b-41d4-a716-446655440001",
  "customerId": "550e8400-e29b-41d4-a716-446655440002"
}
```

Responses: 200 (WorkorderResponse)

Provider test hint: Promote an approved estimate to workorder; assert returned `workorder.id` and `estimateId` linkage.

### Validation notes & TODOs

- The OpenAPI spec contains `api`-prefixed tax endpoints (`/api/v1/tax/*`). Confirm routing expectations for tax endpoints with backend owners; if these should be exposed under `workexec` domain via gateway, open a follow-up task. (See backend issues above.)
- Ensure ContractBehaviorIT tests exercise the error envelope and idempotency (`Idempotency-Key`) on mutation endpoints.

---

Generated from OpenAPI at `pos-workorder/openapi.json` and manifest `docs/capabilities/CAP-004/CAPABILITY_MANIFEST.yaml`.
