# Specification — Organization name on the login form

**Status:** Draft for review
**Date:** 2026-09-12
**Owner:** Platform / Tenancy
**Affects:** `pos-tenant`, `pos-security-service`, `pos-api-gateway` (config only),
`durion-positivity-frontend`, both SDKs
**Relates to:** ADR-0062 §3, §7 (multitenancy), ADR-0025/ADR-0026 (permissions, module boundaries),
`docs/architecture/plans/adr-0023-suppression-postgres-multitenancy-plan.md` (WS2a/WS2b)

---

## 1. Problem

When the sign-in host carries no tenant suffix, the login form asks the user to type the tenant
**slug** (`acme-tire`). A slug is an infrastructure identifier: users do not know it, cannot guess
it, and have to look it up every time they sign in from a device the browser has not seen before.

We want the user to pick their organization by its human-readable name, from a search field, and we
want the browser to remember the choice.

## 2. Decisions taken (from requirements review)

| # | Question | Decision |
|---|---|---|
| D1 | New `organization_name` column? | **No.** The existing `tenant.display_name` is the human-readable name. No schema rename, no second column. The name `organization_name` does not enter the codebase. |
| D2 | What the user sees | The login form labels the field **"Organization"**. The label is a translation string only; the API field stays `displayName` everywhere. |
| D3 | Login input | The **search field replaces the typed slug**. The user types part of the organization name, picks a match from a result list, and the form submits the matched tenant. |
| D4 | Uniqueness | Display names must be unique across the registry, because the user now identifies a tenant by one. Where a collision would occur, a short disambiguating suffix is appended (`Acme Tire & Auto - 2`). |
| D5 | Default value | On tenant registration the display name defaults to the **owning account's legal name**, with the ordinal suffix from D4 when the account already owns a tenant. |
| D6 | "Stored so users don't look it up" | **Browser `localStorage` only.** No server-side per-user preference. A tenant-bearing `Host` still wins and renders the field read-only, as today. |
| D7 | Who may edit | **Both.** Platform operators edit any tenant via the existing platform route; tenant `ADMIN` and `SYSTEM_ADMINISTRATOR` edit their **own** tenant via a new self-service route. |

## 3. Concerns to settle before build

Two items in this design cut against invariants that ADR-0062 currently states. Neither blocks the
work, but both are decisions the team should take deliberately rather than discover in review.

### 3.1 The search endpoint is a tenant-enumeration surface (§5.3)

Today `LoginTenantResolver` answers an unknown slug, an inactive tenant and a wrong password with
the *same* `401 Invalid credentials`, specifically "so a caller cannot enumerate tenants through the
login route" (`LoginTenantResolver` javadoc). A public organization search endpoint hands out that
same information directly: it must be reachable before authentication, because it feeds the login
form.

This is an accepted, deliberate trade — the feature is not buildable without it — but it should be
recorded as such. §5.3 specifies the controls that bound the exposure (minimum query length,
prefix-only matching, result cap, `ACTIVE`-only, rate limit, per-environment kill switch, and no
field in the response beyond what the form needs). The login route's own behaviour does **not**
change: it keeps answering the uniform 401.

**Recommended:** treat this as an ADR-0062 addendum ("the tenant directory is public by
design at the login edge"), and default `auth.tenant-search.enabled` to **off** in `prod` until
someone owns that decision for production, leaving it on in `dev`/`docker`/`alpha`.

### 3.2 Tenant-admin self-service collides with two pos-tenant invariants

`pos-tenant` is platform-only in two independent layers:

1. `PlatformTenantGuard` (`pos-tenant/src/main/java/com/positivity/tenant/internal/config/PlatformTenantGuard.java:70`)
   rejects any request bound to a non-platform tenant with `403 PLATFORM_TENANT_REQUIRED`, before
   any handler runs.
2. Every registry row's `tenant_id` **is** the platform tenant, under RLS. A request bound to tenant
   X reads `public.tenant` as **empty** and cannot update it — the guard is not the only thing in
   the way; the database is.

So a tenant admin cannot simply be granted a permission and pointed at the existing route. §5.5
specifies the narrow mechanism: a single dedicated path, exempt from the guard, whose service method
captures the caller's bound tenant, then performs the registry read/write inside
`TenantContext.callAs(PlatformTenant.ID, ...)` **hard-scoped to that one captured id**. This is the
same "application code binds a tenant itself" exception ADR-0062 already carves out for the platform
tenant, and it is the only privilege-sensitive code in this specification — §7.5 lists the tests
that must defend it.

---

## 4. Domain rules

### 4.1 Display name normalization

A single normalization function governs uniqueness, matching and search. Normalized form
(`display_name_key`) is:

1. Unicode NFKC;
2. trim leading/trailing whitespace;
3. collapse every internal whitespace run to one space;
4. case-fold (`lower`).

`Acme  Tire & Auto ` and `acme tire & auto` are therefore the same name. The **display** value keeps
the operator's original casing and spacing; only the key is normalized.

### 4.2 Uniqueness

`display_name_key` is unique across the tenant registry. Because every registry row belongs to the
platform tenant, the tenancy-schema convention `UNIQUE (tenant_id, <cols>)` yields global uniqueness
for free — no exception to `docs/TENANCY_SCHEMA.md` is needed.

A create or update that would collide answers `409` with code `TENANT_DISPLAY_NAME_TAKEN`.

### 4.3 Default and disambiguation

On `POST /platform/tenants` with `displayName` **omitted or blank**:

```
base = normalize-for-display(account.legal_name)      // NFKC + trim + collapse, original casing kept
n    = (count of tenants already registered under this account, any status) + 1
candidate = n == 1 ? base : base + " - " + n
while candidate's key is taken:  n++; candidate = base + " - " + n     // max 50 attempts
```

* `account.legal_name` is already unique across the registry
  (`account_legal_name_key UNIQUE (tenant_id, legal_name)`, `V1__baseline_tenant.sql:106`), so the
  first tenant of an account never needs a suffix and the loop is a guard against *edited* names
  colliding, not against the common path.
* `base` is truncated so `base + " - " + n` fits 200 characters.
* After 50 attempts the request fails `409 TENANT_DISPLAY_NAME_TAKEN` rather than looping.

> **Assumption to confirm.** The first tenant of an account gets the bare account name
> (`Acme Tire & Auto`) and only the second onward is suffixed (`Acme Tire & Auto - 2`). The
> requirement's example (`"Display Name - Acct 1"`) could also be read as suffixing *every* tenant
> including the first. The unsuffixed-first reading is specified here because it is the better
> default for the single-tenant account, which is the common case. Changing it is a one-line change
> in `TenantDisplayNameAllocator`.

An explicitly supplied `displayName` is **never** auto-suffixed — it is validated and rejected on
collision, so the operator sees what they typed.

### 4.4 Editability and propagation

* Display name is editable for any tenant not in a terminal status (unchanged from today's
  `TenantServiceImpl.update`).
* A rename publishes `tenant.updated` on `tenant.events.v1`, which already carries `displayName` in
  its projection (`TenantProjectionEvent`), so every module's `ext_tenant` replica — including the
  one the search reads — converges with no new event type.
* Renames are **not** retroactive to anything: no other module stores the name, only the replica.
* The slug is still immutable and still the identifier the login API takes.

---

## 5. Backend changes

### 5.1 `pos-tenant` — schema

New migration `V3__tenant_display_name_key.sql`:

```sql
ALTER TABLE public.tenant
    ADD COLUMN display_name_key character varying(200)
        GENERATED ALWAYS AS (lower(btrim(regexp_replace(display_name, '\s+', ' ', 'g')))) STORED;

-- de-duplicate pre-existing rows before the constraint lands (alpha data; suffix the later row)
-- ... see migration body: a DO block appending ' - <n>' by created_at order ...

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_display_name_key UNIQUE (tenant_id, display_name_key);
```

The generated column covers steps 2–4 of §4.1 in the database. NFKC (step 1) is applied by the
application before write; the generated column is the backstop that makes the constraint
unbypassable, not the normalizer of record.

`TenantEntity` maps `displayNameKey` as `insertable = false, updatable = false`.

### 5.2 `pos-tenant` — service and API

| Change | Detail |
|---|---|
| `TenantCreateRequest.displayName` | Becomes **optional** (`requiredMode = NOT_REQUIRED`, drop `@NotBlank`, keep `@Size(max = 200)`). Omitted ⇒ §4.3 default. |
| `TenantDisplayNameAllocator` (new, `internal/service`) | Implements §4.1 and §4.3. Pure and unit-testable; takes the account, the tenant count and a "key is taken" predicate. |
| `TenantServiceImpl.create` | Allocates the default when blank; normalizes; catches `tenant_display_name_key` violation ⇒ `DuplicateResourceException` with code `TENANT_DISPLAY_NAME_TAKEN` (mirrors the existing `isSlugCollision` path, `TenantServiceImpl.java:63`). |
| `TenantServiceImpl.update` | Same collision handling on rename. |
| `TenantResponse` | Unchanged shape; `displayName` now guaranteed non-null and unique. |
| `PlatformTenantController` | `@Operation` text updated: display name optional on create, defaulting rule stated, new `409` documented. |

### 5.3 `pos-security-service` — public organization search

**Route:** `GET /security-service/v1/auth/tenants?q={query}` — under `auth.auth-path-prefix`, so the
gateway already bypasses JWT validation for it (`pos-api-gateway/src/main/resources/application.yml:240`).
No gateway route change; only the new config keys in §5.4.

**Source:** the `ext_tenant` replica, which already holds `display_name` and `status`
(`V3__ext_tenant.sql:5`). New migration `V4__ext_tenant_display_name_search.sql` adds the same
generated `display_name_key` column plus
`CREATE INDEX idx_ext_tenant_display_name_key ON public.ext_tenant (display_name_key text_pattern_ops)`.

**Semantics:**

| Rule | Value |
|---|---|
| Minimum `q` length after normalization | **3** characters; shorter ⇒ `200` with an empty list (never an error — the field is typed into character by character) |
| Match | Prefix of the whole normalized name **or** prefix of any word in it (`key LIKE :q || '%' OR key LIKE '% ' || :q || '%'`). Never an unanchored substring. |
| Status filter | `ACTIVE` only |
| Result cap | **10**, ordered by name length then name (shortest, most exact first) |
| Response item | `{ "slug": "acme-tire", "displayName": "Acme Tire & Auto" }` — nothing else. No tenant id, no status, no account, no counts. |
| Total count | **Not returned.** A total would tell a caller how much is left to enumerate. |
| Rate limit | Per client IP, `429` with the standard `ApiError` on breach. Reuse the existing login-throttle infrastructure rather than adding a second mechanism. |
| Kill switch | `auth.tenant-search.enabled` (see §5.4). Disabled ⇒ `404`, and the frontend falls back to the slug field. |
| Auditing | `@EmitEvent(id = "SECURITY_TENANT_SEARCH", apiVersion = "1")`, threshold preset `search`, registered in the security module's event-type registry. |

The endpoint is `@PreAuthorize("permitAll()")`, like `login` (`AuthController.java:83`).

**New types:** `TenantSearchController` (or a method on `AuthController`), `TenantSearchResponse`
record, `ExtTenantRepository.searchActiveByDisplayNameKey(...)`.

### 5.4 Configuration

| Key | Default | Where |
|---|---|---|
| `auth.tenant-search.enabled` | `true` in `dev`/`docker`/`alpha`, **`false` in `prod`** (see §3.1) | `pos-security-service` |
| `auth.tenant-search.min-query-length` | `3` | `pos-security-service` |
| `auth.tenant-search.max-results` | `10` | `pos-security-service` |
| `auth.tenant-search.rate-limit-per-minute` | `60` | `pos-security-service` |

The frontend learns whether search is available by calling it: a `404` switches the form to the
existing slug input. No new bootstrap/config endpoint.

### 5.5 `pos-tenant` — tenant-admin self-service

**Route:** `PATCH /tenant/v1/tenants/current`, body `{ "displayName": "..." }`.
**Read companion:** `GET /tenant/v1/tenants/current` (so the admin screen can show the current
value) returning `{ tenantId, slug, displayName }` — slug included because it is not a secret from
a session already bound to that tenant.

**Permissions** (new, in `TenantPermissions`; tenant-scoped, *not* `platform:*`):

```java
public static final String TENANT_PROFILE_READ   = "tenant:profile:read";
public static final String TENANT_PROFILE_UPDATE = "tenant:profile:update";
```

Registered through the existing `TenantPermissionRegistration`, and granted to the `ADMIN` and
`SYSTEM_ADMINISTRATOR` template roles in
`pos-security-service/src/main/resources/db/migration/R__seed_tenant_template.sql`. Because that
file is a repeatable migration that fans template grants out to existing tenants
(`R__seed_tenant_template.sql:97`, the platform-template fan-out block), already-provisioned tenants pick the grants up on the next
startup — no backfill script.

**Guard exemption.** `PlatformTenantFilter` gains an exact-path allowlist containing
`/v1/tenants/current` only (not a prefix, not a wildcard). Every other path in the module stays
platform-only.

**Tenant binding.** `TenantSelfServiceImpl`:

```java
UUID caller = TenantContext.current().orElseThrow(...);        // bound by TenantContextFilter from X-Tenant-Id
if (PlatformTenant.isPlatform(caller)) throw ...;              // platform sessions use the platform route
return TenantContext.callAs(PlatformTenant.ID, () -> {
    TenantEntity t = tenantRepository.findById(caller).orElseThrow(...);   // id is the captured caller, never a parameter
    ... apply displayName, collision check, save, factPublisher.tenantUpdated(t) ...
});
```

Non-negotiable properties, each with a test in §7.5:

* the tenant id operated on comes **only** from `TenantContext`, never from the path, body or a
  header — there is no parameter that could name another tenant;
* the elevated scope wraps the smallest possible block and is always unwound (`callAs`, not manual
  `set`/`clear`);
* only `displayName` is writable here — `cell`, `slug`, `accountId`, `status` and
  `initialAdminEmail` are not in the request DTO at all;
* `@EmitEvent(id = "TENANT_SELF_UPDATE", apiVersion = "1")`, preset `write`, added to
  `TenantEventTypes`;
* the same `409 TENANT_DISPLAY_NAME_TAKEN` and terminal-status rules as the platform route.

### 5.6 Platform route

`PATCH /tenant/v1/platform/tenants/{id}` is unchanged in shape. It gains the `409` collision
response and updated `@Operation` text.

---

## 6. Frontend changes (`durion-positivity-frontend`)

### 6.1 Login form

`src/app/features/auth/login.component.{ts,html,css,spec.ts}`:

* The `tenantSlug` text input is replaced by an **Organization** combobox: a text input the user
  types into, plus a results list they pick from. The form control holds the *selected*
  `{ slug, displayName }`; free text with nothing selected does not submit.
* Debounce **250 ms**; do not query below the 3-character minimum; cancel the in-flight request on
  each keystroke (`switchMap`), and drop the subscription in `onCleanup()` per the AGENTS.md effect
  rules.
* The submitted payload is unchanged: `LoginRequest.tenantSlug` carries the selected tenant's slug.
  **The login API contract does not change.**
* The `hostTenantSlug()` branch is untouched: a tenant-bearing host still renders the read-only
  tenant display and sends no slug.
* If the search endpoint answers `404` (disabled per §5.4), the component falls back to today's slug
  input, validators and hint — the existing code path stays in place rather than being deleted.
* Error handling keeps the current shape: search failures degrade to "no matches" and never surface
  a distinct message that would confirm a tenant's existence or absence.

**Accessibility.** The control is a real combobox: `role="combobox"` with `aria-expanded`,
`aria-controls`, `aria-activedescendant`; the list is `role="listbox"` with `role="option"` items;
Up/Down/Enter/Escape are handled; the result count is announced through an `aria-live="polite"`
region. This must pass `npm run a11y:smoke:strict`.

### 6.2 Remembering the choice

New `src/app/core/services/last-tenant.service.ts` (or a small helper beside
`core/security/tenant.ts`):

* Key `durion.login.tenant`, value `{ "slug": "...", "displayName": "...", "savedAt": "<ISO>" }`.
* Written on **successful** login only — never on a failed attempt, so a typo is not remembered.
* Read on login-page init to pre-select the field; the user can clear it with a
  **"Use a different organization"** action, which removes the key and empties the field.
* Precedence: `hostTenantSlug()` > remembered value > empty.
* Survives sign-out (that is the point of the feature).
* **SSR-safe**: the app server-renders via `@angular/ssr`, so every access is behind
  `isPlatformBrowser(...)` and wrapped in `try/catch` — private mode and blocked site data must
  degrade to "nothing remembered", not to a render error.
* Stores no credentials, no username, no token. The organization name is the only thing persisted.

### 6.3 i18n

New keys under `AUTH.LOGIN`, added to **every** file in `src/assets/i18n/`
(`en-US`, `es-US`, `es-MX`, `fr-CA`, `fr-FR`, `qps-ploc`):

| Key | en-US |
|---|---|
| `ORGANIZATION` | `Organization` |
| `ORGANIZATION_PLACEHOLDER` | `Start typing your organization's name` |
| `ORGANIZATION_HINT` | `Type at least 3 characters, then choose your organization.` |
| `ORGANIZATION_ERROR` | `Choose your organization from the list.` |
| `ORGANIZATION_NO_MATCHES` | `No organizations match that name.` |
| `ORGANIZATION_SEARCHING` | `Searching…` |
| `ORGANIZATION_RESULTS_COUNT` | `{{count}} organizations found` |
| `ORGANIZATION_CHANGE` | `Use a different organization` |

`TENANT_SLUG*` keys are **retained** — the fallback path in §6.1 still uses them. `npm run i18n:check`
must pass.

### 6.4 Tenant-admin settings screen

A small form exposing `GET`/`PATCH /tenant/v1/tenants/current`, visible to holders of
`tenant:profile:update`, placed with the other tenant-level administration screens. Two-signal state
machine, `state.set('error')` before `errorKey.set(...)`, co-located `*.service.spec.ts` — the
standard page conventions in `AGENTS.md`.

---

## 7. Testing

### 7.1 `pos-tenant` unit
* `TenantDisplayNameAllocator`: first tenant unsuffixed; second gets ` - 2`; a taken candidate skips
  to ` - 3`; base truncation keeps the result ≤ 200 chars; the 50-attempt ceiling throws.
* Normalization: NFKC, trim, internal-whitespace collapse, case-fold; `Acme  Tire` ≡ `acme tire`.
* `TenantServiceImpl.create`: blank/absent `displayName` defaults; explicit value is never
  auto-suffixed; collision ⇒ `TENANT_DISPLAY_NAME_TAKEN`.
* `TenantServiceImpl.update`: rename collision ⇒ 409; unchanged name ⇒ no event published (the
  existing `changed` guard, `TenantServiceImpl.java:98`).

### 7.2 `pos-tenant` integration (`*IT`)
* The unique constraint rejects a colliding name at the database, not only in the service.
* The de-duplication step of `V3__tenant_display_name_key.sql` leaves the constraint satisfiable on
  a fixture with three identically named tenants.
* `TenancySchemaConformanceIT` still passes with the generated column present.

### 7.3 `pos-security-service`
* Search: below the minimum length ⇒ empty list, no query; word-prefix and whole-prefix both match;
  unanchored substring does **not** match; `PENDING`/`SUSPENDED`/`DECOMMISSIONED` excluded; results
  capped at 10; response carries exactly `slug` + `displayName`.
* Search reachable without a token; rate limit answers `429` with an `ApiError`;
  `auth.tenant-search.enabled=false` ⇒ `404`.
* `LoginTenantResolver` behaviour is **unchanged** — the existing uniform-401 tests must still pass
  untouched.
* `R__seed_tenant_template.sql` grants `tenant:profile:*` to `ADMIN` and `SYSTEM_ADMINISTRATOR` and
  to no other template role, and to no role in the platform tenant's `platform:*` families.

### 7.4 Frontend
* Combobox: debounce, `switchMap` cancellation, subscription cleaned up in `onCleanup()`.
* Submit is blocked with free text and no selection; submit sends the selected slug.
* `404` from search ⇒ the slug input renders and still logs in.
* `localStorage`: written on success only; not written on a failed login; pre-selects on reload;
  "use a different organization" clears it; a throwing `localStorage` (private mode) still renders.
* SSR: the login route renders server-side with no `localStorage` access.
* A tenant-bearing host overrides a remembered value.
* `npm run i18n:check`, `npm run a11y:smoke:strict`, `npx ng test --no-watch`.

### 7.5 Security tests (§3.2 — these are the ones that matter)
* A session bound to tenant X calling `PATCH /v1/tenants/current` updates **only** tenant X.
* No request shape — path, query, body or header — makes the self-service route touch another
  tenant's row. Assert by construction: the DTO has one field and the repository lookup takes the
  captured id.
* A caller without `tenant:profile:update` gets `403`.
* A platform-bound session is refused on the self-service route (it has the platform route).
* After `callAs` returns, `TenantContext.current()` is back to the caller's tenant — including on the
  exception path.
* Every other `pos-tenant` path still answers `403 PLATFORM_TENANT_REQUIRED` for a non-platform
  caller; the allowlist is exact-match and does not admit `/v1/tenants/current/anything` or
  `/v1/tenants/currentX`.
* `TenancyArchitectureTest` and the module `ArchitectureTest` pass.

---

## 8. Work breakdown

| # | Work | Repo / module | Depends on |
|---|---|---|---|
| 1 | `V3__tenant_display_name_key.sql` + entity mapping + de-dup | backend / `pos-tenant` | — |
| 2 | `TenantDisplayNameAllocator`, create/update defaulting and collision handling | backend / `pos-tenant` | 1 |
| 3 | `TenantCreateRequest.displayName` optional; OpenAPI text; 409 responses | backend / `pos-tenant` | 2 |
| 4 | `V4__ext_tenant_display_name_search.sql` + repository query | backend / `pos-security-service` | — |
| 5 | Public search endpoint, config keys, rate limit, event type | backend / `pos-security-service` | 4 |
| 6 | `tenant:profile:*` permissions + template grants | backend / both modules | — |
| 7 | Self-service `GET`/`PATCH /v1/tenants/current`, guard allowlist, `callAs` scoping | backend / `pos-tenant` | 2, 6 |
| 8 | **Run `API Artifacts Sync`** (regenerates specs, permission manifests, both SDKs, frontend tarballs) | backend workflow | 3, 5, 7 |
| 9 | Login combobox + fallback + a11y | frontend | 8 |
| 10 | `last-tenant.service.ts` + SSR guards | frontend | — |
| 11 | i18n keys in all six locale files | frontend | 9 |
| 12 | Tenant-admin settings screen | frontend | 8 |
| 13 | ADR-0062 addendum recording §3.1 and §3.2 | `durion` / `docs/adr` | 5, 7 |

Steps 3, 5 and 7 all change controllers, DTOs and permissions, so step 8 is **mandatory** before any
frontend work begins — per `CLAUDE.md`, the controller is the contract source and the SDK must not
drift.

## 9. Acceptance criteria

1. Registering a tenant without a display name yields the account's legal name; a second tenant on
   the same account yields `<legal name> - 2`.
2. Two tenants cannot hold the same display name, differing only in case or internal whitespace;
   the attempt answers `409 TENANT_DISPLAY_NAME_TAKEN`.
3. On a host with no tenant suffix, the login form shows an **Organization** search field; typing
   three characters lists up to ten matching active organizations; picking one and entering valid
   credentials signs the user in.
4. The next visit to the login page on the same browser pre-selects the last organization signed in
   with, and the user can clear it.
5. A tenant `ADMIN` or `SYSTEM_ADMINISTRATOR` can rename their own organization and cannot reach any
   other tenant's record; a platform operator can rename any tenant.
6. A rename is reflected in the login search within one `tenant.events.v1` round trip.
7. The login endpoint still answers one indistinguishable `401` for a wrong password, an unknown
   tenant and an inactive tenant.
8. `./mvnw -DskipTests=false clean test`, `./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test`,
   `npx ng test --no-watch`, `npm run i18n:check` and `npm run a11y:smoke:strict` all pass.

## 10. Out of scope

* Renaming the column or the API field to `organization_name` (D1).
* Server-side per-user tenant memory (D6) — circular at login: the tenant must be resolved before
  the user can be looked up.
* Fuzzy/typo-tolerant matching, trigram or full-text search. Prefix matching first; revisit if the
  registry grows past a few thousand tenants.
* Changing the slug's role as the API identifier, or making it mutable.
* Per-tenant login branding (logo, colours) — a natural follow-on, not this change.
