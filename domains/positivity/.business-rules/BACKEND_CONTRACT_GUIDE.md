---
title: Positivity (Supplier Integrations) Backend Contract Guide
domain: positivity
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/positivity/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-supplier/openapi.yaml
openapi_commit: dde82cc
last_verified_utc: 2026-08-11T18:00:00Z
last_updated: 2026-08-11
api_reference_generated: none — not yet generated for this domain
traceability:
  capability_manifest_root: docs/capabilities
---

# Positivity (Supplier Integrations) Backend Contract Guide

## Purpose & Scope

Curated contract guide for the supplier-integration domain (module `pos-supplier`, Eureka name
`SUPPLIER`, package `com.positivity.supplier`).

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI for request/response schemas and full endpoint detail.

The domain owns **how** the platform reaches a supplier — connection configuration, credential
references, the outbound transport, and the exchange audit trail. It does **not** own the wire formats:
protocol codecs (EDIWheel A2.5/B/C1/JSON, Michelin S2S) arrive in CAP-318 behind the SPI ports in
`internal.spi`.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-supplier/openapi.yaml`
- Module README (configuration, operations, YAML example): `durion-positivity-backend/pos-supplier/README.md`
- ADR-0050 (vendor profile configuration): `docs/adr/0050-supplier-vendor-profile-configuration.adr.md`
- ADR-0051 (protocol adapter versioning): `docs/adr/0051-supplier-protocol-adapter-versioning.adr.md`
- ADR-0052 (outbound idempotency / duplicate-order prevention):
  `docs/adr/0052-supplier-outbound-idempotency-duplicate-order-prevention.adr.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`

No generated API reference exists for this domain yet. Until one does, treat `pos-supplier/openapi.yaml`
as the schema source of truth.

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` — several are load-bearing in ways that are not visible from a single class.
2. Read the capability section for the behaviour you are changing.
3. Use the `operationId` mappings here, then confirm payloads in `pos-supplier/openapi.yaml`.
4. When you touch a controller, regenerate the spec and **verify the regenerated artifact**, not the
   annotations — springdoc infers a body from the return type when `content` is absent.

Frontend developer workflow:

1. Start with `Frontend API Lookup`, identify the `operationId`.
2. Open `pos-supplier/openapi.yaml` for exact payload and response detail.
3. Note which operations require `supplier:audit:read` — it is not implied by profile admin rights.

Gateway routing: external `/supplier/supplier/admin/...` with `X-API-Version: 1` → `lb://SUPPLIER
/v1/supplier/admin/...` (the first `/supplier` routes to the service and is stripped; the version header
is rewritten into the path). Pre-versioned form: `/supplier/v1/supplier/admin/...`. Same doubled-segment
convention as `pos-warranty` and `pos-inventory`.

## Domain Invariants

1. **Credentials are references, never values.** Every credential field on an auth config holds a
   `scheme:value` reference resolved at call time. The scheme must be backed by a registered resolver —
   today `env:` only — enforced at admin write time *and* at YAML startup, so an unsupported scheme fails
   immediately rather than at first vendor call. A `baseUrl` containing userinfo is rejected: that is a
   plaintext credential (ADR-0050 §4).
2. **Resolved credential values never leave the process.** They are not logged at any level, not carried
   on `ExchangeContext`, not in metrics tags, and not in health details.
3. **`vendorProfileId` is identity; `supplierRef` is a label.** Audit rows and events key on the UUID.
   `supplierRef` is snapshotted alongside it for readability, never as a join key.
4. **Two sources of truth, explicitly recorded.** A profile is `YAML`-managed (reconciled from
   configuration at startup; admin mutation rejected with 409) or `ADMIN`-managed (owned by the API).
5. **Disabling is reversible; deleting is not.** A disabled profile resolves every capability to
   `SUPPLIER_PROFILE_DISABLED`; a disabled binding behaves as absent. `DELETE` hard-cascades profile,
   bindings, auth configs and accounts. Exchange-audit rows deliberately survive — no FK to the profile —
   because the trail of a deleted supplier is exactly what a dispute needs.
6. **A missing capability is a typed outcome, not an error.** ADR-0050 §3: capability endpoints surface
   `CAPABILITY_NOT_CONFIGURED` as a typed status on a 200 response. The handler's 409 mapping is a safety
   net against an escaped exception, not the contract.
7. **Only a pre-send failure may be retried automatically.** ADR-0052 §5. A post-send ambiguity is never
   retried unless the caller declares the request an idempotent batch read; the failure mode is a
   duplicate purchase order.
8. **Every attempt is audited, including failures and each retry.** The audit writer is an
   `ExchangeObserver`, so the transport never depends on a repository.
9. **Payload reads are themselves audited, as a precondition.** ADR-0050 §7. The access record is written
   in the read's own transaction with no catch: if it cannot be written, the read returns nothing.
10. **Payload capture is bounded by the binding's capture level, enforced in the schema.**
    `METADATA_ONLY` carrying a payload is rejected by a database CHECK constraint, not merely by code.

## Capability Index

| Capability | Stories | Status |
| --- | --- | --- |
| CAP-317 — pos-supplier foundation | #1221 (skeleton/model/SPI/registry), #1222 (vendor profiles + admin API), #1223 (base client, exchange audit, scheduler) | Implemented, reviewed, not yet merged |
| CAP-318 — protocol codecs | not yet authored | Not started |

## Frontend API Lookup

All paths are service-relative (`/v1/...`); prefix with `/supplier` at the gateway.

| UI action | Method & path | operationId | Permission |
| --- | --- | --- | --- |
| List configured suppliers | `GET /v1/supplier/admin/profiles` | `listProfiles` | `supplier:profile:read` |
| View one supplier | `GET …/profiles/{vendorProfileId}` | `getProfile` | `supplier:profile:read` |
| Onboard a supplier | `POST …/profiles` | `createProfile` | `supplier:profile:write` |
| Edit a supplier | `PUT …/profiles/{vendorProfileId}` | `updateProfile` | `supplier:profile:write` |
| Remove a supplier | `DELETE …/profiles/{vendorProfileId}` | `deleteProfile` | `supplier:profile:write` |
| List auth configs | `GET …/profiles/{id}/auth-configs` | `listAuthConfigs` | `supplier:profile:read` |
| Add auth config | `POST …/profiles/{id}/auth-configs` | `createAuthConfig` | `supplier:profile:write` |
| Edit auth config | `PUT …/profiles/{id}/auth-configs/{authConfigId}` | `updateAuthConfig` | `supplier:profile:write` |
| Remove auth config | `DELETE …/profiles/{id}/auth-configs/{authConfigId}` | `deleteAuthConfig` | `supplier:profile:write` |
| List commercial accounts | `GET …/profiles/{id}/accounts` | `listAccounts` | `supplier:profile:read` |
| Add account | `POST …/profiles/{id}/accounts` | `createAccount` | `supplier:profile:write` |
| Edit account | `PUT …/profiles/{id}/accounts/{accountId}` | `updateAccount` | `supplier:profile:write` |
| Remove account | `DELETE …/profiles/{id}/accounts/{accountId}` | `deleteAccount` | `supplier:profile:write` |
| List capability bindings | `GET …/profiles/{id}/bindings` | `listBindings` | `supplier:profile:read` |
| Add binding | `POST …/profiles/{id}/bindings` | `createBinding` | `supplier:profile:write` |
| Edit binding | `PUT …/profiles/{id}/bindings/{bindingId}` | `updateBinding` | `supplier:profile:write` |
| Remove binding | `DELETE …/profiles/{id}/bindings/{bindingId}` | `deleteBinding` | `supplier:profile:write` |
| Browse exchanges in a window | `GET …/audit/exchanges` | `listExchanges` | `supplier:audit:read` |
| Trace one call incl. retries | `GET …/audit/exchanges/by-correlation/{correlationId}` | `traceCorrelation` | `supplier:audit:read` |
| One exchange's metadata | `GET …/audit/exchanges/{exchangeAuditId}` | `getExchange` | `supplier:audit:read` |
| Read stored payload content | `GET …/audit/exchanges/{exchangeAuditId}/payload` | `readPayload` | `supplier:audit:read` |
| Who read this payload | `GET …/audit/exchanges/{exchangeAuditId}/accesses` | `listAccesses` | `supplier:audit:read` |

UI notes:

- Auth-config responses **never** return credential reference fields. A form editing an auth config
  writes references; it cannot read back what is currently set. Present this as intentional.
- `listExchanges` takes a half-open `[from, to)` window; `from == to` is a typed 400, and `size` above
  200 is a 400. Tiling adjacent windows will not double-count.
- At `METADATA_ONLY`, `endpointUri` in a response has no query string. This is redaction at capture time,
  not truncation at read time — the original was never stored.
- `readPayload` writes an access record. Do not call it to pre-populate a list view or on hover; each
  call is a recorded disclosure.

## Permission Matrix

| Permission | Bit | Grants |
| --- | --- | --- |
| `supplier:profile:read` | 446 | View profiles, auth configs (without references), accounts, bindings |
| `supplier:profile:write` | 447 | Create/edit/delete profiles, auth configs, accounts, bindings |
| `supplier:audit:read` | 445 | All five audit operations, including decrypted payload content |

`supplier:audit:read` is deliberately **not** implied by profile administration: it exposes commercial
documents exchanged with vendors, which is a strictly wider disclosure than knowing how a connection is
configured. A caller holding both profile permissions and not `supplier:audit:read` receives 403 on every
audit operation, and a test asserts exactly that combination.

`CATALOG_VERSION` is 42 and covers bits 445–447. Because this module uses constant-based `@PreAuthorize`,
`scripts/generate-permissions.sh --sync --check` structurally cannot detect drift here — the manual
catalog entries are the only guarantee.

## Capability Sections

### CAP-317 — pos-supplier foundation

#### Capability Metadata

| Field | Value |
| --- | --- |
| Stories | #1221, #1222, #1223 |
| Module | `pos-supplier` |
| Branch | `cap/317-supplier-foundation` |
| Migrations | V1 baseline, V2 vendor profiles, V3 exchange audit + schedule lease, V4 audit access, V5 widen `protocol_version` |
| New permissions | none beyond bits 445–447 already in catalog v42 |

#### Scope & Intent

Configure a vendor connection, reach it safely, and keep an evidentiary record of the exchange. Three
slices: the module skeleton with the SPI ports and adapter registry; vendor profiles with the admin API;
the outbound base client with exchange audit, retention purge, scheduler lease and the audit read API.

#### API Operation References (OpenAPI Source of Truth)

22 operations, all under `/v1/supplier/admin`. See `Frontend API Lookup` for the full mapping; schemas
live in `pos-supplier/openapi.yaml` at commit `dde82cc`.

#### Behavioral Assertions

Profile administration:

1. Creating a profile with a credential reference whose scheme is unsupported is rejected at write time.
2. Mutating a `YAML`-managed profile through the admin API is rejected; configuration wins.
3. Renaming an auth config still referenced by a binding is a conflict, not a cascade.
4. Deleting a profile cascades to its bindings, auth configs and accounts, and leaves audit rows intact.
5. Changing or deleting an auth config invalidates any cached OAuth2 token for it, so a rotated secret
   takes effect immediately rather than at token expiry.
6. A `baseUrl` containing userinfo is rejected with a message that names the field and does not echo it.

Outbound transport:

7. A binding resolves to `(family, version, baseUrl, path, auth)`; an unregistered version resolves to
   `CAPABILITY_NOT_CONFIGURED` rather than failing loudly. **A version is not validated on write.**
8. Credentials are applied per attempt at call time and never logged.
9. Connect and read timeouts are distinguished by exception type, not message text.
10. Only `PRE_SEND_FAILURE` is retried; a token-endpoint transport failure is pre-send (nothing reached
    the vendor's business endpoint) and therefore retryable, while a vendor 401/403 on the token leg is a
    definitive credential rejection.
11. A breaker opens on transport failures only; a 4xx or configuration error does not count toward it.
12. The health indicator never reports DOWN — a vendor outage must not restart this service.

Exchange audit:

13. One row per attempt, including failures and each retry, sharing a correlation id.
14. Payload columns are encrypted; the envelope binds its header as AAD, so a rewritten key id fails
    authentication rather than decrypting.
15. A payload sealed with a rotated-out key yields a typed unreadable-payload error; the metadata row
    still lists and still reads by id.
16. `METADATA_ONLY` stores no bodies and no URI query string; the database rejects a violation.
17. An unknown or missing binding falls back to `REDACTED`, never `FULL`.
18. The retention purge nulls payloads, stamps `payloads_purged_at`, and keeps metadata permanently.
19. Reading payload content writes an access record in the same transaction; if that write fails, no
    content is returned.
20. A scheduler lease is claimed by an atomic database decision; a stolen lease leaves the old owner
    unable to heartbeat, checkpoint or release, and its page rolls back with its checkpoint.

#### Status Code Semantics (ADR-0017)

| Code | Status | Meaning |
| --- | --- | --- |
| `SUPPLIER_UNKNOWN` | 404 | No profile for that alias or id |
| `SUPPLIER_PROFILE_DISABLED` | 409 | Profile exists, disabled |
| `SUPPLIER_CAPABILITY_NOT_CONFIGURED` | 409 | Safety net only — capability endpoints return a typed 200 status |
| `SUPPLIER_MISSING_BILLING_ACCOUNT` | 409 | No billing account for an account-level operation |
| `SUPPLIER_MISSING_DELIVERY_MAPPING` | 409 | Requested location has no delivery mapping |
| `SUPPLIER_AUTH_CONFIG_MISSING` | 409 | Binding names a nonexistent auth config |
| `SUPPLIER_AUTH_CREDENTIALS_REJECTED` | 409 | Vendor refused the resolved credentials (401/403 on the token leg) |
| `SUPPLIER_PROFILE_YAML_MANAGED` | 409 | Admin mutation of a configuration-owned profile |
| `SUPPLIER_URL_CONTAINS_CREDENTIALS` | 400 | `baseUrl` carries userinfo |
| `SUPPLIER_SECRET_REF_MALFORMED` | 400 | Reference is not a supported `scheme:value` |
| `SUPPLIER_AUTH_TOKEN_RESPONSE_INVALID` | 500 | Vendor returned 2xx with no usable token — vendor contract violation |
| `SUPPLIER_SECRET_*`, `SUPPLIER_YAML_BOOTSTRAP_INVALID` | 500 | Deployment defect; generic client message, detail logged server-side |
| `SUPPLIER_AUDIT_PAYLOAD_UNKNOWN_KEY_ID` / `_AUTHENTICATION_FAILED` / `_MALFORMED_ENVELOPE` | 500 | Stored payload unreadable; names no key id or variable |

Deployment defects return 500 with a deliberately generic message: their detail names environment
variables, and echoing that to a caller is information disclosure (ADR-0050 §3).

#### Audit and Security Rules

- Method security is deny-by-default `@PreAuthorize` on every operation (ADR-0040); the gateway supplies
  identity via `X-Authorities` / `X-User`.
- 401 responses carry **no body** (the shared entry point is bodiless); 403 responses carry `ApiError`.
- Encryption fails closed: the service will not start without a key unless *every* active profile is
  `dev` or `test`. `prod,dev` requires a key.
- A retired encryption key must remain in `previous-keys` for the whole retention window.
- Payload-body redaction is name-based with a compiled-in field set. It cannot redact a credential
  carried positionally (e.g. an EDIFACT `UNB` password), and per-binding data classification is not yet
  implemented — see Open Questions.

#### ADR Constraints

| ADR | Constraint |
| --- | --- |
| ADR-0050 §2 | Profile shape, sandbox overlay, per-profile timeouts |
| ADR-0050 §3 | No error leaks; capability absence is a typed status |
| ADR-0050 §4/§6 | Credential references only; plaintext never persists |
| ADR-0050 §7 | Retention 400 days, encryption at rest, minimization, capture levels, audited access |
| ADR-0051 | Protocol adapter versioning; `internal.domain` / `internal.spi` / `internal.registry` / `internal.adapter.<family>` |
| ADR-0052 §5 | Pre-send vs post-send-ambiguous vs definitive classification (duplicate-order prevention) |
| ADR-0011 | `/v1/{domain}` route convention |
| ADR-0025 §4 | Declared permissions must be enforced |
| ADR-0042 | 4xx typed `ApiError`; parameters carry schema and example |

#### Events & Dependencies

22 event types are registered, all admin-surface: `SUPPLIER_PROFILE_*`, `SUPPLIER_AUTHCONFIG_*`,
`SUPPLIER_ACCOUNT_*`, `SUPPLIER_BINDING_*` (list/get/create/update/delete), plus
`SUPPLIER_AUDIT_EXCHANGE_LIST`, `SUPPLIER_AUDIT_EXCHANGE_GET`, `SUPPLIER_AUDIT_EXCHANGE_TRACE`,
`SUPPLIER_AUDIT_ACCESS_LIST` and `SUPPLIER_AUDIT_PAYLOAD_READ`. `SUPPLIER_AUDIT_PAYLOAD_READ` is
deliberately budgeted as a write: it inserts an access row.

Inbound dependencies: `pos-location` (delivery-location UUIDs on commercial accounts), `pos-security-service`
(permission registration), `pos-event-receiver` (event type registration).

Outbound: none within the platform. Vendor endpoints are external third parties, so ADR-0014's
server-to-server rules do not apply to them.

Consumers of this domain do not exist yet. CAP-318 codecs will be the first, through the SPI ports.

#### Contract Test Traceability

| Assertion group | Test |
| --- | --- |
| Admin CRUD, error envelopes, permission separation | `SupplierAdminControllersWebMvcTest` |
| Secret-scheme allowlist | `AuthReferenceRulesTest`, `SecretSchemeRegistryTest`, `SupplierYamlBootstrapTest` |
| Classification and retry safety | `SupplierBaseClientTest` (asserts requests that reached the wire) |
| OAuth2 cache keying, single-flight, invalidation | `OAuth2ClientCredentialsAuthStrategyTest`, `SupplierAuthStrategyTest` |
| Encryption envelope, tampering, rotation, key policy | `AuditPayloadCipherTest` |
| Capture levels, redaction, URI redaction | `PayloadRedactorTest` |
| Encryption at rest (asserts raw column bytes) | `ExchangeAuditObserverTest`, `ExchangeAuditWriterTest` |
| Transaction boundaries | `ExchangeAuditWriterTest`, `SupplierExchangeAuditPersistenceTest` |
| Lease contention, stolen lease, checkpoint | `SupplierScheduleLeaseRepositoryTest`, `SupplierScheduleCoordinatorTest` |
| Enum/constraint and column-width parity | `SupplierContractKeyParityTest`, `ExchangeAuditColumnWidthParityTest` |

Guarantees are additionally mutation-checked via `.github/hooks/mutation-check-hook.sh`: the mutation must
be proven applied and the test must fail, or the guarantee is reported `UNDEFENDED`.

#### Open Questions / Non-Goals

- **Per-binding, data-classification-driven body-field redaction** (ADR-0050 §7) is not implemented.
  Redaction is credential-name-based and compiled in. Owed by CAP-318, when codecs define real document
  shapes. The ADR was deliberately **not** amended to match the implementation.
- **Positional formats are not redactable by name.** `METADATA_ONLY` is the only level that guarantees a
  positional document retains nothing.
- **Inbound `X-Correlation-Id` reuse** is not wired; only outbound correlation is scoped.
- **Binding `version` is not validated** against the adapter registry, so a typo persists and silently
  resolves every call to `CAPABILITY_NOT_CONFIGURED`.
- **Optimistic locking is deliberately absent** on the admin surface: last-write-wins is accepted and
  recorded. Two operators editing one profile is last-write-wins.
- **Non-goals:** wire formats and codecs (CAP-318), order reconciliation for post-send ambiguity
  (CAP-320), secret-store resolvers beyond `env:`.

## Events & Cross-Domain Dependencies

This domain publishes only admin/audit-surface events and consumes none. It is a leaf: no other backend
domain depends on it yet. `pos-location` UUIDs appear on commercial accounts as opaque references — there
is no synchronous call to that service.

## Verification Metadata

| Gate | Result at `dde82cc` |
| --- | --- |
| `-pl pos-supplier -am -DskipTests=false verify` | SUCCESS, 546 module tests |
| `-pl pos-archunit -am test` | SUCCESS (must run under `test`; `verify` repackages and hides classes — repo issue #909) |
| `spotless:check` | clean |
| Touched-file lint | 0 findings |
| `generate-permissions.sh --sync --check` | exit 0, `CATALOG_VERSION` 42 unchanged |
| `check-flyway-hygiene.sh` | passed, 25 modules |
| Spec + 26-module aggregate | regenerated, aggregate byte-identical |

Known gaps in verification, recorded rather than resolved:

- Full-reactor `verify` is red for three causes unrelated to this domain: pos-accounting uses
  PostgreSQL-only SQL under H2 (proven pre-existing by building the base commit in a worktree),
  `FlywayMigrationIT` in four modules needs a Docker daemon, and pos-archunit fails under `verify` by
  design. Reactor `test` is green across 41 modules.
- **V5 has never executed against real PostgreSQL** — only H2 in PostgreSQL mode. Widening a `varchar` is
  metadata-only and has repo precedent (`pos-security-service/V19`), but the first real execution will be
  in a deployed environment.

## References

- `durion-positivity-backend/pos-supplier/README.md` — configuration, YAML example, operational detail
- `durion-positivity-backend/pos-supplier/openapi.yaml` — schema source of truth
- `docs/adr/0050-supplier-vendor-profile-configuration.adr.md`
- `docs/adr/0051-supplier-protocol-adapter-versioning.adr.md`
- `docs/adr/0052-supplier-outbound-idempotency-duplicate-order-prevention.adr.md`
- `docs/capabilities/CAP-317/WAVE_PAUSE_STATE.md` — decision record and follow-up queue
- `domains/positivity/DOMAIN_NOTES.md`, `domains/positivity/AGENT_GUIDE.md`
