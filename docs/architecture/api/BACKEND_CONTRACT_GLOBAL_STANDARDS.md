# Backend Contract Global Standards

**Status:** Normative  
**Audience:** Backend engineers, frontend engineers, agent workflows  
**Applies To:** `domains/*/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Purpose

Define one cross-domain standard for backend contract documentation so:

- API schemas are maintained in one place,
- domain guides stay concise and behavior-focused,
- frontend developers can quickly find the right API call by workflow.

## Source Of Truth Matrix

| Artifact | Canonical Source | Allowed In Domain Guide | Not Allowed In Domain Guide |
| --- | --- | --- | --- |
| Endpoints, request/response schemas, enums, response codes | Module OpenAPI (`durion-positivity-backend/pos-*/openapi.yaml`) | `operationId`, short endpoint reference, and links | Full copied schema blocks, full endpoint dumps, duplicated enum catalogs |
| Domain behavior and workflow assertions | `BACKEND_CONTRACT_GUIDE.md` | Required | N/A |
| Architecture decisions and invariants | ADRs + `AGENT_GUIDE.md` | ADR IDs + concise applicability notes | Full ADR restatement |
| Capability linkage and issue traceability | Capability manifest + issue tracker | Capability ID, issue links, test IDs | Unlinked or untraceable behavior statements |

## Required Structure For Each Domain Guide

Every `BACKEND_CONTRACT_GUIDE.md` must include:

1. `Purpose & Scope`
2. `How to Use This Guide` (coder + frontend quick usage)
3. `Domain Invariants` (behavior rules that are not OpenAPI schema)
4. `Capability Index` (capability IDs and links)
5. `Capability Sections` (one per capability, behavior-focused)
6. `Frontend API Lookup` (UI task -> `operationId` -> method/path -> notes)
7. `Events & Cross-Domain Dependencies` (emit/consume + constraints)
8. `Verification Metadata` (OpenAPI source path and verification timestamp/hash)
9. `References`

## Capability Section Minimum Contract

Each capability section must contain:

- Capability ID (for example `CAP-214`)
- Linked issue references
- `operationId` list (authoritative API references)
- Behavioral assertions (deterministic and testable)
- Frontend usage notes (which UI action calls which operation)
- ADR references that constrain behavior
- Contract/provider test IDs

## Duplication Rules (Hard)

Domain guides must not:

- Copy full request/response schemas from OpenAPI
- Copy full endpoint inventory tables from OpenAPI
- Copy global standards sections already defined here (naming, timestamps, pagination, error envelope, correlation ID)
- Restate entire ADR content

Domain guides should:

- Link to OpenAPI and generated API reference for schema detail
- Keep only domain-specific deltas and behavior semantics

## Global API Conventions

The following conventions are global and shared across domains.  
Domain guides should reference this section and only document exceptions/deltas:

- JSON field naming
- Data type and format conventions
- Timestamp conventions
- Pagination conventions
- Error envelope conventions
- Correlation ID conventions

When these conventions are updated, update this document and avoid per-domain duplication.

## OpenAPI Referencing Rules

- Use `operationId` as the stable contract key.
- Include method + path only as a convenience reference.
- Include the module OpenAPI path (`pos-*/openapi.yaml`) for each capability.
- Prefer links to generated API reference artifacts for full request/response details.

## Metadata Requirements

Each domain guide must include machine-readable metadata fields:

- `openapi_source`
- `last_verified_utc`
- `openapi_commit` (or equivalent source revision)
- `contract_status` (`draft` or `stable-for-ui`)

## Enforcement Guidance

Validation should fail when:

- a listed `operationId` is missing from OpenAPI,
- required sections are missing,
- full schema duplication patterns are detected,
- references to ADRs/issues are malformed.

Validation entry point:

- Script: `scripts/validate_backend_contract_guides.py`
- Pilot check: `python3 scripts/validate_backend_contract_guides.py --domain accounting`
- Full sweep: `python3 scripts/validate_backend_contract_guides.py`

---

**Initial version:** 2026-02-24  
**Owner:** Durion architecture and domain contract maintainers
