---
title: Customer Relationship Management Backend Contract Guide
domain: crm
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-customer/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-02-24
api_reference_generated: domains/crm/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Customer Relationship Management Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Customer Relationship Management domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-customer/openapi.yaml`
- Generated API reference: `domains/crm/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/crm/.business-rules/AGENT_GUIDE.md`

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` and the relevant capability section.
2. Validate behavior constraints before implementing endpoint changes.
3. Use `operationId` mappings here, then confirm payload details in generated API reference.
4. Ensure tests cover each changed behavioral assertion.

Frontend developer workflow:

1. Start with `Frontend API Lookup` and identify the `operationId` for the UI action.
2. Open generated API reference for exact payload and response details.
3. Implement error handling and headers described in this guide.

## Domain Invariants

- Customer Relationship Management behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-089 | `durion#89` | draft | [CAP] Party Management (Commercial Accounts & Individuals) |
| CAP-090 | `durion#90` | draft | [CAP] Contact Management (Roles, Preferences, and Consent) |
| CAP-091 | `durion#91` | draft | [CAP] Vehicle Registry (VINs, Descriptions, Ownership/Association) |
| CAP-092 | `durion#92` | draft | [CAP] Preferences & Billing Rules |
| CAP-093 | `durion#93` | draft | [CAP] Promotions & Commercial Activity (Lightweight) |
| CAP-094 | `durion#94` | draft | [CAP] Workorder Execution Integration (Bidirectional) |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Deactivate a party relationship | `deactivateRelationship` | DELETE | `/v1/crm/commercial-accounts/{partyId}/relationships/{relationshipId}` | Refer to generated API reference for payload details |
| Delete vehicle | `deleteVehicle` | DELETE | `/v1/crm/{customerId}/vehicles/{vehicleId}` | Refer to generated API reference for payload details |
| Delete a customer | `deleteCustomer` | DELETE | `/v1/crm/{id}` | Refer to generated API reference for payload details |
| Get all customers | `getAllCustomers` | GET | `/v1/crm` | Refer to generated API reference for payload details |
| Get party details | `getParty` | GET | `/v1/crm/accounts/parties/{partyId}` | Refer to generated API reference for payload details |
| Get communication preferences | `getCommunicationPreferences_1` | GET | `/v1/crm/accounts/parties/{partyId}/communicationPreferences` | Refer to generated API reference for payload details |
| Get contacts with roles | `getContactsWithRoles_1` | GET | `/v1/crm/accounts/parties/{partyId}/contacts` | Refer to generated API reference for payload details |
| Get account tier | `getAccountTier` | GET | `/v1/crm/accounts/{accountId}/tier` | Refer to generated API reference for payload details |
| Get contacts for a commercial account | `getContacts` | GET | `/v1/crm/commercial-accounts/{partyId}/contacts` | Refer to generated API reference for payload details |
| Get communication preferences | `getCommunicationPreferences` | GET | `/v1/crm/parties/{partyId}/communicationPreferences` | Refer to generated API reference for payload details |
| Get contacts with roles | `getContactsWithRoles` | GET | `/v1/crm/parties/{partyId}/contacts` | Refer to generated API reference for payload details |
| Search persons | `searchPersons` | GET | `/v1/crm/persons` | Refer to generated API reference for payload details |
| Get a person by ID | `getPerson` | GET | `/v1/crm/persons/{personId}` | Refer to generated API reference for payload details |
| Fetch snapshot by party | `fetchByParty` | GET | `/v1/crm/snapshot/party/{partyId}` | Returns account, contacts, vehicles, and billingRules; refer to generated API reference for payload details |
| Fetch snapshot by vehicle | `fetchByVehicle` | GET | `/v1/crm/snapshot/vehicle/{vehicleId}` | Refer to generated API reference for payload details |
| Get billing rules for party | `getBillingRules` | GET | `/v1/crm/snapshot/party/{partyId}/billing-rules` | Returns BillingRuleRef only; does not require full snapshot load |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-089: [CAP] Party Management (Commercial Accounts & Individuals)

### Capability Metadata

- Capability ID: CAP-089
- Parent Issue: https://github.com/louisburroughs/durion/issues/89
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-customer/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Deactivate a party relationship | `deactivateRelationship` | DELETE | `/v1/crm/commercial-accounts/{partyId}/relationships/{relationshipId}` |
| Delete vehicle | `deleteVehicle` | DELETE | `/v1/crm/{customerId}/vehicles/{vehicleId}` |
| Delete a customer | `deleteCustomer` | DELETE | `/v1/crm/{id}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-customer/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-090: [CAP] Contact Management (Roles, Preferences, and Consent)

### Capability Metadata

- Capability ID: CAP-090
- Parent Issue: https://github.com/louisburroughs/durion/issues/90
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-customer/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get all customers | `getAllCustomers` | GET | `/v1/crm` |
| Get party details | `getParty` | GET | `/v1/crm/accounts/parties/{partyId}` |
| Get communication preferences | `getCommunicationPreferences_1` | GET | `/v1/crm/accounts/parties/{partyId}/communicationPreferences` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-customer/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-091: [CAP] Vehicle Registry (VINs, Descriptions, Ownership/Association)

### Capability Metadata

- Capability ID: CAP-091
- Parent Issue: https://github.com/louisburroughs/durion/issues/91
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-customer/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get contacts with roles | `getContactsWithRoles_1` | GET | `/v1/crm/accounts/parties/{partyId}/contacts` |
| Get account tier | `getAccountTier` | GET | `/v1/crm/accounts/{accountId}/tier` |
| Get contacts for a commercial account | `getContacts` | GET | `/v1/crm/commercial-accounts/{partyId}/contacts` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-customer/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-092: [CAP] Preferences & Billing Rules

### Capability Metadata

- Capability ID: CAP-092
- Parent Issue: https://github.com/louisburroughs/durion/issues/92
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-customer/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get communication preferences | `getCommunicationPreferences` | GET | `/v1/crm/parties/{partyId}/communicationPreferences` |
| Get contacts with roles | `getContactsWithRoles` | GET | `/v1/crm/parties/{partyId}/contacts` |
| Search persons | `searchPersons` | GET | `/v1/crm/persons` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-customer/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-093: [CAP] Promotions & Commercial Activity (Lightweight)

### Capability Metadata

- Capability ID: CAP-093
- Parent Issue: https://github.com/louisburroughs/durion/issues/93
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-customer/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get a person by ID | `getPerson` | GET | `/v1/crm/persons/{personId}` |
| Fetch snapshot by party | `fetchByParty` | GET | `/v1/crm/snapshot/party/{partyId}` |
| Fetch snapshot by vehicle | `fetchByVehicle` | GET | `/v1/crm/snapshot/vehicle/{vehicleId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-customer/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-094: [CAP] Workorder Execution Integration (Bidirectional)

### Capability Metadata

- Capability ID: CAP-094
- Parent Issue: https://github.com/louisburroughs/durion/issues/94
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-customer/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get vehicle for customer | `getVehiclesForCustomer` | GET | `/v1/crm/{customerId}/vehicles/{vehicleId}` |
| Get customer by ID | `getCustomerById` | GET | `/v1/crm/{id}` |
| Create a new customer | `createCustomer` | POST | `/v1/crm` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-customer/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-252: [CAP] Customer Context (CRM Snapshot)

### Capability Metadata

- Capability ID: CAP-252
- Parent Issue: [https://github.com/louisburroughs/durion/issues/252](https://github.com/louisburroughs/durion/issues/252)
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-customer/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Fetch snapshot by party | `fetchByParty` | GET | `/v1/crm/snapshot/party/{partyId}` |
| Fetch snapshot by vehicle | `fetchByVehicle` | GET | `/v1/crm/snapshot/vehicle/{vehicleId}` |
| Get billing rules for party | `getBillingRules` | GET | `/v1/crm/snapshot/party/{partyId}/billing-rules` |

### Behavioral Assertions — Customer Snapshot (Story #4)

| Assertion | Description |
| --- | --- |
| SS-001 | `GET /v1/crm/snapshot/party/{partyId}` returns 200 with `account`, `contacts`, `vehicles`, and `billingRules` sections populated |
| SS-002 | `billingRules.poRequired` is present and accurately reflects the customer's configured billing rule |
| SS-003 | When the underlying billing rules data is missing, defaults are applied: `poRequired=false`, `taxExempt=false`, `paymentTerms="Due on Receipt"`, `creditHold=false`, `autoPayEnabled=false`, `invoiceDeliveryMethod=EMAIL` |
| SS-004 | A second request for the same `partyId` within the cache TTL returns the snapshot from cache; `metadata.source` is `CACHE` |
| SS-005 | `GET /v1/crm/snapshot/vehicle/{vehicleId}` returns 200 with the owning party's snapshot context |
| SS-006 | `GET /v1/crm/snapshot/party/{unknownId}` returns 404 |
| SS-007 | Callers without `PARTY_VIEW` authority receive 403 Forbidden |

### Behavioral Assertions — Billing Rules Surface (Story #3)

| Assertion | Description |
| --- | --- |
| BR-001 | `GET /v1/crm/snapshot/party/{partyId}/billing-rules` returns 200 with `BillingRuleRef` including `poRequired` flag |
| BR-002 | When no billing rules are configured for the party, the endpoint returns defaults (`poRequired=false`, etc.) with all required fields present |
| BR-003 | `GET /v1/crm/snapshot/party/{unknownId}/billing-rules` returns 404 |
| BR-004 | Callers without `PARTY_VIEW` authority on the billing-rules endpoint receive 403 Forbidden |

### Domain Boundaries

- **This service provides billing rules** as a read-only configuration snapshot via `BillingRuleRef`. It does NOT enforce billing rules.
- **Enforcement is the responsibility of downstream services**: Checkout/Order Creation enforces PO requirements; Invoicing enforces payment terms and credit limits; Pricing applies tax exemption.
- Caching: snapshots are cached with a 15-minute TTL. Cache is backed by in-memory store (Caffeine) for development and test; Redis is the preferred production cache.
- Authorization is evaluated at request time even when returning cached data.

### Frontend Usage Notes

- Use `fetchByParty` to load the full CRM snapshot (account, contacts, vehicles, billing rules) during order creation.
- Use `getBillingRules` when only billing configuration is needed (e.g., pre-checkout validation).
- The `billingRules.poRequired` flag indicates whether a PO number must be captured before order submission.
- `metadata.stale: true` indicates the snapshot was served from cache after CRM service became unavailable; surface this as a UI warning.

### ADR Constraints

- ADR-0002: Permission `PARTY_VIEW` is required for all snapshot endpoints.
- ADR-0011, ADR-0014: Auth headers propagated from gateway.
- ADR-0017: 404 when party not found; 403 when unauthorized.
- ADR-0018: Audit actor fields from security context.
- ADR-0022: Stable person identifier in audit events.
- ADR-0024: `retrievedAt` timestamp in snapshot metadata.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-customer/src/test/java/com/positivity/customer/contract/`
- Behavior covered: SS-001..SS-007 and BR-001..BR-004

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-customer/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/crm/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/crm/.business-rules/AGENT_GUIDE.md`
- `domains/crm/.business-rules/DOMAIN_NOTES.md`
- `domains/crm/.business-rules/BACKEND_API_REFERENCE.generated.md`
