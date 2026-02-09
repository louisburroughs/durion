# CAP-053 Backend Contract Update — Accounting

## Inputs

- CAPABILITY_MANIFEST_PATH: `docs/capabilities/CAP-053/CAPABILITY_MANIFEST.yaml`
- OPENAPI_PATH (authoritative): `durion-positivity-backend/pos-accounting/openapi.json`
- BACKEND_CONTRACT_GUIDE_PATH: `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Extracted Coordination Links (from manifest)

- Parent capability issue: https://github.com/louisburroughs/durion/issues/53
- Backend child issue: https://github.com/louisburroughs/durion-positivity-backend/issues/128
- Frontend child issue: https://github.com/louisburroughs/durion-moqui-frontend/issues/192

## OpenAPI/Guide Validation Notes

- OpenAPI operations: **59**
- Guide endpoint summary rows: **59**
- Guide endpoint details: **47**
- Endpoint coverage comparison: **0 missing / 0 extra** (guide operations match the current OpenAPI operations by method+path)
- Gateway URL format scan: no direct-service ports found (no `localhost:<non-8080>` references)

## Proposed Update Summary

- No API shape changes required (guide already matches OpenAPI method+path inventory).
- Documentation alignment needed:
  - Fix OpenAPI source path references to the workspace-relative OpenAPI path.
  - Add CAP-053 implementation links (and keep prior CAP-052 section).
  - Add a change log entry for CAP-053 link update.

## Deliverable

The proposed patch is provided in the accompanying Copilot response as an `apply_patch`-ready block, scoped to `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` only.
