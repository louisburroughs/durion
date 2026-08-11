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
