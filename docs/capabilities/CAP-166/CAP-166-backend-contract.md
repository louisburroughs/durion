# CAP:166 — Backend Contract Summary

**Capability:** CAP:166 — Cost Management (Acquisition & Cost Models)
**Domain:** product
**Backend repo:** louisburroughs/durion-positivity-backend
**Backend child issue:** https://github.com/louisburroughs/durion-positivity-backend/issues/195
**Manifest path:** docs/capabilities/CAP-166/CAPABILITY_MANIFEST.yaml

## Plan (short)
- Validate inputs (manifest, OpenAPI, guide) are present and parseable.
- Compute OpenAPI -> Gateway path transformations and detect missing endpoints.
- Patch `domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md` to align links and mark removed endpoints.
- Create this capability implementation doc with JSON summary and handoff payload.
- Run lightweight verification of JSON code blocks and URLs.
- Provide checklist, JSON summary, and handoff block for story fulfillment.

## OpenAPI delta (high level)
- Added (present in current OpenAPI but not previously documented in guide):
  - `/v1/products/{productId}/lifecycle` (GET, PUT)
  - `/v1/products/{productId}/replacements` (POST)
- Removed / Deprecated (documented in guide but NOT present in current OpenAPI):
  - `/v1/products/catalog/name/{name}`
  - `/v1/products/name/{name}`
  - `/v1/products/noninventory/name/{name}`
- Changed: no structural changes detected that conflict with documented method names; OpenAPI is authoritative.

## Files changed (applied)
- Updated: domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md
- New file: docs/capabilities/CAP-166/CAP-166-backend-contract.md (this file)

## Handoff payload (for each story)

- capability_label: "CAP:166"
- capability_id: "CAP:166"
- domain: "product"
- parent_capability_number: 166
- parent_capability_url: https://github.com/louisburroughs/durion/issues/166
- parent_capability_title: "[CAP] Cost Management (Acquisition & Cost Models)"
- parent_stories_list: []
- backend_child_issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/195


## JSON Summary
```json
{
  "capability_id": "CAP:166",
  "manifest_path": "docs/capabilities/CAP-166/CAPABILITY_MANIFEST.yaml",
  "backend_issues": ["https://github.com/louisburroughs/durion-positivity-backend/issues/195"],
  "updated_files": ["domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md"],
  "openapi_changes": { "added": ["/v1/products/{productId}/lifecycle","/v1/products/{productId}/replacements"], "changed": [], "removed": ["/v1/products/catalog/name/{name}","/v1/products/name/{name}","/v1/products/noninventory/name/{name}"] },
  "confidence": 95
}
```

## Notes
- OpenAPI at `/home/louisb/Projects/durion-positivity-backend/pos-catalog/target/openapi.json` is authoritative; any request/response contract must be derived from it.
- The guide was updated minimally to correct authoritative references and mark deprecated endpoints.
- Cross-repo traceability: backend implementation for this contract update is in `louisburroughs/durion-positivity-backend` PR #547.

---
*End of CAP:166 backend contract brief.*
