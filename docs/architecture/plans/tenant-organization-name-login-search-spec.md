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
| D4 | Uniqueness | Display names are unique across the registry, because the user now identifies a tenant by one. The constraint lands **now**; see §4.5 for why. |
| D5 | Default value | Seeded from the owning **account's legal name** (not the trading name — §4.3), with a `#N` suffix only where that would collide. The seed is a fallback, not a final assignment: the operator is expected to set a meaningful name at registration. |
| D6 | "Stored so users don't look it up" | **Browser `localStorage` only.** No server-side per-user preference. A tenant-bearing `Host` still wins and renders the field read-only, as today. |
| D7 | Who may edit | **Platform operators only, for now.** Tenant self-service is deferred to the account-setup flow, which does not yet exist (§5.5). No new permissions are introduced by this change. |

## 3. Concerns to settle

### 3.1 The search endpoint is a tenant-enumeration surface (§5.3)

Today `LoginTenantResolver` answers an unknown slug, an inactive tenant and a wrong password with
the *same* `401 Invalid credentials`, specifically "so a caller cannot enumerate tenants through the
login route" (`LoginTenantResolver` javadoc). A public organization search endpoint hands out that
same information directly: it must be reachable before authentication, because it feeds the login
form.

This trade is **inherent to the feature, not incidental to it**: there is no version of "the user
picks their organization from a list" in which the list is also secret. The question is therefore
not whether to disclose the directory but how tightly to bound the disclosure. §5.3 specifies those
bounds — minimum query length, prefix-only matching (never an unanchored substring), a ten-result
cap, `ACTIVE`-only, a per-IP rate limit, no total count, and a response carrying nothing beyond the
two fields the form needs. The login route's own behaviour does **not** change: it keeps answering
the uniform 401, so nothing here makes it easier to confirm a *credential*.

**Search is enabled by default in every profile, production included.** An earlier draft of this
specification proposed defaulting it off in `prod`. That was wrong, and the reason is worth
recording so it is not re-proposed: it assumed production resolves the tenant from a per-tenant
hostname, leaving search a convenience. It does not. `auth.tenant-host-suffix` reads
`${AUTH_TENANT_HOST_SUFFIX:}` (`pos-api-gateway/src/main/resources/application.yml:246`) and that
variable is set in **no** profile, `.env.example` or compose file — blank disables host derivation,
so `hostTenantSlug()` is null everywhere and the tenant field always renders. Disabling search in
production would reduce it to today's type-the-slug login, which is the problem this change exists
to solve.

`auth.tenant-search.enabled` remains, with a narrower purpose:

* an **incident lever** — if the directory is scraped, it can be turned off without a deploy, and
  login still works for anyone who knows their slug (§6.1's fallback);
* a **per-deployment choice** for an installation that should not publish a directory at all.

**Revisit if** `AUTH_TENANT_HOST_SUFFIX` is ever configured. Once production serves tenants from
per-tenant hostnames, the field renders read-only from the `Host` header, search stops being the
login path, and turning it off in production becomes close to free.

**Recommended:** record the trade as an ADR-0062 addendum — "the tenant directory is public by
design at the login edge, bounded by the §5.3 controls."

### 3.2 Tenant self-service is deferred — recorded here so it is not rediscovered

Letting a tenant's own administrator rename their organization is **out of scope** for this change
(D7); it belongs to the account-setup flow when that is built. The analysis below is kept because
that work will hit both of these on day one, and neither is obvious from the outside.

`pos-tenant` is platform-only in two *independent* layers:

1. `PlatformTenantGuard`
   (`pos-tenant/src/main/java/com/positivity/tenant/internal/config/PlatformTenantGuard.java:70`)
   rejects any request bound to a non-platform tenant with `403 PLATFORM_TENANT_REQUIRED`, before
   any handler runs.
2. Every registry row's `tenant_id` **is** the platform tenant, under RLS. A request bound to tenant
   X reads `public.tenant` as **empty** and cannot update it — the guard is not the only thing in
   the way; the database is.

So a tenant administrator cannot be given self-service by granting a permission and pointing them at
the existing route. It requires a dedicated path exempt from the guard, whose service captures the
caller's bound tenant and then performs the registry read/write inside
`TenantContext.callAs(PlatformTenant.ID, ...)` **hard-scoped to that one captured id** — the same
"application code binds a tenant itself" exception ADR-0062 carves out for the platform tenant. That
is privilege-sensitive code and needs its own test set. **None of it is built by this
specification.**

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

### 4.3 Seed value: legal name, not trading name

`account` carries two names, and the choice between them is not cosmetic:

| | `legal_name` | `trading_name` |
|---|---|---|
| Meaning | "Registered legal name" (`AccountCreateRequest.java:25`) | "Trading name, when different" (`AccountCreateRequest.java:30`) — the DBA name |
| Example | `Acme Tire & Auto LLC` | `Acme Tire` |
| Required | **Yes**, `NOT NULL` | No, nullable |
| Unique | **Yes**, globally (`account_legal_name_key`, `V1__baseline_tenant.sql:106`) | **No constraint at all** (`V1__baseline_tenant.sql:21`) |

The trading name is the better *label* — it is the name on the sign above the shop, the one an
employee recognizes. It is the worse *seed*, for three reasons:

1. **It is usually absent.** It is only filled in "when different", so most accounts have none and
   the rule would fall back to the legal name anyway — producing a mixed population where some
   tenants are seeded from the DBA name and some from the LLC name, with no rule a user can predict.
2. **The legal name is the account's identity of record.** The tenant registry is an
   ownership/billing registry; seeding from the field that legally identifies the account is
   coherent, seeding from an optional marketing alias is not.
3. **Trading names collide; legal names cannot.** Two unrelated accounts may both trade as
   `Bob's Tires` and nothing stops them. Seeding from the trading name therefore *manufactures* the
   cross-account collision problem — the `Bob's Tires #3` whose `#1` and `#2` belong to strangers.
   Seeding from the legal name cannot produce that, because legal names are already unique: the only
   counter ever needed is for **one account's own** tenants, where the numbering is coherent because
   every number in the sequence belongs to the same account.

**Decision: seed from `legal_name`.** The trading name is not used by this rule.

### 4.4 Seeding and disambiguation

On `POST /platform/tenants` with `displayName` **omitted or blank**:

```
base = normalize-for-display(account.legal_name)   // NFKC + trim + collapse, original casing kept
candidate = base
n = 1
while candidate's key is taken:  n++; candidate = base + " #" + n     // max 50 attempts
```

* An account's first tenant gets the bare legal name; the second gets `Acme Tire & Auto LLC #2`, the
  third `#3`. The suffix reads as "the second one", so no number in an account's sequence is ever
  missing — which was the objection to a globally-allocated counter.
* `base` is truncated so `base + " #" + n` fits 200 characters.
* After 50 attempts the request fails `409 TENANT_DISPLAY_NAME_TAKEN` rather than looping.
* An explicitly supplied `displayName` is **never** auto-suffixed — it is validated and rejected on
  collision, so the operator sees exactly what they typed.

**The seed is a fallback, not the intended outcome.** A multi-tenant account almost never wants
ordinals; it wants something meaningful — `Acme Tire — Tucson`, `Acme Tire — Phoenix`. So the
operator registering a tenant is expected to supply `displayName`, and once a platform registration
screen exists it should **prefill the seed and let the operator edit it** rather than assign it
silently. Until such a screen exists, operators call the API with an explicit `displayName` and the
seed only catches the case where they do not.

**A single-tenant account never carries a suffix** (confirmed). `Acme Tire & Auto LLC`, not
`Acme Tire & Auto LLC #1` — `#1` is noise for the common case, and a number only appears once there
is something to disambiguate from.

### 4.5 What must be decided now, and what can wait

The naming **policy** and the uniqueness **invariant** have opposite migration costs, and separating
them is what keeps this change from painting us into a corner.

**Cheap to change at any time — do not over-think today:**

* the seed source, the suffix format, whether the first tenant is suffixed, and whether operators
  are *required* to supply a name rather than being offered a default.

`display_name` has no foreign keys, no external contract and no role in authentication — login
resolves by slug, which stays immutable. A rename propagates automatically through `tenant.updated`
on `tenant.events.v1`, and the three `ext_tenant` replicas (`pos-security-service`, `pos-catalog`,
`pos-image`) self-heal from it. Renaming tenants later is a UX decision, not a migration.

**Expensive to defer — decide now:**

* the **unique constraint** on `display_name_key`.

Adding it today is one line of DDL against an empty-to-tiny registry. Adding it after tenants exist
means renaming live tenants to break collisions that users have already learned. Dropping it later,
if enforced uniqueness turns out to be the wrong call, is free.

**So: land the constraint in this change, and leave the naming policy adjustable.**

### 4.6 Editability and propagation

* Display name is editable for any tenant not in a terminal status (unchanged from today's
  `TenantServiceImpl.update`), by a platform operator.
* A rename publishes `tenant.updated` on `tenant.events.v1`, which already carries `displayName` in
  its projection (`TenantProjectionEvent`), so every `ext_tenant` replica — including the one the
  search reads — converges with no new event type.
* Renames are not retroactive to anything: no other module stores the name, only the replica.
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
-- ... see migration body: a DO block appending ' #<n>' by created_at order ...

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
| `TenantCreateRequest.displayName` | Becomes **optional** (`requiredMode = NOT_REQUIRED`, drop `@NotBlank`, keep `@Size(max = 200)`). Omitted ⇒ §4.4 seed. The `@Schema` description states that the seed is a fallback and that an explicit, meaningful name is preferred. |
| `TenantDisplayNameAllocator` (new, `internal/service`) | Implements §4.1 and §4.4. Pure and unit-testable; takes the account's legal name and a "key is taken" predicate. |
| `TenantServiceImpl.create` | Allocates the seed when blank; normalizes; catches `tenant_display_name_key` violation ⇒ `DuplicateResourceException` with code `TENANT_DISPLAY_NAME_TAKEN` (mirrors the existing `isSlugCollision` path, `TenantServiceImpl.java:63`). |
| `TenantServiceImpl.update` | Same collision handling on rename. |
| `TenantResponse` | Unchanged shape; `displayName` now guaranteed non-null and unique. |
| `PlatformTenantController` | `@Operation` text updated: display name optional on create, seeding rule stated, new `409` documented. |

### 5.3 `pos-security-service` — public organization search

**Route:** `GET /security-service/v1/auth/tenants?q={query}` — under `auth.auth-path-prefix`, so the
gateway already bypasses JWT validation for it
(`pos-api-gateway/src/main/resources/application.yml:240`). No gateway route change; only the new
config keys in §5.4.

**Source:** the `ext_tenant` replica, which already holds `display_name` and `status`
(`V3__ext_tenant.sql:5`). New migration `V4__ext_tenant_display_name_search.sql` adds the same
generated `display_name_key` column plus
`CREATE INDEX idx_ext_tenant_display_name_key ON public.ext_tenant (display_name_key text_pattern_ops)`.

**Semantics:**

| Rule | Value |
|---|---|
| Minimum `q` length after normalization | **3** characters; shorter ⇒ `200` with an empty list (never an error — the field is typed into character by character) |
| Match | Prefix of the whole normalized name **or** prefix of any word in it (`key LIKE :q \|\| '%' OR key LIKE '% ' \|\| :q \|\| '%'`). Never an unanchored substring. |
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
| `auth.tenant-search.enabled` | **`true` in every profile, production included** (see §3.1); an incident lever, not a production default | `pos-security-service` |
| `auth.tenant-search.min-query-length` | `3` | `pos-security-service` |
| `auth.tenant-search.max-results` | `10` | `pos-security-service` |
| `auth.tenant-search.rate-limit-per-minute` | `60` | `pos-security-service` |

The frontend learns whether search is available by calling it: a `404` switches the form to the
existing slug input. No new bootstrap/config endpoint.

### 5.5 Tenant self-service — deferred

Not built here. Renaming an organization from inside a tenant belongs to the **account-setup flow**,
which does not yet exist. This change introduces **no new permissions**: `tenant:profile:read` /
`tenant:profile:update` are not added, `TenantPermissionRegistration` is unchanged, and
`R__seed_tenant_template.sql` is not touched.

Two consequences worth stating so nothing is built on a wrong assumption:

* `PlatformTenantGuard` keeps its current blanket rule — **no path in `pos-tenant` is exempt**. The
  module stays platform-callers-only exactly as ADR-0062 §7 describes.
* Whoever builds account setup must read §3.2 first; the guard and RLS both stand in the way, and the
  mechanism needed there is privilege-sensitive.

### 5.6 Platform route — the only edit path

`PATCH /tenant/v1/platform/tenants/{id}` is unchanged in shape and remains the sole way to rename a
tenant. It gains the `409 TENANT_DISPLAY_NAME_TAKEN` response and updated `@Operation` text.

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

### 6.4 No tenant-admin settings screen

Deferred with §5.5. There is no in-tenant organization-name screen in this change; renaming is a
platform-operator action only.

---

## 7. Testing

### 7.1 `pos-tenant` unit
* `TenantDisplayNameAllocator`: an account's first tenant gets the bare legal name; the second gets
  ` #2`; a taken candidate skips to ` #3`; base truncation keeps the result ≤ 200 chars; the
  50-attempt ceiling throws.
* The allocator reads `legal_name` and **never** `trading_name`, including when a trading name is
  present and differs (§4.3).
* Normalization: NFKC, trim, internal-whitespace collapse, case-fold; `Acme  Tire` ≡ `acme tire`.
* `TenantServiceImpl.create`: blank/absent `displayName` seeds; an explicit value is never
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
* Search reachable without a token; rate limit answers `429` with an `ApiError`; search is enabled
  by default in every profile, and `auth.tenant-search.enabled=false` ⇒ `404` (the incident lever).
* `LoginTenantResolver` behaviour is **unchanged** — the existing uniform-401 tests must still pass
  untouched.

### 7.4 Frontend
* Combobox: debounce, `switchMap` cancellation, subscription cleaned up in `onCleanup()`.
* Submit is blocked with free text and no selection; submit sends the selected slug.
* `404` from search ⇒ the slug input renders and still logs in.
* `localStorage`: written on success only; not written on a failed login; pre-selects on reload;
  "use a different organization" clears it; a throwing `localStorage` (private mode) still renders.
* SSR: the login route renders server-side with no `localStorage` access.
* A tenant-bearing host overrides a remembered value.
* `npm run i18n:check`, `npm run a11y:smoke:strict`, `npx ng test --no-watch`.

### 7.5 Boundary regression
* Every `pos-tenant` path still answers `403 PLATFORM_TENANT_REQUIRED` for a non-platform caller —
  no allowlist, no exemption, no new permission (§5.5).
* `TenancyArchitectureTest` and the module `ArchitectureTest` pass.

---

## 8. Work breakdown

| # | Work | Repo / module | Depends on |
|---|---|---|---|
| 1 | `V3__tenant_display_name_key.sql` + entity mapping + de-dup | backend / `pos-tenant` | — |
| 2 | `TenantDisplayNameAllocator`, create/update seeding and collision handling | backend / `pos-tenant` | 1 |
| 3 | `TenantCreateRequest.displayName` optional; OpenAPI text; 409 responses | backend / `pos-tenant` | 2 |
| 4 | `V4__ext_tenant_display_name_search.sql` + repository query | backend / `pos-security-service` | — |
| 5 | Public search endpoint, config keys, rate limit, event type | backend / `pos-security-service` | 4 |
| 6 | **Run `API Artifacts Sync`** (regenerates specs, permission manifests, both SDKs, frontend tarballs) | backend workflow | 3, 5 |
| 7 | Login combobox + fallback + a11y | frontend | 6 |
| 8 | `last-tenant.service.ts` + SSR guards | frontend | — |
| 9 | i18n keys in all six locale files | frontend | 7 |
| 10 | ADR-0062 addendum recording §3.1 (public tenant directory at the login edge) | `durion` / `docs/adr` | 5 |

Steps 3 and 5 change controllers and DTOs, so step 6 is **mandatory** before any frontend work
begins — per `CLAUDE.md`, the controller is the contract source and the SDK must not drift.

## 9. Acceptance criteria

1. Registering a tenant without a display name yields the owning account's **legal** name; a second
   tenant on the same account yields `<legal name> #2`. The trading name is never used.
2. Two tenants cannot hold the same display name, differing only in case or internal whitespace;
   the attempt answers `409 TENANT_DISPLAY_NAME_TAKEN`.
3. On a host with no tenant suffix, the login form shows an **Organization** search field; typing
   three characters lists up to ten matching active organizations; picking one and entering valid
   credentials signs the user in.
4. The next visit to the login page on the same browser pre-selects the last organization signed in
   with, and the user can clear it.
5. A platform operator can rename any tenant, and the rename is reflected in the login search within
   one `tenant.events.v1` round trip. No in-tenant caller can reach any `pos-tenant` route.
6. The login endpoint still answers one indistinguishable `401` for a wrong password, an unknown
   tenant and an inactive tenant.
7. `./mvnw -DskipTests=false clean test`, `./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test`,
   `npx ng test --no-watch`, `npm run i18n:check` and `npm run a11y:smoke:strict` all pass.

## 10. Out of scope

* Renaming the column or the API field to `organization_name` (D1).
* **Tenant self-service renaming and the permissions it would need** — deferred to the account-setup
  flow (D7, §5.5); the obstacles it will face are recorded in §3.2.
* Server-side per-user tenant memory (D6) — circular at login: the tenant must be resolved before
  the user can be looked up.
* Fuzzy/typo-tolerant matching, trigram or full-text search. Prefix matching first; revisit if the
  registry grows past a few thousand tenants.
* Changing the slug's role as the API identifier, or making it mutable.
* Per-tenant login branding (logo, colours) — a natural follow-on, not this change.
