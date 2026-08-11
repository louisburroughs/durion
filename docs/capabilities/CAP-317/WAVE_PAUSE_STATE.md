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
