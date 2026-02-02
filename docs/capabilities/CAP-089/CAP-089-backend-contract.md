# CAP-089 — Backend Contract Guide Update (CRM)

## Inputs

- `BACKEND_CONTRACT_GUIDE_PATH`: `domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- `OPENAPI_PATH`: `durion-positivity-backend/pos-customer/openapi.json`
- `CAPABILITY_MANIFEST_PATH`: `docs/capabilities/CAP-089/CAPABILITY_MANIFEST.yaml`

## What I validated

```text
- CAPABILITY_MANIFEST.yaml parses (YAML)
- openapi.json parses (OpenAPI 3.1 JSON) and contains `paths`
- BACKEND_CONTRACT_GUIDE.md exists and is writable
```

OpenAPI summary (from `durion-positivity-backend/pos-customer/openapi.json`):

```text
paths: 20
operations (method+path): 27
```

Guide vs OpenAPI endpoint coverage check:

```text
openapi ops: 27
guide ops: 27
added: 0
removed: 0
```

## Capability linkage extracted (CAP-089)

- Parent capability: https://github.com/louisburroughs/durion/issues/89
- Parent stories:
  - https://github.com/louisburroughs/durion/issues/95
  - https://github.com/louisburroughs/durion/issues/96
  - https://github.com/louisburroughs/durion/issues/97
  - https://github.com/louisburroughs/durion/issues/98
- Backend child issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/112
  - https://github.com/louisburroughs/durion-positivity-backend/issues/111
  - https://github.com/louisburroughs/durion-positivity-backend/issues/110
  - https://github.com/louisburroughs/durion-positivity-backend/issues/109

## Proposed doc change (summary)

No OpenAPI delta was detected versus the existing CRM backend contract guide’s endpoint headings. The only change proposed is to add CAP-089 coordination links into the guide (so reviewers can trace the contract to the issues that implement it), and to bump the guide version/date/change log accordingly.

## Next step

Apply the patch in `domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md` after approval.
