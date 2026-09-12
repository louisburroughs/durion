# Execution plan — Organization name on the login form

**Companion to:** `tenant-organization-name-login-search-spec.md` (the specification; this document
is the sequencing, validation and risk plan for building it)
**Date:** 2026-09-12
**Repos:** `durion-positivity-backend`, `durion-positivity-frontend`, `durion` (ADR only)

---

## 1. Shape of the work

Ten units of work across three repos, with **one hard gate in the middle**: the backend changes
controllers and DTOs, so `API Artifacts Sync` must regenerate the specs and SDKs before any frontend
work can compile against them.

```
        ┌──────────────────────────── can all run in parallel ────────────────────────────┐
        │                                                                                 │
   A. pos-tenant          B. pos-security-service     C. pos-api-gateway    E1. frontend  │  F. ADR
      schema + seeding       search endpoint             rate limiter          storage    │    addendum
        │                        │                          │                    │        │
        └────────────┬───────────┴──────────────────────────┘                    │        │
                     ▼                                                           │        │
        ══════ D. API Artifacts Sync (GATE — regenerates specs + both SDKs) ══════         │
                     ▼                                                                     │
              E2/E3/E4. frontend combobox, i18n, tests ◄──────────────────────────┘        │
                     ▼                                                                     ▼
                                        Done
```

**Critical path:** A/B → D → E2 → E4. C, E1 and F are off the critical path and should be picked up
whenever someone is blocked.

## 2. Branches and PRs

Per `durion/CLAUDE.md` §Git Workflow — branch `feat/tenant-organization-login-search` in each repo
(or `cap/<cap-id>-tenant-organization-login-search` if this gets a capability id).

Four PRs, in this order:

| PR | Repo | Contents | Merge before |
|---|---|---|---|
| 1 | backend | A + B + C (one PR — they share the Flyway/test cycle and D needs all of them) | D |
| 2 | backend (generated) | Whatever `API Artifacts Sync` opens | PR 3 |
| 3 | frontend | E1–E4 | — |
| 4 | `durion` | F (ADR addendum) | — anytime |

PR 1 is the big one. If it needs splitting for review, split **A** from **B+C** — they touch
different modules and different databases, and neither depends on the other.

---

## 3. Stages

### Stage A — `pos-tenant`: uniqueness and seeding

Spec §4.1, §4.2, §4.4, §5.1, §5.2.

| # | File | Work |
|---|---|---|
| A1 | `pos-tenant/src/main/resources/db/migration/V3__tenant_display_name_key.sql` *(new; V2 is the highest existing)* | Add nullable `display_name_key`; backfill with `lower(btrim(regexp_replace(display_name,'\s+',' ','g')))`; `DO` block suffixing ` #<n>` on collisions by `created_at`; `SET NOT NULL`; `ADD CONSTRAINT tenant_display_name_key UNIQUE (tenant_id, display_name_key)`. Plain column, **not** `GENERATED ALWAYS` — see §5.1 of the spec and R3 below. |
| A2 | `internal/entity/TenantEntity.java` | Map `displayNameKey` as an ordinary insertable/updatable column, `nullable = false`, length 200. |
| A3 | `internal/service/TenantDisplayNameAllocator.java` *(new)* | `normalize(String)` (NFKC → trim → collapse → lower) and `allocate(String legalName, Predicate<String> keyTaken)`. Pure; no Spring, no repository — takes the predicate. |
| A4 | `internal/repository/TenantRepository.java` | `boolean existsByDisplayNameKey(String key)`. |
| A5 | `internal/service/TenantServiceImpl.java` | `create`: seed from `accountRepository.findById(accountId).legalName` when `displayName` is blank; set the key on every write; catch `tenant_display_name_key` violations → `DuplicateResourceException("TENANT_DISPLAY_NAME_TAKEN")`, mirroring the existing `isSlugCollision` branch at `:63`. `update`: same key maintenance and collision handling. |
| A6 | `internal/dto/TenantCreateRequest.java` | `displayName` → optional: drop `@NotBlank`, `requiredMode = NOT_REQUIRED`, keep `@Size(max = 200)`; `@Schema` says the seed is a fallback and a meaningful name is preferred. |
| A7 | `internal/controller/PlatformTenantController.java` | `@Operation` text for create/update: seeding rule, new `409`. `@ApiResponse(responseCode = "409")` on both. |

**Validate**

```bash
./mvnw -pl pos-tenant -am -Dtest=TenantDisplayNameAllocatorTest,TenantServiceImplTest test
./mvnw -pl pos-tenant -am test                      # includes ArchitectureTest
./mvnw -pl pos-tenant -am verify                    # Flyway/RLS ITs: TenancySchemaConformanceIT, TenantIsolationIT
./mvnw spotless:apply && ./mvnw -pl pos-tenant validate
```

**Exit:** an account's first tenant seeds to the bare legal name, the second to `<legal name> #2`;
a colliding explicit name is rejected with `409`; the constraint holds at the database; the
`dev`/H2 path populates the key identically (R3).

---

### Stage B — `pos-security-service`: the search endpoint

Spec §5.3, §5.4. Independent of A.

| # | File | Work |
|---|---|---|
| B1 | `db/migration/V6__ext_tenant_display_name_search.sql` *(new; **V5** is the highest existing — not V4)* | Add `display_name_key`, backfill from `display_name`, index `(display_name_key text_pattern_ops)`. |
| B2 | `internal/entity/ExtTenant.java` + the `tenant.events.v1` listener | Maintain `display_name_key` on every projection upsert, using the same normalization as A3. Extract the normalizer to a shared place, or duplicate it with a test asserting the two agree — do not let them drift. |
| B3 | `internal/repository/ExtTenantRepository.java` | `searchActiveByDisplayNameKey(String q, Pageable limit)` — `status = 'ACTIVE' AND (key LIKE :q\|\|'%' OR key LIKE '% '\|\|:q\|\|'%')`, ordered by `length(display_name), display_name`. |
| B4 | `internal/dto/TenantSearchResponse.java` *(new)* | `record TenantSearchResponse(String slug, String displayName)`. Nothing else — no id, no status. |
| B5 | `internal/controller/…` | `GET /v1/auth/tenants?q=`, `@PreAuthorize("permitAll()")`, `@EmitEvent(id = "SECURITY_TENANT_SEARCH", apiVersion = "1")`. Below the minimum length → empty list, no query. Disabled → `404`. |
| B6 | `internal/config/…EventTypes.java` | Register `SECURITY_TENANT_SEARCH` with the `search` preset. |
| B7 | `application*.yml` | `auth.tenant-search.{enabled,min-query-length,max-results}`; **`enabled: true` in every profile** including `prod`. |

**Validate**

```bash
./mvnw -pl pos-security-service -am -Dtest='TenantSearch*Test,LoginTenantResolverTest' test
./mvnw -pl pos-security-service -am test
```

**Exit:** `q=acm` returns `Acme…`; `q=tire` matches mid-name by word prefix; `q=cme` matches
nothing; non-`ACTIVE` excluded; ≤10 results; response has exactly two fields; **every pre-existing
`LoginTenantResolver` test still passes unmodified** (the uniform 401 is untouched).

---

### Stage C — `pos-api-gateway`: rate limiting

Spec §5.4a. Independent; can be built and merged before or after B.

| # | Work |
|---|---|
| C1 | `RedisRateLimiter` bean (60/min, burst 10) + a client-IP `KeyResolver`, wired as a `RequestRateLimiter` filter on the search path of the `security-service` route. |
| C2 | **Verify fail-open on Redis failure** — do not assume the framework default. See R1. |
| C3 | `KeyResolver` must agree with the trusted-proxy regex (`application.yml:16-19`); see R2. |

**Validate**

```bash
./mvnw -pl pos-api-gateway -am test
# integration: hammer the path past the threshold → 429; stop Redis → requests still pass (fail open)
```

**Exit:** `429` past the threshold; with Redis down, login and search both still work; two clients
behind the load balancer get separate buckets.

---

### Stage D — `API Artifacts Sync` (the gate)

Only after A, B and C are pushed. Required by `CLAUDE.md`: controllers are the contract source and
the SDKs must not drift.

```bash
gh workflow run api-artifacts-sync.yml --repo louisburroughs/durion-positivity-backend \
  --ref feat/tenant-organization-login-search -f modules="pos-tenant pos-security-service"
```

**Exit:** the workflow opens a PR per repo; the backend spec/permission PR and the frontend SDK
tarball PR are both merged. Frontend work does not start before this lands — `@durion-sdk/*` is
refreshed into `node_modules` on every `start`/`build`/`test`
(`scripts/sdk/install-sdk-packages.mjs`), so a stale SDK fails the build with a confusing error.

---

### Stage E — frontend

Spec §6. **E1 needs no SDK and can start on day one.** E2–E4 wait for D.

| # | File | Work |
|---|---|---|
| E1 | `src/app/core/services/last-tenant.service.ts` *(new)* | `localStorage` key `durion.login.tenant`. Every access behind `isPlatformBrowser()` **and** `try/catch` — the app server-renders, and private mode throws. Written on successful login only. |
| E2 | `src/app/features/auth/login.component.{ts,html,css}` | Replace the `tenantSlug` input with the Organization combobox: 250 ms debounce, `switchMap` cancellation, subscription released in `onCleanup()`, selection required to submit. Submits `LoginRequest.tenantSlug` from the selected row — **the login contract does not change**. Keep the `hostTenantSlug()` branch and the slug-input fallback for a `404` from search. |
| E3 | `src/assets/i18n/{en-US,es-US,es-MX,fr-CA,fr-FR,qps-ploc}.json` | The eight `AUTH.LOGIN.ORGANIZATION*` keys. **Keep the `TENANT_SLUG*` keys** — the fallback still uses them. |
| E4 | `login.component.spec.ts`, `last-tenant.service.spec.ts` | Spec §7.4. |

**Validate**

```bash
npx ng test --include="src/app/features/auth/**/*.spec.ts" --no-watch
npx ng test --no-watch
npm run i18n:check
npm run a11y:smoke:strict
npm run build                     # SSR build must succeed — catches an unguarded localStorage
```

**Exit:** all five green; the combobox is keyboard-operable end to end; the SSR build passes.

---

### Stage F — ADR addendum

`durion/docs/adr/` — record spec §3.1: the tenant directory is public by design at the login edge,
bounded by the §5.3 controls, with search enabled in production. Off the critical path; do it
whenever, but before PR 1 merges so the decision is on record alongside the code.

---

## 4. Risk register

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | **Rate limiter fails closed.** A Redis blip returns `429` for every login attempt. | Medium | **Severe** — total login outage, strictly worse than the risk being mitigated | C2 verifies fail-open explicitly and a test covers it. The gateway already chose fail-open for revocation and disabled the Redis health indicator for exactly this reason (`application.yml:191-195`); match that stance. |
| R2 | **`KeyResolver` collapses all clients into one bucket.** `X-Forwarded-*` is stripped unless the peer matches the trusted-proxy regex, so every request may present the load balancer's IP. | **High** if written naively | Severe — 60 req/min for the entire platform | C3; test with and without a forwarded header, from a trusted and an untrusted peer. |
| R3 | **`display_name_key` never populated under `dev`/H2.** The `dev` profile disables Flyway and builds the schema from entities, so a DB-generated column would be absent and null, and the collision check would silently pass everything. | Was near-certain with the original design | High — broken seeding in the environment developers use | Already designed out: the application writes the key (spec §5.1). A3's tests run on H2 and assert the key is populated. |
| R4 | **Two normalizers drift.** `pos-tenant` (A3) and the `ext_tenant` listener (B2) each normalize; if they diverge, search silently misses tenants. | Medium | Medium — tenants unfindable at login, no error anywhere | Share the implementation, or duplicate with a test asserting agreement on a shared fixture of awkward inputs (NFKC forms, double spaces, mixed case, trailing space). |
| R5 | **De-duplication migration renames a live tenant.** A1 suffixes collisions on existing alpha data. | Low (small dataset) | Low — login is by slug, so a rename breaks no session or bookmark | Dump `tenant(id, slug, display_name)` before running; the migration logs every row it renames. |
| R6 | **Frontend built against a stale SDK.** | Medium | Low — loud build failure, not a silent bug | Stage D is a hard gate; E2 does not start until the SDK PR is merged. |
| R7 | **Enumeration through the search endpoint** (spec §3.1). | Accepted | Accepted | The §5.3 bounds; `auth.tenant-search.enabled` as an incident lever; Stage F records the decision. |

**Rollback.** Each stage reverts independently. A: drop the constraint and column (names revert to
non-unique, nothing else depends on them). B: `auth.tenant-search.enabled=false` turns the endpoint
off without a deploy and the frontend falls back to the slug input — this is the fastest lever and
needs no revert. C: remove the filter. E: revert the frontend PR; the backend is harmless on its own.

---

## 5. Definition of done

Spec §9 in full, plus:

1. `./mvnw -DskipTests=false clean test` green across the reactor.
2. `./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test` green.
3. `./mvnw -pl pos-tenant -am verify` green (Flyway + RLS integration tests).
4. `npx ng test --no-watch`, `npm run i18n:check`, `npm run a11y:smoke:strict`, `npm run build` green.
5. `API Artifacts Sync` run and its PRs merged.
6. ADR addendum merged.
7. Verified by hand on `docker`: register two tenants under one account with no display name →
   `Acme Tire & Auto LLC` and `Acme Tire & Auto LLC #2`; sign in by typing `acme` and picking one;
   reload the page and confirm the organization is pre-selected.

## 6. Sequencing note for a single implementer

Day 1: A1–A7 (the largest unit, and it gates nothing else — get it out of the way while fresh).
Day 2: B1–B7, then C1–C3. Push, open PR 1, trigger Stage D while review runs.
Day 3: E1 (no SDK needed) while D lands, then E2–E4. F at any point.

If two people are available, split **A** and **B+C** — different modules, different databases, no
shared files.
