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

   **AMENDMENT (2026-08-11, implemented in `a5caf79`, coordinator-endorsed): key rotation is part of this decision, not extra scope.** Decrypt-only keys are supplied as `pos.supplier.audit.encryption.previous-keys` in `keyId:base64` form; the active key seals new payloads while prior keys stay readable. This *completes* the decision's intent rather than exceeding it: the envelope already carries a key id, which is meaningless without a rotation path, and the 400-day retention window of ADR-0050 §7 outlives any sane key lifetime — so without it, rotating a key would silently orphan every stored payload for the rest of the window. An unconfigured key id is reported as `SUPPLIER_AUDIT_PAYLOAD_UNKNOWN_KEY`, distinct from an authentication failure, and its message names `previous-keys` as the remedy.

   **Suppression precedent:** the two GCM sites carry line-scoped `// nosemgrep` for semgrep's audit-category `gcm-detection`. pos-supplier is the first module in the repo doing crypto, so this is the precedent later modules will copy — each suppression states the evidence (the per-call `SecureRandom` nonce, the absence of any derived-nonce path, and `AuditPayloadCipherTest.NonceDiscipline.neverReusesANonceForTheSameKeyAndPayload` by name) and is deliberately never file- or class-wide.
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

---

## UPDATE — slice 2 Code Review verdict: **FAIL** (landed after pause was recorded)

Substance is sound (ADR-0050 model, YAML-authoritative reconciliation, secret hygiene, catalog bookkeeping, 214 tests all verified good; the manual bits 445-447 were confirmed mutually consistent across all four artifacts, and the reviewer confirmed `--sync --check` structurally *cannot* detect drift for constant-based `@PreAuthorize` — so the manual entries are the only guarantee and they hold). It fails on contract-shape items that are expensive to reverse after SDK generation.

### Blocking fix queue (must clear before PR)
1. **[high] Route base unversioned + prefix-doubling.** All four admin controllers map `/supplier/admin/...`; every other module maps `/v1/{domain}/...`. With `Path=/supplier/**` + `StripPrefix=1` the external path becomes `/supplier/supplier/admin/profiles`. Remap to `/v1/supplier/admin/...`, regenerate `pos-supplier/openapi.yaml` **and** `pos-api-gateway/docs/openapi-aggregate.yaml`. **Do this before any SDK generation.**
2. **[med] OpenAPI CI + discovery gaps.** Register `pos-supplier: mode: STRICT` in `pos-openapi-validation/src/test/resources/openapi/module-inventory.yaml` (currently the module's spec is validated by nothing); regenerate the gateway aggregate.
3. **[med] ADR-0042 annotation depth.** 4xx `@ApiResponse`s are typed as the success schema (e.g. 404 → `VendorProfileView`); add `@Content(schema = @Schema(implementation = ApiError.class))`, document 401/403, add `@Schema` to `service.model` records, `@Parameter` schema/example — then regenerate spec + SDK.
4. **[med] Secret-reference scheme allowlist.** `AuthReferenceRules.isWellFormed` validates `scheme:value` shape but not the scheme, so `MYDOMAIN:hunter2` (a plaintext credential) persists and only fails at call time — violates ADR-0050 §4/§6. Validate against supported resolver schemes at write time and in `SupplierYamlBootstrap.validate`; add `"user:hunter2"` tests. Consider a scheme-dispatching composite resolver now (a second `SecretReferenceResolver` bean would make by-type injection ambiguous).

### Non-blocking (fold in during slice 3 or a cleanup pass)
5. Add `sandboxBaseUrlOverride` + `retryBackoff` to `VendorProfileRequest`/`View` (ADMIN profiles currently cannot express the ADR-0050 §2 sandbox overlay — slice-3 adapters would read null), or document YAML-only ownership in ADR-0050.
6. Move the 456-line reconciler out of `internal.config` into `internal.service` (`SupplierYamlReconciler`) and extract shared entity mappers — admin and YAML paths currently duplicate field mapping (the `apiKeyHeader` change already had to be applied twice).
7. Map `SupplierConfigurationException` in `SupplierExceptionHandler` with a code→status table + test (**required before slice 3 exposes the resolver over HTTP**, else `CAPABILITY_NOT_CONFIGURED` becomes a 500); rename `CAPABILITY_NOT_CONFIGURED` → `SUPPLIER_CAPABILITY_NOT_CONFIGURED` for prefix consistency.
8. Normalise `DownstreamPermissionCatalog.java:545-547` comment spacing to the generator's `", // 445"` form.
9. Add `pos-supplier/README.md` (admin routes, permissions, full `supplier.profiles` YAML example with `env:` refs, disable-not-delete semantics) and create `durion/domains/positivity/.business-rules/BACKEND_CONTRACT_GUIDE.md` (does not exist).
10. Consider exposing `version` on profile views + If-Match on PUT (today two operators editing one profile is last-write-wins), or record the decision.

### Open decisions needed (were the reviewer's questions)
- **Route convention**: confirm the `/v1/supplier/...` remap (recommended — repo-wide convention) vs. an ADR-0011 amendment justifying a new convention. **Decides fix #1.**
- **Secret-store resolver** (`vault:`/AWS) in slice 3 scope? Should the write-time scheme allowlist be per-deployment configurable?
- **Exchange-audit FK to `supplier_profile`?** If yes, admin `deleteProfile` (currently a hard delete of profile + children) must become a disable, or the audit must snapshot `supplierRef` — ADR-0050 §6/§7 tension. **Decide before slice 3's Flyway V3.**

### Additional slice-3 carry-forwards from this review
- `supplier:audit:read` (bit 445) **must** be enforced by the audit read endpoints or removed (ADR-0025 §4 parity is owed); payload reads must themselves be audited (ADR-0050 §7).
- Any *new* slice-3 permission needs a **second** manual catalog batch (bits 448+), `CATALOG_VERSION` → 43, and updates to `PermissionCodeTest.EXPECTED_PERMISSION_COUNT` + `SecurityGatewayConfigTest` out-of-range guard — `--sync --check` will not catch it.
- `sellerPartyId`/`sellerAgencyCode` are bound by `SupplierProfileProperties.Accounts` but never persisted — slice 3 must persist them or drop them from the YAML contract.

---

## UPDATE — blocking fix #1 cleared (2026-08-11): routes remapped to `/v1/supplier`

Backend `cap/317-supplier-foundation` @ `5449315`.

**Done**
- All four admin controllers now map `/v1/supplier/admin/...` (`SupplierProfileAdminController`, `SupplierAccountAdminController`, `SupplierAuthConfigAdminController`, `SupplierEndpointBindingAdminController`) — repo convention `/v1/{domain}/...` restored, prefix-doubling gone. External path is now `/supplier/v1/supplier/admin/profiles`, matching the shape of every other module (e.g. `/customer/v1/customers`).
- `pos-supplier/openapi.yaml` regenerated from the running app via the `openapi` Maven profile (springdoc + `scripts/sanitize-openapi.py`). Diff is exactly the eight path keys; no schema churn.
- `pos-api-gateway/docs/openapi-aggregate.yaml` regenerated with the full 26-module list (`scripts/generate-openapi.sh`'s aggregation step). Diff is additive only: the eight supplier paths, the `pos-supplier` tag, and the `x-aggregated-modules` entry — no drift in the other 25 modules. This also clears the aggregate half of blocking fix #2.
- `SupplierAdminControllersWebMvcTest.BASE` updated; `mvn -pl pos-supplier test` → **214 tests, 0 failures** (same count as before the remap).

**Decided**
- The route-convention open decision is resolved in favour of the `/v1/supplier/...` remap. No ADR-0011 amendment needed.

**Deliberately not touched (still open)**
- Gateway `application.yml` needs no change: the `/supplier` prefix it strips with `StripPrefix=1` is the service prefix, not the API version.
- Blocking #2 remainder: `pos-supplier: mode: STRICT` is still unregistered in `pos-openapi-validation/.../module-inventory.yaml`.
- Blocking #3 (ADR-0042 response typing) and #4 (secret-scheme allowlist) are untouched — both will require another spec regeneration afterwards.
- No Angular SDK generated yet, so nothing downstream to re-sync from this remap.

**Noticed, pre-existing**
- `mvn -pl pos-supplier spotless:check` fails on two files that predate this change and were not part of it: `ArchitectureTest.java` (line-wrap in `resideInAnyPackage`) and `ServiceModelInvariantsTest.java` (lambda wrap in `rejectsBlankAccountNumber`). `mvn -pl pos-supplier spotless:apply` fixes both. Add to the non-blocking queue.

---

## UPDATE — slice-2 fix wave complete (2026-08-11): all four blocking items cleared

Backend `cap/317-supplier-foundation` @ `9e61d81` (pushed). Five commits on top of `5449315`.

### Blocking queue — CLEARED

| # | Item | Commit | Evidence |
| --- | --- | --- | --- |
| 1 | Route remap to `/v1/supplier` | `5449315` (previous session) | 214 tests green |
| 2 | `pos-supplier: mode: STRICT` in `module-inventory.yaml` | `a5228d1` | 43 validation tests green; mutation-probed |
| 3 | ADR-0042 annotation depth | `a5228d1` | 17/17 ops: zero 4xx untyped, zero missing 401/403 |
| 4 | Secret-reference scheme allowlist | `26ed6a6` | AuthReferenceRulesTest 40 → 86 |

### Non-blocking queue

| # | Item | Status |
| --- | --- | --- |
| 5 | `sandboxBaseUrlOverride` + `retryBackoff` on profile DTOs | **DONE** (`a5228d1`) — implemented, not deferred to an ADR note |
| 6 | Move the 456-line reconciler to `internal.service` | **DEFERRED to slice 3, bound** (see decision below) |
| 7 | Map `SupplierConfigurationException`; rename the code | **DONE** (`d1cfb1d`) |
| 8 | `DownstreamPermissionCatalog` comment spacing | **DONE** (`9e61d81`) — review's premise corrected, see below |
| 9 | `pos-supplier/README.md` + `BACKEND_CONTRACT_GUIDE.md` | Out of scope for this wave; close-out |
| 10 | Optimistic locking / `version` on views | **CLOSED as decided** — last-write-wins accepted |

### What landed, in detail

- **`26ed6a6` secret-scheme allowlist.** `SecretReferenceResolver` now declares `scheme()`; new `SecretSchemeRegistry` collects the resolver beans, dispatches by scheme, and *is* the allowlist. Today `env:` alone. Not deployment-configurable by design. The registry is a distinct type from `SecretReferenceResolver`, so a second resolver never makes by-type injection ambiguous; duplicate/blank scheme claims fail startup. Enforced at admin write time **and** in `SupplierYamlBootstrap.validate`. Rejection messages name the scheme and allowlist but never echo the value.
- **`d1cfb1d` configuration-exception mapping.** Code → status table: `SUPPLIER_UNKNOWN` 404; `PROFILE_DISABLED` / `CAPABILITY_NOT_CONFIGURED` / `MISSING_BILLING_ACCOUNT` / `MISSING_DELIVERY_MAPPING` / `AUTH_CONFIG_MISSING` 409 (ADR-0017 §2 prefers 409 to 422 for state collisions); the four secret/bootstrap codes 500 with a **generic** message, detail logged server-side only, because those messages name deployment env-var names. `CAPABILITY_NOT_CONFIGURED` value → `SUPPLIER_CAPABILITY_NOT_CONFIGURED`. The identically named `service.model` outcome-enum constants were **not** renamed — different namespace (successful-response status, not `ApiError.code`); renaming them would break the published contract.
- **`a5228d1` contract pass** (single spec regeneration for #2/#3/#5). All 4xx now `@Content(schema = ApiError)`; 401/403 on every operation; class- and component-level `@Schema` on all 8 admin records; `schema`/`example` on every `@Parameter`. Success responses keep springdoc inference (already correct). `service.model.RetryBackoff` mirror added; `SupplierContractKeyParityTest` now pins **all five** contract/internal enum mirrors — they convert via `valueOf(name())`, so a one-sided constant was previously only a runtime failure.

### Decisions recorded this wave

1. **[#6 reconciler refactor] DEFERRED to slice 3, as a bound precondition — not optional.** Evidence: all **33 of 33** entity setters are used by *both* `SupplierYamlBootstrap` and `SupplierProfileAdminServiceImpl` — the mapping surface is 100 % duplicated, none unique to either path. The tax has now been paid twice in consecutive changes (`apiKeyHeader` in slice 2, `sandboxBaseUrlOverride`/`retryBackoff` in this wave). *Why not now:* the refactor is **not** mechanical — the two paths map to the same fields from *different source types* (YAML `ProfileSpec`/`AuthSpec`/`BindingSpec` vs contract `VendorProfileRequest`/`AuthConfigRequest`/…), so no shared mapper is directly extractable. The real design is to convert YAML specs into the contract request records and route both paths through one request→entity mapper — extending the pattern the bootstrap **already** uses when it builds an `AuthConfigRequest` to reuse `AuthReferenceRules`. That deserves design attention, and it carries semantic subtleties (YAML `enabled` null→true, sandbox nesting, `sourceOfTruth`, disable-not-delete). *Binding mandate:* do it as the **first step** of slice 3's `sellerPartyId`/`sellerAgencyCode` persistence work, which must touch account mapping anyway and would otherwise pay the duplication tax a third time. **The `internal.config` → `internal.service` package move + rename is separately REJECTED** as presentation-only churn: it forces import rewrites across tests and delivers no behavioural or duplication benefit. No ArchUnit rule requires it (pos-archunit and the module's `ArchitectureTest` are green with the reconciler where it is).
2. **[#8] The review's stated premise was wrong and is corrected.** The target `", // 445"` form is **not** what the generator emits: `generate-permissions.py` pads to `max(1, 45 - len(perm))` — 26 spaces for `supplier:audit:read`. One space is what **palantir-java-format/spotless** produces; spotless collapses trailing-comment padding. That is why the generator-shaped crm batch (bits 438–442) is already one-space in the file. Verified by running `spotless:check` before/after: the supplier lines moved from rewritten to unchanged context.
3. **Pre-existing defect discovered, deliberately NOT fixed:** bits 443–444 (`pos-people-contact`) carry the same wide padding and are still rewritten by spotless. That dirt is present at base `da21c33`, so `pos-security-common` `spotless:check` was **already failing before CAP-317 touched the file**. Out of this wave's scope, matching the ledger's existing decision not to repair pos-marketing's archunit gaps here. Note spotless is bound to **no** lifecycle phase and **no** CI workflow runs it — it is hygiene, not a gate.
4. **Pre-existing gap discovered:** `module-inventory.yaml` is missing `pos-marketing`, `pos-people-contact` and `pos-tax` as well (22 entries vs 26 spec-producing modules). Only `pos-supplier` was added, per scope.

### Gate results (all at `9e61d81`)

| Gate | Result |
| --- | --- |
| `./mvnw -pl pos-supplier -am -DskipTests=false verify` | **BUILD SUCCESS** — pos-supplier 287 tests, 0 failures (214 → 287) |
| `./mvnw -pl pos-archunit -am test` | **BUILD SUCCESS** — 20 tests / 4 classes, incl. `EntityStandardsArchitectureTest`, `ClasspathVisibilityGuardTest`, `DomainWallsTest` |
| `./mvnw -pl pos-openapi-validation test` | **BUILD SUCCESS** — 43 tests, 0 failures |
| `./mvnw -pl pos-security-common test` | **BUILD SUCCESS** — 28 tests, 0 failures |
| `scripts/generate-permissions.sh --sync --check` | **exit 0**, all modules up-to-date, **CATALOG_VERSION unchanged (v42)** |
| `scripts/check-flyway-hygiene.sh` | **passed**, 25 modules |
| lint hook `--module pos-supplier` | **PASS** — 113 rules / 59 files, 0 findings |
| lint hook `--module pos-security-common` | **PASS** — 113 rules / 1 file, 0 findings |
| `./mvnw -pl pos-supplier spotless:check` | **clean** (was failing at `5449315`; `770666e` fixed 38 files, format-only, 214 tests green before and after) |
| Full-reactor `verify` | **NOT RUN** — close-out gate, and no cross-module consumer exists: only `pos-archunit` references `pos-supplier` (verified by grep + pom scan), and that gate is green |

Spec regeneration was batched once after the contract work: `pos-supplier/openapi.yaml` via the `openapi` Maven profile, then `openapi-aggregate.yaml` rebuilt with the full **26**-module list (the script's own aggregation code, invoked with the complete list — never `generate-openapi.sh pos-supplier`). The aggregate came out **byte-identical**: 773 paths, 26 modules, 8 supplier paths, zero additions/removals — correct, because this pass changed operation *contents*, not path keys, and the aggregate indexes path keys only.

### Process caveat — READ THIS

The executing session had **no subagent-invocation tool**: `API Planner`, `anvil`, `API Surface Coder`, `Domain Data Coder`, `Backend Testing Agent`, `Code Review Agent` and `Documentation Agent` could not be invoked, and `mcp__tokensave-backend__*` was unavailable. All work — planning, implementation, testing and this documentation — was done by the orchestrator directly. **There is therefore NO independent Code Review Agent verdict for this fix wave.** An independent review of `5449315..9e61d81` is still owed before the PR.

### Slice 3 — now unblocked, three binding decisions received

1. **Exchange-audit FK: NO FK.** `ExchangeAudit` stores `vendorProfileId` as a plain UUID column with no FK to `supplier_profile`, plus a denormalised `supplierRef` snapshot for readability. Admin `deleteProfile` **keeps its current hard-delete semantics** — slice 2's behaviour and tests are unchanged, do NOT convert it to a soft disable. Audit rows survive profile deletion, satisfying ADR-0050 §7. `vendorProfileId` remains the identity in audit/event payloads; the `supplierRef` snapshot is descriptive only, never a join/filter key. Design Flyway V3 accordingly.
2. **Secret-store resolver: `env:` only; allowlist NOT deployment-configurable.** No `vault:`/AWS resolver in slice 3. Already implemented this wave (`26ed6a6`). Explicitly rejected: letting deployments widen the allowlist by property, because a deployment could then allowlist a scheme nothing resolves and push the failure back to call time — the exact defect fix #4 closes.
3. **Optimistic locking: last-write-wins accepted.** No `@Version` addition, no `version` field on profile views, no `If-Match` on PUT. The admin surface is low-concurrency (a handful of operators) and this avoids a third contract change and spec regeneration in the wave. Item #10 is **closed as decided, not deferred**. (Note: `SupplierProfileEntity` already carries a `@Version` column and the handler already maps `ObjectOptimisticLockingFailureException` → 409; the decision is only that the *version is not exposed on the contract* and `If-Match` is not required.)

Carry-forwards still in force for slice 3: `supplier:audit:read` (bit 445) **must** be enforced by the audit read endpoints or removed; payload reads must themselves be audited; `sellerPartyId`/`sellerAgencyCode` must be persisted or dropped from the YAML contract (and see decision 1 above — do the mapper unification first); **do not bump `CATALOG_VERSION`**; do not touch the route bases; do not run `generate-openapi.sh` with a module filter.

### Follow-ups recorded, deliberately NOT actioned (coordinator-confirmed)

- **Fleet-wide ADR-0042 §1 description-depth gap — NOT pos-supplier's to close alone.** ADR-0042 §1
  asks for a 7-part, 4–8-sentence tool-invocation description per operation. **No module in the repo
  follows it.** Median operation description length: pos-warranty 54 chars, pos-accounting 70,
  pos-people-contact 64, pos-supplier 58. `OpenApiModuleValidator` asserts description *presence*
  only, so `mode: STRICT` does not enforce depth either. Rewriting pos-supplier's 17 descriptions
  alone would make it the sole compliant module of 26 — a unilateral divergence in voice. Belongs in
  its own fleet-wide effort. The four items actually constituting slice-2 review finding #3
  (4xx → `ApiError`, 401/403 documented, `@Schema` on the admin records, `@Parameter`
  schema/example) are complete.
- **`module-inventory.yaml` also misses `pos-marketing`, `pos-people-contact`, `pos-tax`**
  (22 entries vs 26 spec-producing modules) — their specs are validated by nothing. Only
  `pos-supplier` was registered, per scope.
- **`pos-security-common` `spotless:check` already failed at base `da21c33`** on the
  `pos-people-contact` bits 443–444 comment padding. Pre-existing; not CAP-317's to repair.

> **Note on this file vs `Durion-Processing.md`:** `.gitignore:9` lists `Durion-Processing.md` under
> "#temp processing document", so the execution ledger is a **transient local run artifact and is
> never committed** (hence `.github/hooks/safe-delete-DP.sh`). `WAVE_PAUSE_STATE.md` is the tracked,
> durable resume brief. Anything that must survive a session — decisions, gate evidence, follow-ups —
> belongs **here**, not only in the ledger.

---

## UPDATE — independent review FAIL cleared + slice-3 client foundation started (2026-08-11)

Backend `cap/317-supplier-foundation` @ `cb61886`, pushed. Two commits on top of `9e61d81`.

### Independent Code Review of `5449315..9e61d81`: **FAIL** → now remediated in `cb61886`

The review confirmed all four blocking items plus #5 and #8 as genuinely closed, verified `770666e`
as truly format-only, confirmed the 500 branch leaks nothing, and independently corroborated the #8
premise correction (adding that `generate-permissions.py`'s sync functions are strictly append-only,
so the normalization is durable). It failed on four defects in the annotations added by `a5228d1`.

| # | Defect | Resolution |
| --- | --- | --- |
| 1 | All 17 `401`s declared an `ApiError` body no code path sends (`GatewaySecurityConfig:138` is `HttpStatusEntryPoint`, bodiless) | Fixed — **but not by the prescribed method**, see below |
| 2 | Binding `enabled` `@Schema` copied from `VendorProfileRequest`, describing a supplier-wide kill switch | Fixed in both binding records using their own correct javadoc |
| 3 | `EndpointBindingRequest` class `@Schema` named `SUPPLIER_CAPABILITY_NOT_CONFIGURED`, not a contract value | Fixed to `CAPABILITY_NOT_CONFIGURED` + a clause naming both and which is which |
| 4 | `version` example `"2.5"` resolves to nothing (legal keys are `A2_5`…`S2S_V1`); write path does no validation | Fixed to `A2_5`; description now states it is unvalidated on write and lists the shipped keys |
| 5 | `updateAuthConfig` allowlist guard undefended by any test | Test added; mutation-checked |
| 7 | `AuthReferenceRules` interpolated the rejected scheme into its message | Now names the field and allowlist only |
| 6 | Shape note on `SupplierConfigurationCodeMappingTest` | Javadoc pointer added; no restructuring |

### IMPORTANT — the prescribed fix for defect 1 was wrong, and regeneration is what caught it

Dropping `content =` from the 401 annotations does **not** produce a bodiless response. springdoc then
falls back to the method return type — *the very mechanism that caused the original 4xx defect* — and
the regenerated spec showed **13 of 17 401s newly typed as the SUCCESS schema**, which is strictly
worse than the defect being fixed. The working fix is an explicitly empty content:
`content = @Content(schema = @Schema(hidden = true))`. Verified in the regenerated artifact: 17
documented 401s, zero declaring a body.

**Generalizable lesson: for this module, "remove the annotation" is never equivalent to "declare no
body" — absence means inference. Any future response-shape change must be verified in the
regenerated spec, never reasoned about from the annotation diff alone.**

`403` deliberately **keeps** its `ApiError` body: `@PreAuthorize` denials flow through
`SupplierExceptionHandler.handleAccessDenied`, which does return an envelope. Verified, not assumed.

### Slice-3 client foundation (`55f8f49`) — first increment, no OpenAPI surface

`internal.client`: `ExchangeOutcome` (ADR-0052 §5 classification — only `PRE_SEND_FAILURE` is
retryable; idempotent batch reads stay a per-call property rather than widening the constants),
`ExchangeContext` + `ExchangeObserver` + `ExchangeObserverConfig` no-op default (invoked once per
attempt **including failures and each retry**, per ADR-0050 §7; carries no credential material),
`SupplierCorrelationContext` (thread-scoped mirroring `AuditActorContext` — *not* request-scoped,
because the per-binding scheduler has no servlet scope), and the three auth strategies +
`SupplierAuthStrategies` dispatcher (fails startup on a missing or duplicated type).

OAuth2 specifics: cache keyed per `authConfigId` (never per token URL, or two profiles sharing an
endpoint would share a token), expiry-skew refresh so a token cannot expire mid-flight and return an
indistinguishable 401, and single-flight behind a per-key lock with an in-lock re-check.
Mutation-checked: removing the re-check fails the concurrency test with "No further requests
expected". Basic+ApiKey encodes UTF-8 explicitly.

### Gate results at `cb61886`

pos-supplier `-am verify` **BUILD SUCCESS**, **313 tests** 0 failures (287 → 311 → 313);
pos-openapi-validation **43**; touched-file lint **0 findings**; `spotless:check` **clean**;
`generate-permissions.sh --sync --check` **exit 0**, CATALOG_VERSION unchanged at v42;
`openapi-aggregate.yaml` rebuilt with the full 26-module list and **diff-verified byte-identical**.
`pos-archunit -am test` **BUILD SUCCESS** — confirmed fresh, not inherited: 30 modules, 15:23 min,
all four rule classes re-run (`ArchitectureTests` 12, `ClasspathVisibilityGuardTest` 2,
`DomainWallsTest` 1, `EntityStandardsArchitectureTest` 5), 0 failures. Note for future waves: the
`-am` form runs every dependency module's full test suite, so budget ~15 min and run it detached.

### Slice-3 constraint carried forward (from the reviewer)

The 409 mapping for `CAPABILITY_NOT_CONFIGURED` is a **safety net, not the contract**. ADR-0050 §3
requires the capability endpoints to surface that condition as a **typed 200 status**, so slice 3
must still convert at the service layer. The 409 makes an escaped exception look plausible rather
than loud — treat any 409 with that code as a defect signal.

### Two more fleet-wide gaps logged as notes (out of scope, same shape as the description gap)

- **No module supplies swagger `@RequestBody` description/required/examples** (ADR-0042 §3).
- **pos-supplier is now strictly ahead of the fleet** on 401/403 documentation and `ApiError` response
  typing — pos-warranty documents neither. Scope all three ADR-0042 gaps into one fleet pass.

---

## FOLLOW-UP (own PR, NOT this wave) — resilience4j family is half-pinned and resolves split

**Proven, not inferred.** `./mvnw -pl pos-document-helper dependency:tree -Dincludes='io.github.resilience4j:*'`
and the same for `pos-supplier` both resolve **identically**:

```
resilience4j-spring-boot4      2.4.0   <- root pom pin
  resilience4j-spring6         2.3.0
    resilience4j-annotations   2.3.0
    resilience4j-consumer      2.3.0
    resilience4j-framework-common 2.3.0
  resilience4j-micrometer      2.3.0
    resilience4j-bulkhead / -ratelimiter / -timelimiter  2.3.0
resilience4j-retry             2.4.0   <- root pom pin
  resilience4j-core            2.3.0
resilience4j-circuitbreaker    2.3.0
```

So **2.4.0 autoconfiguration and retry sit on a 2.3.0 core / circuitbreaker / spring6 / micrometer
stack**. `resilience4j-retry:2.4.0` depends on `resilience4j-core:2.3.0`.

### Exact mechanism (traced to the line)

1. Root `pom.xml` imports `spring-cloud-dependencies:${spring-cloud.version}` (2025.1.2).
2. That transitively imports `spring-cloud-circuitbreaker-dependencies:5.0.2`, which at
   **line 88** sets `<resilience4j.version>2.3.0</resilience4j.version>` and at **lines 92–98**
   imports `io.github.resilience4j:resilience4j-bom` at that version — managing the whole family.
3. Root `pom.xml` `dependencyManagement` then overrides **exactly two** artifacts
   (`resilience4j-retry`, `resilience4j-spring-boot4`) to `${resilience4j.version}` = **2.4.0**.
4. **Spring Boot's own BOM (4.1.0) does not manage resilience4j at all** — verified by grepping
   `spring-boot-dependencies-4.1.0.pom`. The 2.3.0 baseline is Spring Cloud's, not Boot's.

**The defect is the half-pin**, not any single artifact: two members of a family that an imported
BOM manages as a coherent set are overridden individually.

### Two options

- **A (leaning, and the coordinator's): delete both partial pins** and let the imported
  `resilience4j-bom` govern the whole family at 2.3.0. Coherent, the combination Spring Cloud
  ships and tested, zero new downloads. Also removes the `resilience4j.version` property's
  misleading appearance of controlling the family.
- **B: import `resilience4j-bom:2.4.0` explicitly** in root `dependencyManagement` *before* the
  spring-cloud import, moving the whole family up together. Coherent at a newer version, but
  pulls new artifacts and diverges from what Spring Cloud 2025.1.2 was tested against.

### Why not in this wave

Either change alters what **`pos-document-helper` and `pos-tax`** resolve, so it needs their test
suites as evidence and belongs in its own PR — not inside a capability wave. `pos-supplier` mirrors
`pos-document-helper`'s GAVs exactly and inherits the split rather than introducing it (proven: both
trees are identical).

### Consequence already honoured in pos-supplier

**Do not rely on any resilience4j auto-configured behaviour that differs across 2.3/2.4.** The module
configures `Retry`/`CircuitBreaker` **programmatically** (matching `DocumentClient`), ships **no**
resilience4j yaml, and leaves **`registerHealthIndicator` off** — verified: the string appears only in
explanatory comments, never as a setting.

---

## DECISION — supplier health indicator always reports UP (new repo pattern, justified)

`SupplierClientHealthIndicator` reports breaker state in `details` only and **never DOWN**.

**The hazard it closes.** `docker-compose.yml:1291-1297` gives pos-supplier
`healthcheck: wget -qO- http://localhost:8080/actuator/health` (`retries: 12`,
`start_period: 300s`), and `application.yml:20-29` exposes `health` with **no health groups defined**
and `show-details: when-authorized`. Every contributor therefore lands in the aggregate status the
container healthcheck reads. Sibling services in the same file gate startup on
`condition: service_healthy`. So a DOWN on breaker-open would mark the container unhealthy on one
vendor's outage, Docker would restart it, and dependents would refuse to start — **handing any vendor
a restart lever over our own service.** A supplier being unreachable is the expected steady state a
circuit breaker exists to absorb; pos-supplier with every vendor down still serves its admin API and
audit reads perfectly well.

*Verified nuance:* **nothing currently gates on pos-supplier** (`depends_on` scan found no
dependents — it is a new module), so today's hazard is the **container restarting itself**; the
dependent-startup hazard is latent and arrives with the first consumer.

**Constraints honoured**
- Always `Health.up()`; never `status(open > 0 ? DOWN : UP)`. **Mutation-checked**: applying
  resilience4j's OPEN→DOWN default fails 3 tests, each naming the restart hazard.
- `registerHealthIndicator` **not** set on any resilience4j config (its default is this hazard).
- Keys are `(vendorProfileId, capability)`; breaker name `supplier.<vendorProfileId>.<CAPABILITY>`.
- Details carry no credential material — a test asserts the rendered details contain no `http://`,
  `https://`, `Bearer `, `Basic `, `env:` or `apikey`, which matters because
  `show-details: when-authorized` makes them HTTP-reachable.
- **Micrometer is the alerting channel** for breaker state; health answers "is this pod serviceable",
  and it is. A per-key gauge follows with `SupplierClientMetrics`.

New repo pattern (no `HealthIndicator` exists anywhere else in the repo), justified because the
alternative default would let a vendor outage restart our container. Boot 4 package is
`org.springframework.boot.health.contributor` — verified against `spring-boot-health-4.1.0.jar`, not
guessed.

**If a future wave adds health groups**, the alternative shape is to keep this indicator out of the
default group and expose it at `/actuator/health/suppliers`; the always-UP contract makes that
optional rather than necessary.

---

## SLICE 3 PROGRESS — client transport layer COMPLETE (2026-08-11)

Backend `cap/317-supplier-foundation` @ `69ef390`, pushed. `55f8f49`, `4a38426`, `69ef390`.

### Done — `internal.client` (13 production classes, 342 module tests)

| Piece | Notes |
| --- | --- |
| `ExchangeOutcome` | ADR-0052 §5 classification; only `PRE_SEND_FAILURE` retryable |
| `ExchangeContext` / `ExchangeObserver` / `ExchangeObserverConfig` | Per attempt incl. failures; no-op default; no credential material |
| `SupplierCorrelationContext` | Thread-scoped (scheduler has no servlet scope); reuse-or-generate |
| 3 auth strategies + `SupplierAuthStrategies` | Call-time resolution, never logged; startup validation |
| `SupplierBreakerRegistry` | Breaker per `(vendorProfileId, capability)`; `minimumNumberOfCalls` guard |
| `SupplierClientHealthIndicator` | **Always UP**; see the decision section above |
| `SupplierClientMetrics` | `supplier.client.breaker.state` gauge + `supplier.client.exchanges` timer |
| `SupplierHttpRequest` / `SupplierHttpResponse` | Transport-level; codecs map to `SupplierExchange` in CAP-318 |
| `SupplierBaseClient` | The transport itself |
| `FaultInjectingHttpServer` (test) | Socket-level faults per binding decision 2 |

### Two real defects found by testing, not inspection

1. **`toEntity(String.class)` failed on unexpected content types.** Now reads `byte[]` and decodes
   UTF-8 — the correct design for a transport that will carry XML, JSON and EDIFACT under assorted
   (sometimes absent) content types.
2. **Any `RestClientException` other than the two caught escaped UNCLASSIFIED** to the caller as a raw
   Spring type — the error leak ADR-0050 §3 forbids. Added a catch-all classified ambiguous, since a
   conversion failure means a response *was* received and therefore the request *was* sent.

Also, the socket fixture's refused-port helper originally used `HttpServer.create` + `stop`, which
leaves the port bound with no accept loop: the client connected into the backlog and **read**-timed
out, silently inverting what the pre-send test proved. Now a plain `ServerSocket`, closed, for a
genuine `ECONNREFUSED`. Worth remembering — a fixture bug that makes a test pass for the wrong reason
is worse than a failing test.

### Deliberate conservative choice, recorded

`SocketTimeoutException` covers **both** connect and read timeouts, and the JDK distinguishes them
only by message text. Any socket timeout is therefore classified `POST_SEND_AMBIGUOUS`. The risk is
asymmetric: calling a connect timeout ambiguous costs one retry we could safely have made; calling a
read timeout pre-send costs **a duplicate purchase order at a vendor**. Connection refused, unknown
host and no route are definitively pre-send and keep their retryable classification. This slightly
under-retries by design.

### Gate results at `69ef390`

pos-supplier `-am verify` **BUILD SUCCESS**, **342 tests** 0 failures (322 → 342);
`spotless:check` clean; touched-file lint **0 findings**. Retry tests assert on requests that actually
reached the wire (`receivedRequests()`), not only on the returned outcome, because an outcome can look
correct while the client silently re-sent the document.

**Mutation checks now at six**, each confirmed to fail when the guarantee is removed: OAuth2
single-flight, `updateAuthConfig` allowlist guard, configuration code→status completeness, the
never-DOWN health guarantee, and the never-retry-ambiguous rule (dropping the idempotent gate fails
exactly the read-timeout and 5xx tests).

### REMAINING in slice 3 — not started

1. `ExchangeAudit` entity + **Flyway V3** (no FK, snapshot `vendorProfileId` + `supplierRef`,
   plus `supplier_schedule_lease` per decision 4)
2. `EncryptedPayloadConverter` per binding decision 1 (AES-256-GCM, bytea, 0x01 version + key-id +
   12-byte nonce + ciphertext, fail-closed in prod/indus/alpha, ephemeral + WARN in dev/test)
3. Capture levels `FULL|REDACTED|METADATA_ONLY` + credential-header redaction
4. 400-day retention purge (delete payloads, keep metadata rows)
5. Per-binding scheduler per decision 4 (compare-and-claim lease on DB `now()`, heartbeat, owner-guarded
   checkpoint in the batch transaction)
6. Audit read API behind bit **445** — must actually enforce `supplier:audit:read` or the permission is
   removed (ADR-0025 §4 parity debt), and payload reads must themselves be audited (ADR-0050 §7)

The audit writer will be an `ExchangeObserver`, which is why that SPI exists: `SupplierBaseClient`
never depends on a repository.

---

## UPDATE — client-layer review FAIL cleared; encryption landed (2026-08-11)

Backend `cap/317-supplier-foundation` @ `1f6fc1a`, pushed. Commits since `69ef390`:
`67dc356` factory switch, `a5caf79` encryption, `585c49c` review queue, `1f6fc1a` token invalidation.

### Independent review of `9e61d81..69ef390`: **FAIL** → cleared

All findings reproduced in the code before fixing. Three HIGH were behavioural, not annotation-deep.

| # | Finding | Resolution |
| --- | --- | --- |
| 1 | OAuth2 **token-endpoint transport** failure reported as `AUTH_CONFIG_MISSING` → 409, never retried | Split three ways (below) |
| 2 | `contentType` mandatory on the record, **never sent** — every document went out `text/plain` | Applied and asserted on the wire |
| 3 | Query params/`pathSuffix` unencoded; `absoluteUri` **outside every try**, so a malformed URI escaped raw with **no observation** | Encoded; URI resolved once inside the observed region |
| 4 | `ExchangeContext` javadoc claimed bodies "already redacted" — they are not | Corrected on both `ExchangeContext` and `ExchangeObserver` |
| 6 | Breaker counted **every** exception, laundering permanent rejections into retryables | `recordException(countsAsTransportFailure)` |
| 5 | never-DOWN guarantee not total (no try/catch, no Boot wrapping) | Wrapped; UP + `detailsUnavailable` |
| 7/8 | `invalidate` had no caller — a revoked token re-sent for up to an hour | Wired on 401 |
| 10-13 | Token in record `toString`; two `@NonNull` gaps; fixture leaked its executor | All fixed |

### Finding 1 in detail — the worst defect in the range

`catch (RestClientException)` is the **parent** of both `ResourceAccessException` and
`RestClientResponseException`, so a refusal, timeout, 503 or 429 on the **token** leg all became
`AUTH_CONFIG_MISSING` → `CONFIGURATION_ERROR` → 409, blaming the operator for a correct env var.
Now split:

- **transport failure of the token leg** → new `SupplierAuthTransportException` → classified
  `PRE_SEND_FAILURE` and **returned**, so a caller can consult `isSafeToRedispatch()`. Nothing reached
  the vendor's *business* endpoint, so ADR-0052 §5 makes it unambiguously pre-send.
- **401/403** → new code `SUPPLIER_AUTH_CREDENTIALS_REJECTED` (409). The references resolved; the
  vendor refused them. Retrying would only hammer the vendor.
- **2xx with no `access_token`** → new code `SUPPLIER_AUTH_TOKEN_RESPONSE_INVALID` (500-generic). A
  vendor contract violation, not operator error.

Adding two codes forced two explicit HTTP decisions, because
`SupplierConfigurationCodeMappingTest` fails on any unmapped code — the guard doing its job. The table
outgrew `Map.of`'s 10-pair limit and moved to `Map.ofEntries`.

### Rulings applied

- **Finding 6:** fixed *what opens the breaker*, not `isSafeToRedispatch()`. ADR-0052 §3's premise is
  true for a genuine transport-driven breaker; only the miscount made it misleading.
- **3xx:** classified `CONFIGURATION_ERROR` naming the redirect target — "fix this profile's baseUrl",
  not "the vendor permanently refused this order". **Note:** it arrives on the **success** path, since
  Spring's `retrieve()` raises only for 4xx/5xx. My first attempt put it in the exception handler,
  where it was dead code until a test caught it.

### Two pre-existing tests asserted the DEFECT and were corrected, not worked around

The token-endpoint-500 test pinned `AUTH_CONFIG_MISSING` — it *was* finding 1. Second time this wave
that tests were part of the defect (the first was finding 7's scheme-echo tests).

### Encryption (`a5caf79`) — binding decision 1

AES-256-GCM, envelope `0x01 | keyIdLen | keyId | 12-byte nonce | ciphertext+tag`, header as **AAD** so
version and key id are tag-covered. Key mandatory in prod/indus/alpha (fail closed), ephemeral + WARN
in dev/test. **Rotation via decrypt-only `previous-keys`** — this is what the key id is *for*: a
400-day retention window outlives any key lifetime, so without it rotation would orphan every payload.
`PayloadUnreadableException` keeps malformed / unknown-key / authentication-failed distinct, because a
failed GCM tag is potential tampering evidence and must not read as routine misconfiguration.
Converter maps `null`↔`null`: `METADATA_ONLY` bindings and purged rows legitimately have no payload.

Two justified `nosemgrep` suppressions on the GCM sites — `gcm-detection` is an **audit** rule asking a
human to confirm nonce uniqueness, which the `SecureRandom` per-message nonce and the 500-iteration
test establish. **pos-supplier is the first module in the repo doing crypto**, so the first to meet
these rules. Bare form because the local hook derives rule ids from the rules-clone path.

### Gate evidence at `1f6fc1a` — also covers `67dc356`, whose recorded evidence predated it

pos-supplier `-am verify` **BUILD SUCCESS**, **387 tests** 0 failures (342 → 387);
`spotless:check` clean; touched-file lint **0 findings**; `generate-permissions --sync --check` exit 0,
CATALOG_VERSION unchanged. **Mutation checks now at ten**, each confirmed failing when its guarantee is
removed — added this round: breaker `recordException`, the Content-Type header, the connect/read
ordering trap, the AAD binding, and the 401 invalidation.

### Still open from findings 7/8 — recorded, not silently skipped

- The OAuth2 **token leg runs on the platform-internal `RestClient.Builder`**, whose own javadoc says
  vendor-facing transport does not run through it, so **per-profile timeouts do not apply to the token
  call**.
- **`updateAuthConfig` does not invalidate a cached token** when an operator rotates a secret, so a
  rotated credential is not picked up until natural expiry.

### REMAINING in slice 3

`ExchangeAudit` entity + **Flyway V3** (no FK, snapshot `vendorProfileId` + `supplierRef`, plus
`supplier_schedule_lease`); capture levels + credential-header redaction **in the observer** (its
obligation, per the corrected contract); 400-day purge keeping metadata; scheduler per decision 4;
audit read API behind bit **445**, which must genuinely enforce it (ADR-0025 §4 parity debt) and whose
payload reads must themselves be audited (ADR-0050 §7).

---

## PATTERN TO WATCH — twice this wave, tests asserted the defect

Recording this because it recurred and the failure mode is subtle: **a test that pins wrong behaviour
is worse than no test**, because it converts a defect into a defended invariant and the next person to
fix the code sees a red build and assumes they broke something.

1. **Finding 7 (scheme echo).** `AuthReferenceRulesTest` and `SupplierYamlBootstrapTest` asserted
   `hasMessageContaining("user:")` — i.e. they required the message to echo the rejected scheme, which
   for a plaintext credential containing a colon *is* credential text. The tests were half the defect.
2. **Finding 1 (token-leg transport).** The token-endpoint-500 test asserted
   `AUTH_CONFIG_MISSING`, pinning exactly the misclassification the review flagged.

Both were corrected to the right contract rather than worked around. When a fix makes an existing test
fail, check whether the test was asserting the defect before assuming the fix is wrong.

## PATTERN TO WATCH — framework behaviour beats reasoning in this module

Two prescriptions in this wave did not survive contact with actual framework behaviour, and in both
cases only a regenerated artifact or a real test caught it:

1. **"Drop `content =` to make 401 bodiless"** — springdoc treats an absent `content` as an instruction
   to *infer from the method return type*, so 13 of 17 401s became typed as the SUCCESS schema, worse
   than the defect. Fixed with `@Content(schema = @Schema(hidden = true))`.
2. **"Classify 3xx in the `RestClientResponseException` handler"** — Spring's `retrieve()` raises only
   for 4xx/5xx, so a redirect arrives on the **success** path. The branch was dead code until a test
   failed with `expected: CONFIGURATION_ERROR but was: OK`.

**Rule for this module: verify contract- and transport-shape changes against the regenerated spec or a
real socket, never against the annotation/handler diff alone.**

---

## UPDATE — audit read API delivered (2026-08-11)

Backend `cap/317-supplier-foundation` @ `a61b369`, **not pushed, no PR** (per standing instruction).
durion `claude/ediwheel-integration-arch-tr3ibn` @ `400422a`.

### The ADR-0025 §4 parity debt is closed

Bit 445 is now genuinely enforced. Five endpoints under `/v1/supplier/admin/audit`, every one
`@PreAuthorize`-gated on `supplier:audit:read`, and `SupplierExchangeAuditControllerWebMvcTest`
exercises each three ways — granted, **denied while holding both `supplier:profile:read` and
`supplier:profile:write`**, and unauthenticated. Mutation-proven: weakening the payload endpoint to
`PROFILE_READ` fails the separation tests with `403 expected but was 200`.

Endpoints: `GET /exchanges` (windowed, paginated, optional capability filter),
`GET /exchanges/by-correlation/{id}` (oldest-first — a retry sequence only reads as a sequence in the
order it happened), `GET /exchanges/{id}`, `GET /exchanges/{id}/payload` (the audited read),
`GET /exchanges/{id}/accesses`.

**Not nested under `/profiles/{vendorProfileId}`**, and that follows from binding decision 1: audit rows
carry no FK and `deleteProfile` is a hard delete, so the audit trail of a *deleted* supplier is exactly
what an investigation asks for. A path implying "child of an existing profile" would promise something
the data model deliberately refuses. The profile is a query filter.

### Two structural properties, both mutation-proven

**1. Metadata reads never decrypt.** Every listing selects into `ExchangeAuditMetadata` via an explicit
JPQL constructor expression that does not name the payload columns. Not an interface projection: "no
plaintext is produced here" is a security property and must not depend on a projection optimisation.
Three consequences, all wanted — no crypto work on listings, no plaintext in the JVM for a request that
asked for none, and **a row whose payload cannot be decrypted still lists**. That last one is the point:
the row an investigation is looking for must not be the row that breaks the listing.

**2. The access record is a precondition of access, not a side effect.** `AuditAccessRecorder` is
`MANDATORY`, does not catch, and `saveAndFlush`es inside the read's own transaction. If it cannot be
written, the caller gets nothing.

The counterpart took a second decision: `readPayload` declares
`noRollbackFor = PayloadUnreadableException.class`. An unreadable payload is a read that **happened** and
produced nothing usable — the case §7 most wants recorded, because it may be tampering or a mishandled
rotation. Letting it roll back would delete the evidence of the one event worth investigating.

This forced a design change worth knowing about: **payload content cannot be read through
`ExchangeAuditEntity`.** Its converter decrypts during Hibernate *hydration*, so a decrypt failure would
be raised from inside the persistence context with the identity needed to write the access row trapped in
the load that just failed. Content is therefore read through `ExchangeAuditRawPayloadEntity` — a second,
`@Immutable` mapping of the same table with **no converter** — and decrypted explicitly in the service.
Mutation-proven: adding `@Convert` to that entity (the exact mistake its javadoc forbids) fails the
UNREADABLE test.

### Decisions taken

1. **`payload_outcome`, not `decrypt_outcome`, with three values.** `NOT_CAPTURED` joins
   `DECRYPTED`/`UNREADABLE` for the legitimately empty cases (METADATA_ONLY, no body, purged). An
   evidentiary row must not claim a disclosure that never happened. `SupplierContractKeyParityTest` now
   pins the enums to the V4 CHECK constraint text — that pair has a *third* copy in SQL, and a constant
   added to the enum alone would surface as a constraint violation on an audit write, i.e. exactly where
   §7 makes the write a precondition of serving a payload.
2. **`PayloadUnreadableException` → 500 with its typed code and a generic message.** Handled explicitly,
   not left to the catch-all, for three reasons: the catch-all would collapse "unconfigured key"
   (operator error) and "failed GCM tag" (possible tampering) into `INTERNAL_ERROR`; it would log a
   security-relevant integrity failure as "Unhandled exception"; and with rotation shipped this path is
   **reachable in normal operation**. The exception's own message names the envelope's key id and the
   `previous-keys` property — useful in a log, and never returned. `AUTHENTICATION_FAILED` gets its own
   log line saying what it may mean. Test asserts the response contains neither the key id nor the
   property name.
3. **A 403 is not a payload read** — denials stay out of `supplier_audit_access` (schema-pinned by
   `chk_saccess_kind`), and reading the access trail is not itself recorded, or every audit review would
   generate the noise the next review has to sift through.
4. **Access-row retention is permanent**, stated in the migration as a decision. The 400-day payload purge
   deliberately does not apply: this table holds no payloads, it holds the record of who saw them.
5. **NO `correlation_id` on the access row.** The only correlation scope this module has is
   `SupplierCorrelationContext`, established around an **outbound** exchange — it is not in flight during
   an inbound audit read, so the column could only ever have been null, and a permanently null column reads as
   "this read had no correlation" rather than "we never captured one". See the follow-up below.
6. **Half-open query windows** (`from` inclusive, `to` exclusive), replacing `Between`. Adjacent windows
   tile without listing a boundary attempt twice; `from == to` is a typed 400 rather than a silently empty
   page. An unknown capability filter is also a typed 400 — a filter that silently matches nothing turns a
   typo into "this integration was never used".
7. **`PagedResponse<T>` in `service.model`**, following pos-customer/pos-marketing rather than returning
   Spring Data's `Page`, whose unstable nested shape would leak into the SDK. Kept free of Spring types;
   mapping lives on the impl side of the ADR-0026 boundary.

### ArchUnit caught a real defect, not a layering nit

The module cycle check failed on `internal.service → internal.client`, because the recorder read
`SupplierCorrelationContext`. Investigating rather than relocating the class revealed that scope is
**never active during an inbound audit read** — the field would have been null in production forever. The
cycle was removed by deleting an incorrect dependency, not by moving a class to accommodate it.

**FOLLOW-UP (recorded, deliberately not actioned):** `SupplierCorrelationContext`'s javadoc states an
inbound `X-Correlation-Id` "must be reused so a vendor exchange can be traced back to the operator action
that caused it". **Nothing in production does this** — only a test calls `withCorrelationId`. Implementing
it properly means a `OncePerRequestFilter` establishing the scope for every inbound request, which would
also give outbound vendor calls the operator's correlation id and would let the access row carry an honest
one. That is a module-wide request-handling change and was not smuggled into this slice.

### Pre-existing breakage found and fixed: pos-archunit was RED

`pos-archunit` had **not** been run after the scheduler-lease commit (`32d8080`) — my verification gap.
`SupplierScheduleLeaseEntity` was violating four fleet entity rules.

- **ADR-0024** (`@CreatedDate`/`@LastModifiedDate`/`@EntityListeners`): added. Behaviour-neutral for the
  lease's DB-time guarantee — every *transition* still sets `updated_at = now()` in the atomic UPDATE;
  the listener applies only at JPA insert, the one moment there is no transition to attribute it to.
  Nothing about lease safety reads `updated_at`.
- **ADR-0013** (`@UUIDv7Id` on a UUID `@Id`): added to both `SupplierScheduleLeaseEntity.bindingId` and
  the new `ExchangeAuditRawPayloadEntity`. **Both are assigned natural-key ids that are never generated.**
  This is safe only because `UUIDv7HibernateGenerator` returns `currentValue != null ? currentValue :
  generate()` and reports `allowAssignedIdentifiers() == true` — verified in source, not assumed.

  **Trade-off named rather than hidden:** before the annotation, saving a lease with a null `bindingId`
  failed loudly on NOT NULL; now it would silently insert a lease keyed to a nonexistent binding, which no
  constraint catches (valid UUID, no FK). `SupplierScheduleLeaseRepositoryTest`
  `.assignedBindingIdSurvivesTheIdentifierGenerator` pins assigned-id-wins so a regression fails the
  build. **DECISION GAP for the fleet:** ADR-0013 governs id *generation*; an assigned natural-key id is
  outside its scope, and the rule has no exemption for `@Id` fields that are never generated. The correct
  fix is to amend `EntityStandardsArchitectureTest` to exempt them — a 9-module fleet decision, so the gate
  was satisfied rather than weakened unilaterally.

### Hook integrity fix (durion `400422a`) — the mutation checker had this wave's own bug

`mutation-check-hook.sh` gate 3 checked that a `Tests run:` line *existed*. Maven prints
`Tests run: 0, Failures: 0` when a selector matches nothing, so the gate passed on a run where nothing
executed — and a run with no tests neither fails nor proves anything, which gate 3 then reported as
**UNDEFENDED**. Hit for real: `Class#method` does not match a method inside a `@Nested` class (surefire
needs `Class$Nested#method`), so the first check of the `noRollbackFor` guard was reported UNDEFENDED on
the strength of zero tests. The gate now requires a non-zero count and its abort message names the
`@Nested` selector form. With the fix the same check reports DEFENDED.

**This is the second time the same failure class has appeared in this wave.** The first produced the hook;
the second was inside the hook.

### Mutation checks recorded (11, all DEFENDED)

| Mutation | Test that failed |
| --- | --- |
| `noRollbackFor` removed from `readPayload` | access record lost on unreadable payload |
| `MANDATORY` → `REQUIRED` | recorder callable outside a transaction |
| `@Convert` added to `ExchangeAuditRawPayloadEntity` | access record lost on unreadable payload |
| access-write failure swallowed | payload served despite unrecordable read |
| `classify` always returns `DECRYPTED` | `NOT_CAPTURED` outcome |
| `!to.isAfter(from)` → `to.isBefore(from)` | empty-window rejection |
| metadata read routed through `findById` (entity hydration) | undecryptable row readable by id |
| payload endpoint `@PreAuthorize` weakened to `PROFILE_READ` | both permission-separation tests |
| `NOT_CAPTURED` dropped from the V4 CHECK constraint | enum↔constraint parity |
| `@CreatedDate` removed from the lease | NOT NULL insert |
| (plus the pre-fix run that exposed the hook's own gate bug) | — |

### Gate results at `a61b369`

| Gate | Result |
| --- | --- |
| `./mvnw -pl pos-supplier -DskipTests=false test` | **499 tests, 0 failures** (313 → 499) |
| `./mvnw -pl pos-supplier -DskipTests=false verify` | **BUILD SUCCESS** |
| `./mvnw -pl pos-archunit -am -DskipTests=false test` | **BUILD SUCCESS** (was RED before this slice) |
| lint hook `--module pos-supplier` | **PASS** — 113 rules / 28 touched files, **0 findings** |
| `scripts/check-flyway-hygiene.sh` | **passed**, 25 modules |
| `scripts/check-authz-doc-drift.sh` | **passed**, catalog v42 unchanged |
| `pos-supplier/openapi.yaml` | regenerated via the `openapi` profile — **+5 paths, +5 schemas, 0 changed, 0 removed** |
| `pos-api-gateway/docs/openapi-aggregate.yaml` | rebuilt with the full **26**-module list — +10 lines, 773 → 778 paths, **additive-only**, no module dropped |
| Contract-rule audit of the 5 new operations | 401 bodiless ×5, 403 `ApiError` ×5, every 4xx/5xx `ApiError`, every parameter has schema + description |
| Full-reactor `verify` | **NOT RUN** — close-out gate |

`check-authz-doc-drift.sh` requires durion as a **sibling** of the backend (`../durion`); in this
container durion lives elsewhere, so a symlink was needed. Worth knowing before reporting it as broken.

### Still owed before close-out

1. **`updateAuthConfig` / delete / rename → OAuth2 cache invalidation.** NOT IMPLEMENTED. Seam decided:
   `internal.service` publishes a plain-record application event and `OAuth2ClientCredentialsAuthStrategy`
   `@EventListener`s it, so no `service → client` compile dependency appears. Put the event in
   `internal.spi` (framework-clean, already the neutral package for exactly this) — `service → spi` and
   `client → spi`, no cycle. `SupplierAuthStrategies.invalidateCachedCredential` already exists and is
   wired for 401s; only the admin-write trigger is missing. Today an operator who rotates a client secret
   keeps failing until the cached token expires naturally — up to an hour.
2. **Token-leg timeouts.** NOT IMPLEMENTED. Must come off the existing per-binding client cache; the
   now-false `RestClientConfig` javadoc needs correcting and the caught cause needs chaining.
3. **Independent Code Review of the persistence + audit-read range** (`1f6fc1a..HEAD`). Still owed; this
   session had no subagent-invocation tool, so there is again no independent verdict.
4. `pos-supplier/README.md` + `BACKEND_CONTRACT_GUIDE.md` (close-out item 9).
5. The inbound-correlation filter (above), and the fleet ADR-0013 assigned-id exemption decision.

---

## UPDATE — slice 3 complete: persistence review queue cleared, both owed items landed (2026-08-11)

### Review fix queue

| # | Item | Resolution |
| --- | --- | --- |
| F1 | Key-required predicate failed open | **Fixed.** Polarity inverted: an allowlist of where a key is OPTIONAL (`dev`, `test`) instead of a list of where it is required. An empty profile set — the shape compose actually ships — now fails startup. |
| F2 | `pos.supplier.audit.encryption.*` bound by nothing | **Fixed.** Wired in `application.yml`, `docker-compose.yml` and `.env.example` from `SUPPLIER_AUDIT_ENC_KEY`, the variable the failure message names. |
| F3 | `REQUIRES_NEW` on the audit write was inert | **Fixed.** Extracted `ExchangeAuditWriter`; the observer delegates through the injected bean. |
| F4 | Lease mutations joined the page transaction | **Fixed** on the coordinator, plus a recorded `now()` decision. |
| F5 | V3 CHECK constraints unpinned; `correlation_id` untruncated | **Fixed.** Both constraints pinned to their enums; the client-influenced field is truncated. |
| F6 | Per-binding configured redaction not implemented | **Recorded, not implemented** — deferred to CAP-318. §7 no longer reads as complete. ADR untouched. |
| F7 | `JpaRepository` on the converter-bypassing entity | **Fixed.** Narrowed to `Repository` with one declared `findById`. |
| F8 | `@ConditionalOnMissingBean` racing scan order | **Fixed.** `@Primary` on the audit observer; precedence declared, not raced for. |
| F9/F10 | "no FK" comment; `@PrePersist` guard | **Settled differently — see below.** |
| F11 | Purge has no lease | **Documented and tested as safe**, with the lease rejected for a stated reason. |
| F12 | Oversized values costing audit rows | **Fixed** with F5. |
| F13 | Raw NUL byte made a test file binary | **Fixed.** Unicode escape instead; git and ripgrep can read it again. |

### F9/F10 — I did not commit the guard you asked for, because it does not work

Two corrections, both found by writing the test rather than by reading the code:

1. **The hazard I reported does not exist.** V3 declares `fk_slease_binding` on `binding_id` with
   `ON DELETE CASCADE`. A minted lease id references no binding and fails the insert, loudly. My earlier
   report of "a silent orphan lease no constraint catches" was wrong, and my javadoc said so too. Corrected.
2. **`@PrePersist` fires AFTER identifier generation.** The guard could never observe a null id — it was dead
   code that read as protection. Removed.

What is committed instead is the test, repointed at the constraint that actually protects this, and
mutation-proven: dropping `fk_slease_binding` from V3 makes it fail. The residual concern is now purely that
an FK violation is a less readable message than a domain one, so the fleet ADR-0013 exemption follow-up is
cosmetic rather than safety-relevant — as you predicted, for a different reason than either of us had.

### F4 — the `now()` decision, and why it is a correctness issue

**PostgreSQL's `now()` is `transaction_timestamp()`: it does not advance for the transaction's lifetime.**
`heartbeat`, `stillOwns` and `release` are all called from inside a long-running page, so joining that
transaction broke each differently: a heartbeat five minutes into a ten-minute page would compute its new
expiry from the moment the page *started*, so the operation whose entire purpose is keeping a long run's lease
alive would compute an expiry already in the past and the lease would be stolen mid-run; `stillOwns` would
compare against the same frozen reading and call an expired lease live; `release` would roll back with a
failed page.

**Decision: keep `now()`, reject `clock_timestamp()`, move the boundary.** `clock_timestamp()` is
PostgreSQL-only, so every contention test that establishes this lease's correctness would stop running, and it
would not fix the release-rollback half at all. `REQUIRES_NEW` fixes both, portably.

**Placed on `SupplierScheduleCoordinator`, not the repository.** On the repository it would additionally stop
every `@DataJpaTest` of those queries from seeing its own uncommitted fixtures — the tests would have to be
rewritten around a constraint unrelated to what they prove. `advanceCheckpoint` deliberately stays `REQUIRED`
(binding decision 4).

**H2 cannot reproduce this** — its `now()` is statement-scoped — so no behavioural test can demonstrate it and
the boundary is pinned structurally. `SupplierScheduleCoordinatorTest` did have to move to real commit
boundaries, which is the right shape for a lease test anyway.

### The two owed items

**OAuth2 invalidation.** `internal.service` publishes `SupplierAuthConfigChanged`; `internal.client` listens.
Event, not a call, because the reverse edge is a package cycle; the record lives in `internal.spi`. Published
unconditionally on update **including a pure rename**, because rotating a secret changes the value behind an
unchanged `env:` reference and is invisible from here — over-signalling costs one token request,
under-signalling costs an hour of failures. A plain `@EventListener`, not `AFTER_COMMIT`, for the same
asymmetry. `deleteProfile` reads its auth config ids before the bulk delete.

**Known limitation, stated rather than assumed:** an application event does not leave the JVM, so this clears
the cache only on the instance that served the admin request. Complete for a single instance; a cross-instance
signal needs the platform event bus. Recorded as a follow-up.

**Token-leg timeouts.** Extracted `SupplierHttpClients` so the token leg shares the vendor transport. It had
been using the *platform* builder — 2s connect / 5s read budgets meant for in-cluster services, applied to a
third-party token endpoint, on `SimpleClientHttpRequestFactory`, which cannot distinguish a connect timeout
from a read timeout. **The token leg still had the exact defect `67dc356` fixed for the main leg.** Its
timeouts are now its own (`pos.supplier.oauth2.connect-timeout-millis` / `read-timeout-millis`, 5s/15s):
`apply()` receives only the auth config, and a token endpoint is a different endpoint from the vendor API and
reasonably budgeted separately. The `RestClientConfig` javadoc claiming vendor transport "does not run through
this builder" was false by the time OAuth2 shipped, and is corrected. The caught cause was already chained.

### A self-inflicted trap worth reading: test-resources config SHADOWS main config

Activating the `test` profile via `src/test/resources/application.yml` was the first attempt at F1, and it was
worse than the problem. **A test-classpath `application.yml` replaces the main one entirely**, so the module's
real configuration stopped being loaded by any test — and a duplicate `pos:` key I introduced in
`application.yml` survived a fully green **519-test** run, surfacing only when the app was actually started to
generate the OpenAPI spec. The profile is now activated by a surefire `systemPropertyVariables` entry, so the
main file loads in tests and `PosSupplierApplicationSmokeTest` is once again a real guard on it.

### The mutation hook, again

Two more findings in the tool itself, both of the same family as the first:

1. **Gate 4 added: `--expect-fail-message` is now mandatory for Spring-context tests.** A context test can
   fail because the assertion fired or because the mutation broke context startup, and those are
   indistinguishable by exit code — a DEFENDED verdict would not mean what it says. Detected from the
   annotations in the test source.
2. **`grep -qF` without `--`** treated an expected message starting with a dash as its own flag. Fixed in the
   hook and the self-test.

`mutation-check-selftest.sh` now covers 5 cases and is itself verified against the pre-fix hook: it fails
there, reproducing the exact false UNDEFENDED, and passes against the fixed one.

Also recorded, because it wasted a cycle: **AssertJ's `.as(...)` description is NOT in the failure output when
`assertThatThrownBy` fails because nothing was thrown** — the description is attached to the returned assert,
which never exists. One test was reshaped to assert on the collection rather than on `getFirst()` blowing up,
so its failure names the guarantee instead of throwing `NoSuchElementException`.

### Gates at the final commit

| Gate | Result |
| --- | --- |
| `./mvnw -pl pos-supplier -am -DskipTests=false verify` | **BUILD SUCCESS** — pos-supplier **519** tests (499 to 519) |
| `./mvnw -pl pos-archunit -am -DskipTests=false test` | **BUILD SUCCESS** |
| `./mvnw -pl pos-supplier spotless:check` | **clean** |
| lint hook, 25 touched files | **PASS**, 113 rules, **0 findings** |
| `scripts/generate-permissions.sh --sync --check` | **exit 0**, all up-to-date, **CATALOG_VERSION 42 unchanged** |
| `scripts/check-flyway-hygiene.sh` | **passed**, 25 modules |
| `pos-supplier/openapi.yaml` regenerated | **no change** — this wave altered no contract shape |
| `openapi-aggregate.yaml` rebuilt, 26 modules | **no change**, 778 paths, no module dropped |

### One new semgrep suppression

`SupplierAuthStrategyTest`: `strategy.supportedType() == SupplierAuthType.BEARER` is an **enum** comparison,
where `==` is correct and `.equals()` would be worse. Line-scoped, justification names the type and why the
rule cannot see it.

### Still owed at close-out

1. Independent review of the full remaining range.
2. `pos-supplier/README.md` + `BACKEND_CONTRACT_GUIDE.md`.
3. Follow-ups: inbound `X-Correlation-Id` filter; cross-instance credential invalidation; fleet ADR-0013
   assigned-id exemption (now cosmetic); CAP-318 per-binding redaction (F6).
