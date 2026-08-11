## PAUSE STATE — 2026-08-11 (session credit pause; resume from here)

### Where the wave stands

| Slice | Story | Status |
| --- | --- | --- |
| 1 | #1221 skeleton/model/SPI/registry | **COMPLETE** — implemented, test gate green, Code Review **PASS** (2 minor deferrals folded into slice 2; carry-forwards honored) |
| 2 | #1222 profiles/admin API | Implementation + test gate **COMPLETE** (214 module tests green, catalog v42 check green); **Code Review pending** (agent was mid-run at pause) |
| 3 | #1223 client/audit/scheduler | **NOT STARTED** (a Client Coder run was lost to a model switch before writing anything) |
| Close-out | gates + PR | Pending slice 3 |

All slice 1+2 work is committed and pushed: `durion-positivity-backend` branch `cap/317-supplier-foundation`, commit `472de48` (147 files). Base branch is `main`.

### Binding arbitration decisions already made (anvil — do NOT re-decide)
1. **Audit payload encryption**: AES-256-GCM via JPA `AttributeConverter` (`EncryptedPayloadConverter`, bytea columns, envelope = 0x01 version + key-id + 12-byte nonce + ciphertext); key from `pos.supplier.audit.encryption.key` (env-ref, 32-byte base64) + `key-id` (default k1); fail-closed startup in prod/indus/alpha, ephemeral key + WARN in dev/test; decrypt failure → typed `PayloadUnreadableException`. **OPS ESCALATION OPEN**: `SUPPLIER_AUDIT_ENC_KEY` must be provisioned in non-dev secret stores before first deploy.
2. **Test stubs**: MockRestServiceServer for protocol-level; JDK `com.sun.net.httpserver` via a `FaultInjectingHttpServer` fixture for socket-level (connect/read timeout, refused, breaker) — fixture never grows request-verification features.
3. **Packages**: repo convention wins (`internal.entity/repository/controller/dto/service/client` + ADR-0051's `internal.domain/spi/registry/adapter.<family>`); ADR-0051 §1 errata is a separate doc-only durion PR (not this wave).
4. **Scheduler lease**: `supplier_schedule_lease` table; atomic compare-and-claim UPDATE using DB `now()` (never JVM time); lease `max(2×run, 10min)` via `pos.supplier.schedule.lease-duration`; heartbeat every lease/3 extends owner-guarded; stolen lease → abort before next page, checkpoint commits in the same tx as the batch page, owner-guarded; release = set lease_until=now.

### Slice 3 relaunch briefs (sequence: Client Coder → Domain Data Coder → API Surface Coder → Testing → Review)
- **Client Coder**: `internal.client` — `SupplierBaseClient` (per-binding RestClient, correlation reuse/generate + X-Correlation-Id, ADR-0052 §5 classification: pre-send retryable / post-send-ambiguous never-retried / definitive-4xx), 3 auth strategies (Basic+ApiKey w/ apiKeyHeader default `apikey`; OAuth2 client-credentials with per-authConfigId cache + single-flight; bearer) resolving refs call-time, resolved values never logged; resilience4j (GAVs from pos-document-helper/pos-tax) retry pre-send-only + breaker per (vendorProfileId, capability) + health indicator; Micrometer per (vendorProfileId, capability); `ExchangeObserver.onExchange(ExchangeContext)` SPI invoked on EVERY attempt incl. failures, no-op default bean.
- **Domain Data Coder**: `ExchangeAudit` entity + Flyway V3 (incl. `supplier_schedule_lease`), `EncryptedPayloadConverter` per decision 1, capture levels FULL|REDACTED|METADATA_ONLY per binding (column exists on binding entity: `captureLevel`), credential-header redaction, failures recorded, 400-day purge job (`pos.supplier.audit.retention`, delete payloads keep metadata), scheduler per decision 4 wiring binding `scheduleCron`, `AuditActorContext.withActor` for system writes (flush inside scope).
- **API Surface Coder**: audit read API behind `supplier:audit:read` — permission bit **445 already in catalog v42, permissions.yaml already declares it — do NOT bump the catalog again**; add constant to `SupplierPermissions`, warranty `@PreAuthorize` idiom; paging/filtering per warranty read-controller pattern; OpenAPI + regenerate module openapi.yaml.
- Carry-forwards in force: `vendorProfileId` (never `supplierRef`) in audit/event identity; AuthConfigView never gains ref fields; deps with first use; vendor-wire-type escape ArchUnit rule lands with first codec (CAP-318, not this wave).

### Environment recreate (fresh container)
- Clone backend (base `main`), branch `cap/317-supplier-foundation`; durion at the session repo root.
- Java 25: `apt-get update && apt-get install -y openjdk-25-jdk-headless`; `ln -sfn /usr/lib/jvm/java-25-openjdk-amd64 /opt/jdk25`; build `JAVA_HOME=/opt/jdk25 ./mvnw ...` (first build downloads ~15 min; then prefer `-o`).
- pos-archunit must run in-reactor (`-pl pos-archunit -am`), see repo issue #909.
- semgrep registry is proxy-blocked: lint hook with `--config` pointing at a clone of github.com/semgrep/semgrep-rules `java/`.
- `gh` unavailable: PR via GitHub MCP `create_pull_request` (hook documented as primary, MCP as fallback).

### Remaining close-out gates (unchanged from plan)
Full-reactor `JAVA_HOME=/opt/jdk25 ./mvnw -DskipTests=false verify`; pos-archunit in-reactor; `scripts/generate-permissions.sh --sync --check`; `scripts/check-flyway-hygiene.sh`; lint hook pos-supplier + pos-api-gateway; Documentation Agent ledger/status updates; PR base `main`, head `cap/317-supplier-foundation`, title `[CAP:317] pos-supplier foundation: module skeleton, vendor profiles, base client & exchange audit`, body MUST include: CATALOG_VERSION 41→42 fleet-coordinated deploy, SUPPLIER_AUDIT_ENC_KEY ops prerequisite, Angular SDK regeneration follow-up (pos-supplier/openapi.yaml is the input), closes #1221 #1222 #1223.

---

## Slice 2 Code Review verdict (received 2026-08-11, post-pause): **FAIL**

Substance is strong (ADR-0050 model, YAML-authoritative reconciliation, secret hygiene, permission bookkeeping, 214 tests all validated as genuine). It fails on contract-shape items that are expensive to reverse once the Angular SDK is generated. **Slice 2 is NOT accepted; do not open the PR until the blocking queue is cleared.**

### Blocking (must fix before PR)
1. **Route base is unversioned and prefix-doubling** — all four admin controllers map `/supplier/admin/...`; every other module in the repo maps `/v1/{domain}/...`. With gateway `Path=/supplier/**` + `StripPrefix=1` this resolves externally as `/supplier/supplier/admin/profiles`. **Recommended resolution: remap to `/v1/supplier/admin/...`** (CLAUDE.md "follow existing patterns"; pre-production policy = fix, no shim). Mechanical: 4 `@RequestMapping` values + regenerate spec/aggregate/SDK. No further arbitration needed unless a deliberate new fleet convention is intended, which would require an ADR-0011 amendment.
2. **OpenAPI plumbing**: register `pos-supplier: mode: STRICT` in `pos-openapi-validation/src/test/resources/openapi/module-inventory.yaml` (currently the module's spec is validated by nothing) and regenerate `pos-api-gateway/docs/openapi-aggregate.yaml` (stale; no supplier paths indexed).
3. **ADR-0042 response typing**: every 4xx `@ApiResponse` must declare `ApiError` content (spec currently types 404/409 bodies as the success schema), document 401/403, add `@Schema` to `service.model` records and `schema`/`example` to `@Parameter`; then regenerate spec + SDK.
4. **Secret-reference scheme allowlist**: `AuthReferenceRules.isWellFormed` validates `scheme:value` shape but not the scheme, so `MYDOMAIN:hunter2` (a plaintext credential) persists — violates ADR-0050 §4/§6. Enforce an allowlist (`env`, + future store) at admin write time AND in `SupplierYamlBootstrap.validate`; add `"user:hunter2"` cases to `AuthReferenceRulesTest` and `SupplierYamlBootstrapTest`.

### Non-blocking queue (address in-wave)
5. Add `sandboxBaseUrlOverride` + `retryBackoff` to `VendorProfileRequest`/`View` and map them, or document YAML-only ownership in ADR-0050 (today an ADMIN profile can set `sandbox=true` with no way to supply the URL → slice-3 adapters read null).
6. Move the 456-line reconciler out of `internal.config` into `internal.service` (`SupplierYamlReconciler`, thin `ApplicationRunner` remains) and extract shared entity mappers — admin and YAML paths currently duplicate field mapping (the `apiKeyHeader` change already had to be applied twice).
7. Map `SupplierConfigurationException` in `SupplierExceptionHandler` with a code→status table + test (otherwise slice 3 turns `CAPABILITY_NOT_CONFIGURED` into a 500, the leak ADR-0050 §3 forbids); rename `CAPABILITY_NOT_CONFIGURED` → `SUPPLIER_CAPABILITY_NOT_CONFIGURED` for code-prefix consistency.
8. Normalise `DownstreamPermissionCatalog.java:545-547` comment spacing to the generator's `", // 445"` form.
9. Add `pos-supplier/README.md` (admin routes, permissions, full `supplier.profiles` YAML example with `env:` refs, disable-not-delete semantics) and create `durion/domains/positivity/.business-rules/BACKEND_CONTRACT_GUIDE.md` with the CAP-317 entry (story #1222 asks for it; file does not exist).

### Open questions for the user/architecture at resume
- Route convention (finding 1) — confirm remap vs deliberate deviation. **Decides whether the PR can proceed.**
- Is a `vault:`/AWS secret-store resolver in slice-3 scope, and should the scheme allowlist be deployment-configurable?
- Will the exchange-audit table FK to `supplier_profile`? That decides whether ADMIN `deleteProfile` (currently a hard delete of profile + children) must become a disable now — ADR-0050 §6/§7 preserve history, so a slice-3 FK would either break or orphan commercial records.
- Expose `version` on profile views + require If-Match on PUT, or accept last-write-wins on the admin surface (record the decision either way)?

### Additional slice-3 carry-forwards from this review
- `supplier:audit:read` (bit 445) is allocated but enforced nowhere — slice 3 MUST attach it to the audit read endpoints or the permission must be removed (ADR-0025 §4 parity is owed). Payload reads must themselves be audited.
- Any NEW slice-3 permission needs a **second** manual catalog batch (bits 448+, CATALOG_VERSION → 43) across all four artifacts plus `PermissionCodeTest.EXPECTED_PERMISSION_COUNT` and the `SecurityGatewayConfigTest` out-of-range guard — `--sync --check` cannot detect this because the module uses constant-based `@PreAuthorize` (verified: the generator only diffs string-literal scans and is additive-only).
- `sellerPartyId`/`sellerAgencyCode` are bound by `SupplierProfileProperties.Accounts` but never persisted — slice 3 must persist them or drop them from the YAML contract.
