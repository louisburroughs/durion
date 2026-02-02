# CAP:090 — Backend Contract Update (CRM)

## Inputs

- Manifest: `docs/capabilities/CAP-090/CAPABILITY_MANIFEST.yaml`
- OpenAPI: `durion-positivity-backend/pos-customer/openapi.json`
- Contract Guide Updated: `domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Scope

Update the CRM backend contract guide with CAP:090 coordination links and contract details for contact management endpoints (roles + communication preferences), using the OpenAPI as the source of truth.

## Extracted Backend Issues (from manifest)

- https://github.com/louisburroughs/durion-positivity-backend/issues/108
- https://github.com/louisburroughs/durion-positivity-backend/issues/107
- https://github.com/louisburroughs/durion-positivity-backend/issues/106

## OpenAPI Delta Summary

No endpoint add/remove delta detected between the current guide’s Endpoint Summary table and the provided OpenAPI.

## Changes Applied

### Contract Guide Updates

- Bumped guide version to `1.2`.
- Updated OpenAPI Source reference to `pos-customer/openapi.json`.
- Added CAP:090 implementation links block (parent capability, parent stories, backend child issues).
- Enriched these endpoint sections with request/response schema snippets and provider test hints (no invented examples):
  - `GET /v1/crm/accounts/parties/{partyId}/communicationPreferences`
  - `POST /v1/crm/accounts/parties/{partyId}/communicationPreferences`
  - `GET /v1/crm/accounts/parties/{partyId}/contacts`
  - `PUT /v1/crm/accounts/parties/{partyId}/contacts/{contactId}/roles`
- Added additional schema sections in “Entity-Specific Contracts” for:
  - `UpsertCommunicationPreferencesRequest`
  - `UpsertCommunicationPreferencesResponse`
  - `ContactWithRoles`, `AssignedRole`
  - `UpdateContactRolesRequest`, `RoleAssignment`, `UpdateContactRolesResponse`

### Validation Notes

- The newly inserted JSON Schema blocks (labeled “Request Schema/Response Schema (JSON Schema, abridged)”) parse as valid JSON.
- The contract guide contains other historic `json` blocks that may not be strict JSON (pre-existing); the validation was scoped to newly inserted blocks.

## Human Checklist

- Review TODOs in the endpoint sections and confirm they map to CAP:090 backend issue intent.
- Confirm “stub” endpoints under `/v1/crm/parties/...` remain correct (these are still `501` in the current OpenAPI).
- When backend implementation finalizes behavior, replace TODOs with concrete example requests/responses and provider test assertions.

## JSON Summary

```json
{
  "capability_id": "CAP:090",
  "manifest_path": "docs/capabilities/CAP-090/CAPABILITY_MANIFEST.yaml",
  "backend_issues": [
    "https://github.com/louisburroughs/durion-positivity-backend/issues/108",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/107",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/106"
  ],
  "updated_files": [
    "domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md",
    "docs/capabilities/CAP-090/CAP-090-backend-contract.md"
  ],
  "openapi_changes": {
    "added": [],
    "changed": [],
    "removed": []
  },
  "confidence": 97,
  "applied": true,
  "commit": "b97edab"
}
```
