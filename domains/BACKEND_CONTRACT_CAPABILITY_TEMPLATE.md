---
title: Backend Contract Capability Template
domain: shared
doc_type: backend_contract_template
template_type: capability_section
owner_repo: louisburroughs/durion
path: domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md
last_updated: 2026-02-24
---

# Backend Contract Capability Template

Use this template to add a capability section to a domain backend contract guide.

Reference standard:
- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`

Rules:
- OpenAPI is the source of truth for schemas/endpoints/enums/status codes.
- Do not copy full request/response schemas into capability sections.
- Use `operationId` as the primary API reference key.

~~~md
## CAP-XXX: <Capability Name>

### Capability Metadata
- Capability ID: CAP-XXX
- Capability Status: draft | stable-for-ui
- Domain: <domain>
- Parent Issue: <repo#issue>
- Backend Issues: <repo#issue>, <repo#issue>
- Frontend Issues: <repo#issue> (optional)
- OpenAPI Source: `durion-positivity-backend/pos-<module>/openapi.yaml`
- Last Verified OpenAPI Commit: `<sha>`

### Scope & Intent
Short description of business scope and implementation intent.

### API Operation References (OpenAPI Source of Truth)
| UI Task / Backend Use Case | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| <e.g., Load replenishment recommendations> | `<operationId>` | GET | `/v1/...` | <filters, pagination, role notes> |
| <e.g., Confirm replenishment> | `<operationId>` | POST | `/v1/...` | <idempotency/event note> |

### Behavioral Assertions
- <Deterministic rule 1>
- <Deterministic rule 2>
- <Validation/error rule>

### Frontend Usage Notes
- UI component/screen: `<component or route>`
- Primary call sequence: `<operationId A> -> <operationId B>`
- Required headers: `X-Correlation-Id`, `Authorization`, `Idempotency-Key` (as applicable)
- Permissions/roles: `<role/authority>`
- Common failure handling expectations: `<code> -> <UI behavior>`

### ADR Constraints
- ADR: `<ADR-ID>` - <constraint summary>
- ADR: `<ADR-ID>` - <constraint summary>

### Events & Dependencies
- Emits: `<EVENT_TYPE>`
- Consumes: `<EVENT_TYPE>`
- Cross-domain dependencies: `<service/event dependency>`

### Contract Test Traceability
- Provider test class(es): `<module>/src/test/...`
- Contract test IDs:
  - `<CP-XXX-100>`
  - `<VE-XXX-100>`
  - `<LC-XXX-100>`
- Behavioral assertion coverage map:
  - `<Assertion 1> -> <test ID(s)>`
  - `<Assertion 2> -> <test ID(s)>`

### Open Questions / Non-Goals
- Open questions:
  - <question or None>
- Non-goals:
  - <out of scope behavior>
~~~
