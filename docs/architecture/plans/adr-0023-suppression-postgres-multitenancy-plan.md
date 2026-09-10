---
title: 'ADR-0023 Suppression: Postgres Row-Level Multitenancy Plan'
created: 2026-09-07
updated: 2026-09-10
status: active
---

## ADR-0023 Suppression: Postgres Row-Level Multitenancy Plan

**Version:** 0.3 **Status:** Active (ADR-0062 accepted 2026-09-09) **Last Updated:** 2026-09-10
**Related:** [ADR-0062](../../adr/0062-postgres-row-level-multitenancy.adr.md) (proposed, supersedes ADR-0023),
ADR-0023 Remove `tenantId` (to be superseded), ADR-0011, ADR-0013, ADR-0024, ADR-0040, ADR-0044, ADR-0045, ADR-0061,
Foundation-First Tenant Cell Deployment Architecture

**v0.3 (2026-09-09):** the tenant registry moves to a new `pos-tenant` module that also owns the `account`
(contacts, billing profile) behind each tenancy, with `pos-security-service` as a consumer; WS2 splits into WS2a and
WS2b; the owning customer is an `account`, never an "organization". Also corrections from a verification pass against Hibernate 7.4.5 and the current code: superseding
ADR renumbered to ADR-0062 (0061 was taken the same day by location scope), RLS policy cast fixed, superuser-owner and
managed-Postgres note, verified `@TenantId` semantics, `X-Tenant-*` added to the gateway strip list, composite foreign
keys made the default, Spring cache scoping, **roles are tenant-scoped** (decision taken 2026-09-09), and the footprint
re-measured.

---

## Purpose

ADR-0023 (ACCEPTED, 2026-02-21) removed `tenantId` from every contract and declared the platform single-organization.
This document describes what it takes to suppress that decision and make Durion a high-volume multitenant platform on
Postgres, with tenancy enforced inside the application and the database rather than by deploying one stack per customer.

It answers three questions:

1. What does the platform look like today, measured against the code that actually exists?
2. What is the recommended target model, and why?
3. What is the work, per repository and per workstream, and roughly how much effort is it?

The estimate assumes the constraints given for this assessment: the platform is in alpha, there is no production data,
and every database can be dropped and recreated between builds. No backfill, dual-write, or compatibility bridge is
planned anywhere in this document.

---

## Governance Conflict To Resolve First

Suppressing ADR-0023 is not only an ADR edit. Two later documents build on it and lock in a *different* answer to
multitenancy than the one this plan proposes:

| Document | What it locks in | Conflict with this plan |
| --- | --- | --- |
| [Foundation-First Tenant Cell Deployment Architecture](../deployment/FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md) | "Durion is a per-organization isolated deployment model" and "deliberately avoids a shared multi-tenant database model" | Direct contradiction of a locked decision |
| [ADR-0045](../../adr/0045-autonomous-environment-lifecycle-management.adr.md) | Lifecycle automation executed per tenant cell | Compatible, but its cost model assumes one cell per customer |
| `.ai/GLOSSARY.md`, `domains/security/security-questions.md` (DECISION-INVENTORY-008), `knowledge-catalog/adr/` | `tenantId` is "deprecated, not implemented" | Wording only |

The recommended resolution is not to discard the tenant-cell architecture but to reframe it:

- A **tenant cell** becomes a runtime that hosts one *or more* tenants. A pooled cell serves many small customers on one
  Postgres instance; a dedicated cell serves one customer who needs physical isolation. Both run the same images.
- The **unit of isolation** moves from the deployment boundary to the `tenant_id` column plus Postgres row-level
  security (RLS). Dedicated cells then become a premium/regulatory option instead of the only option.

This must be recorded as a new ADR that supersedes ADR-0023 and amends the tenant-cell document and ADR-0045.
That ADR is [ADR-0062](../../adr/0062-postgres-row-level-multitenancy.adr.md), ACCEPTED 2026-09-09; Appendix A keeps
the outline it was written from. WS0 (amending the tenant-cell document, ADR-0045, and the other rows of ADR-0062's
"Amends" table) is now the first task, because the choice between pooled and dedicated cells changes the operations,
backup, and provisioning work.

---

## Current State (Measured 2026-09-07)

Figures are from a fresh checkout of `durion-positivity-backend`, `durion-positivity-frontend`, and
`durion-positivity-sdk-angular` on their default branches.

**Re-measured 2026-09-09** (same method where the method is stated; two days of merges): `@Entity` classes 474 to 493,
`@KafkaListener` methods 108 to 116, `@Scheduled` methods 62 to 67, Flyway migrations 394 to 415, files with native
`@Query` or `JdbcTemplate` 19 to 21, and 51 `@Modifying` bulk JPQL statements (not counted in v0.2; see "Hibernate"
under Enforcement layers). The footprint grows by roughly one small module's worth every few days, so the WS3 figures
below are a floor: re-measure at WS3 kickoff and re-baseline the per-module class table.

### Backend persistence footprint

| Measure | Value | Why it matters |
| --- | --- | --- |
| Modules with their own Postgres database | 26 (one Postgres instance, `postgres/init-databases.sql`) | 26 schemas to retrofit; one RLS convention must be applied 26 times |
| `@Entity` classes | 474 (largest: inventory 65, accounting 60, workorder 46, catalog 40) | Every tenant-scoped entity needs a `tenantId` mapping |
| `@MappedSuperclass` base entity | none | No single place to add the column mapping; needs a scripted retrofit |
| Spring Data repositories | 460 | Derived queries are covered automatically by Hibernate's tenant filter |
| Flyway migrations | 394 SQL files across 27 modules | New per-module tenancy migration; no edits to existing files required |
| `UNIQUE` constraints in migrations | 311 | Every one that names a business key must be re-scoped to `(tenant_id, ...)` |
| `@Query` JPQL/native | 358, of which 19 native in 11 files, plus 11 `JdbcTemplate` users | Native SQL and `JdbcTemplate` bypass the Hibernate filter; RLS is the safety net, but each must be audited |
| `CREATE SEQUENCE`/`nextval` | 7 | Human-readable numbering (order, invoice, workorder numbers, 136 files reference them) is mostly application-side and must become per-tenant |
| TimescaleDB hypertable | 1 (`pos-event-receiver`) | Hypertables support RLS but need `tenant_id` in the partitioning design |
| Database role | All 26 databases owned by the `POSTGRES_USER` superuser | Superusers and table owners bypass RLS; a non-owner application role is a prerequisite |
| Spring cache users | `pos-customer` (`CacheConfig`), `pos-catalog` (`@Cacheable` in `ProductDetailServiceImpl`), Redis in gateway and security-service (token revocation) | Cache keys must carry the tenant or a cached row leaks across tenants |

### Backend runtime and integration footprint

| Measure | Value | Why it matters |
| --- | --- | --- |
| Gateway-injected headers | `X-User`, `X-User-Id`, `X-Roles`, `X-Perm-Bits`, `X-Perm-Ver`, `X-Loc-Fin-Bits`, `X-Loc-Oth-Bits`, `X-Loc-Scope` (`GatewaySecurityConstants`, ADR-0061) | Add `X-Tenant-Id`, sourced only from the validated JWT, to both the inject list and the `stripInboundIdentityHeaders` list in `GatewayAuthProperties` |
| Access-token claims | `sub`, `uid`, `username`, `roles`, `perm_bits`, `perm_ver`, optional `person_id` (`JwtServiceImpl`) | Add `tid`; refresh token also carries `tid` |
| `users.username` | globally `UNIQUE` (`pos-security-service` V1) | Becomes unique per tenant; login must resolve the tenant before the credential check |
| `roles` / `role_permissions` / `role_assignments` | `roles.name` globally `UNIQUE`; seeded by V3, V8, V24 and `R__seed_reference_security.sql`; `role_assignments` is the only store of a user's roles since the ADR-0061 amendment of 2026-09-09 | Roles become tenant-scoped rows (decision below); the seed migrations become a per-tenant role template applied at provisioning; the `permissions` catalog stays global |
| Domain event envelope | `DomainEventEnvelope` record, no tenant field | Add a required `tenantId`; also emit it as a Kafka header |
| `@KafkaListener` methods | 108 across 17 modules | One record interceptor binds tenant context before every listener; individual listeners need no edits unless they open their own transactions oddly |
| Outbox implementations | 18 modules | Outbox rows carry `tenant_id`; the poller is a platform-scoped (cross-tenant) job |
| Event producer call sites | 31 `DomainEventEnvelope.of(...)` callers | Signature change; mechanical |
| `@Scheduled` jobs | 62 across 19 modules (supplier 12, inventory 10, workorder 7) | Each job is either per-tenant (iterate the tenant registry) or platform-scoped (explicitly bypasses RLS) |
| Gateway routes | 23 explicit routes (ADR-0014) | Per-tenant rate limiting is optional follow-up work |

### Backend test footprint

| Measure | Value | Why it matters |
| --- | --- | --- |
| Integration tests (`*IT.java`) | 190 | Isolation tests are additive |
| Unit tests (`*Test.java`) | 1,391 | Mostly unaffected |
| Modules testing on H2 | 25 (`ddl-auto: create-drop` in 23 test profiles) | H2 has no RLS and no `current_setting()`; H2 tests cannot prove isolation and will silently skip it |
| Modules testing on Testcontainers | 6 | Pattern already exists; extend to the rest |
| `@DataJpaTest` / `@SpringBootTest` | 41 / 142 | The ones that hit a database need a bound tenant in the test fixture |
| ArchUnit rule classes | 10 (`pos-archunit`), including `EntityStandardsArchitectureTest` | Natural home for "every entity is tenant-scoped or explicitly global" |

### Frontend and SDK footprint

| Measure | Value | Why it matters |
| --- | --- | --- |
| Tenant awareness | none; `JwtClaims` has `sub`, `roles`, `exp`, `perm_bits`, `perm_ver` | Add `tid`; expose a tenant signal from `AuthService` |
| Token storage | `localStorage` (access + refresh), roles cached in `sessionStorage` | Tokens are tenant-bound; storage must clear on tenant change |
| Login | single `LoginComponent` posting `LoginRequest { username, password }` via `@durion-sdk/security` | Tenant resolution (host or form field) is the only user-visible change |
| `organizationId` usage | two feature areas (CRM integration events, accounting ingestion submit) | These are *organization* concepts and stay distinct from tenant per ADR-0023 §2 |
| SDK packages | 25 generated packages | Only `sdk-security` changes shape; others regenerate unchanged |

---

## Target Model

### Decision summary

| Topic | Recommendation |
| --- | --- |
| Isolation strategy | **Shared database, shared schema, `tenant_id` discriminator column on every tenant-scoped table, enforced by Postgres RLS and mirrored by Hibernate's `@TenantId` filter** |
| Tenant identifier | `tenant_id UUID NOT NULL` (UUID v7 per ADR-0013); a human `slug` lives only on the tenant registry row |
| Relationship to `organizationId` | Unchanged from ADR-0023 §2: `organizationId` remains an in-tenant business concept (see accounting's "NULL means global default" GL mappings). Tenant is the isolation and billing boundary. Never conflate them |
| Owning customer | An **`account`** in the new `pos-tenant` module (ADR-0062 §7): legal name, contacts, billing profile; one account may own several tenants. The word "organization" is not used for tenancy; the existing in-tenant `organizationId` fields are a remnant with no owner and are left for separate cleanup |
| Roles | **Tenant-scoped.** `roles`, `role_permissions`, and `role_assignments` carry `tenant_id`; `roles.name` is unique per `(tenant_id, name)`. The `permissions` catalog (code-first, `perm_bits` bitset, `CATALOG_VERSION`) stays global. Provisioning applies a platform role template (today's seed migrations) to the new tenant; the canonical seeded role names (`ROLE_ADMIN`, `ROLE_CONTROLLER`, ...) are immutable per tenant because the frontend gates on them (ADR-0040 §6), and a tenant may add custom roles beyond the template |
| Platform operators | Users of one reserved **platform tenant** (slug `platform`, bootstrapped by a `pos-tenant` migration under a constant id published by `pos-tenancy-common`). `ROLE_PLATFORM_ADMIN` exists only in that tenant's template. `pos-tenant`'s rows all belong to the platform tenant, so its endpoints work under normal RLS binding; there is no unbound or root session |
| Where tenant context comes from | The validated JWT only. Never from a request body, query parameter, or client-supplied header (keeps DECISION-INVENTORY-008) |
| Database credentials | One shared `pos_app` role for all 26 databases, no superuser, no `BYPASSRLS`, no ownership; the owner credential is used by Flyway only. No per-tenant or per-service roles; customers never hold a database credential |
| Global (non-tenant) data | Explicitly whitelisted per module with a `@TenantGlobal` marker and no RLS policy: vehicle reference data (NHTSA, CarAPI), fitment, tax rate tables, the permission catalog, Flyway history, tenant replicas; and one RLS exception (2026-09-10): the receiver's `emitted_event` hypertable (TimescaleDB excludes row security from compression and caggs); every query names `tenant_id` |
| Deployment | Same images for pooled and dedicated cells; tenancy is a data property, not a build property |
| First administrator credential (decided 2026-09-10) | **Operator-delivered activation token.** Provisioning leaves the first `ADMIN` credential-expired; a `platform:tenant:provision` caller mints a one-time 72-hour token via a narrow `pos-security-service` endpoint; unauthenticated `POST /v1/auth/activate` sets the password. No mail needed; the same token later drives e-mail reset |
| Tenant registry behind `TenantIterator` (decided 2026-09-10) | **Cached REST lookup against `pos-tenant`.** The library's `RemoteTenantRegistry` polls an internal, shared-secret `pos-tenant` list endpoint, keeps the last good snapshot through outages, and starts from the static list. Accepted: a runtime dependency on `pos-tenant` and one service secret; no per-module `ext_tenant` replica |
| Bulk-loaded roles and the template (decided 2026-09-10) | **Loader targets the platform tenant; provisioning reconciles.** `pos-bulk-loader` takes a target tenant per job; a role loaded into the platform tenant gets `template_key` and joins the template; `reconcileTemplate(tenant)` adds new template roles and grants to existing tenants, leaving tenant-local edits alone |
| Outbox manifests (decided 2026-09-10) | **Per tenant.** Manifest records carry `tenantId`, publishers emit one manifest per tenant per window, consumers track drift and request replay per tenant (WS4) |
| Per-tenant observability (decided 2026-09-10) | **Tenant dimension with global rollups.** `emitted_event_hourly` grouped by `tenant_id` (global view is the sum); `pos-mcp-server` keeps a per-tenant tool-priority overlay tuned from that tenant's invocation log, falling back to the global priority (WS6) |

### Why shared schema plus RLS, not the alternatives

| Option | Verdict | Reason |
| --- | --- | --- |
| Database per tenant (today's tenant cell) | Keep as an *option*, not the default | 26 databases per tenant does not scale to thousands of tenants; migrations, pools, and backups multiply by tenant count |
| Schema per tenant (Hibernate `SCHEMA` strategy) | Rejected | 26 services x N tenants x ~500 tables per schema; Flyway runs per schema; `pg_catalog` bloat; connection pools cannot be shared efficiently |
| Shared schema with application-only filtering | Rejected | One missed `where tenant_id = ?` in a native query is a cross-tenant leak; Durion already has 19 native queries and 11 `JdbcTemplate` users |
| **Shared schema, RLS + Hibernate `@TenantId`** | **Recommended** | Two independent enforcement layers; works with existing UUID v7 keys; index-friendly equality predicate; forward-compatible with hash partitioning by `tenant_id` and with Citus-style sharding if a single instance is ever outgrown |

### Enforcement layers

1. **Database (authoritative).** Every tenant-scoped table has `tenant_id UUID NOT NULL`, `ENABLE ROW LEVEL SECURITY`,
   `FORCE ROW LEVEL SECURITY`, and a policy
   `USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid) WITH CHECK (same)`.
   With no setting bound the predicate is NULL and the table reads as empty: fail closed. The `NULLIF` matters:
   `current_setting(..., true)` returns `''`, not NULL, for a setting that was set and later cleared with
   `set_config(name, '', ...)`, and `''::uuid` raises a cast error instead of hiding rows. Connections are therefore
   returned with `RESET app.current_tenant`, and the policy tolerates `''` anyway. `current_setting` is `STABLE`, so
   the planner evaluates it once per query and keeps using the `(tenant_id, ...)` indexes.
2. **Database roles.** One shared application role, `pos_app`, is created once for the Postgres instance (roles are
   cluster-wide) and granted `CONNECT` on all 26 databases plus DML on every table and sequence, with
   `ALTER DEFAULT PRIVILEGES` so tables created by later migrations inherit the grants. `pos_app` is not a superuser,
   has no `BYPASSRLS`, and owns nothing, so every policy applies to it. The existing `POSTGRES_USER` superuser keeps
   ownership and is used only by Flyway (`spring.flyway.user`), never by the application datasource. There is no
   per-tenant and no per-service database credential, and no runtime role that can bypass RLS. Customers never
   receive a database credential of any kind; tenants exist only as rows.
   Two consequences of the owner being a superuser today: `FORCE ROW LEVEL SECURITY` has no effect on it (superusers
   always bypass RLS), so Flyway seed migrations that insert scoped rows keep working on Compose and the alpha host
   without binding a tenant. On managed Postgres (RDS, Cloud SQL) there is no superuser, `FORCE` then applies to the
   owner too, and every seed migration that writes a scoped table must bind `app.current_tenant` per tenant first.
   The migration template does this from day one (`SELECT set_config('app.current_tenant', :tenant, true)` inside
   the migration transaction) so the alpha host and a managed host behave the same.
3. **Connection binding.** A `TenantAwareDataSource` wrapper in a new `pos-tenancy-common` library runs
   `SELECT set_config('app.current_tenant', ?, false)` on checkout and `RESET app.current_tenant` on return.
   Session-level (not `SET LOCAL`) is chosen because services connect to Postgres directly today. If PgBouncer
   transaction pooling is adopted later, this must switch to `SET LOCAL` inside mandatory transactions; note it in
   the ADR as a known constraint.
   Cross-tenant work never bypasses RLS. Jobs that need every tenant iterate the tenant registry and bind each tenant
   in turn; tables a platform job must read without a tenant bound (outbox, processed-event ledger, `ext_tenant`
   replica) are global tables that carry `tenant_id` as plain data and have no policy. A job that runs with no
   tenant bound and touches a scoped table reads zero rows and cannot insert, so a misclassified job is a no-op,
   not a leak.
4. **Hibernate.** A `TenantScopedEntity` `@MappedSuperclass` carries `@TenantId private UUID tenantId`, and a
   `CurrentTenantIdentifierResolver` reads `TenantContext`. Hibernate then appends `tenant_id = ?` to derived and
   JPQL queries and rejects inserts whose tenant does not match the bound context. This is defense in depth and also
   what makes H2-based unit tests see *some* tenancy behaviour.
   Verified against the shipped `hibernate-core` 7.4.5.Final (Spring Boot 4.1.1) on 2026-09-09:
   - `TenantIdBinder` registers the `_tenantId` filter with `applyToLoadByKey = true`, so `find()`,
     `getReferenceById()` and lazy loads by id are tenant-filtered too (715 such call sites today). Older Hibernate
     left load-by-key unfiltered; that gap is closed.
   - The SQM converter attaches the filter restriction to bulk HQL `update` and `delete` statements as well
     (51 `@Modifying` queries today). RLS still owns the final word.
   - The filter is **not** `autoEnabled`; the session enables it when a resolver is registered and
     `resolver.isRoot(tenant)` is false. `isRoot` is a Hibernate-level bypass and is never implemented on Durion:
     RLS would still hide the rows and the two layers would disagree. The resolver returns `false` unconditionally,
     and an ArchUnit rule pins that.
   - `spring-boot-hibernate` 4.1.1 does not auto-register a `CurrentTenantIdentifierResolver` bean, so the
     `HibernatePropertiesCustomizer` shown below is required, not optional.
   - Hibernate forces the `@TenantId` property non-updatable and non-optional at boot, so Lombok `@Builder` and
     `@AllArgsConstructor` on subclasses need no change; the scripted retrofit stays mechanical.
5. **Build.** ArchUnit rules in `pos-archunit`: every `@Entity` either extends `TenantScopedEntity` or is annotated
   `@TenantGlobal`; no `nativeQuery = true` on a tenant-scoped entity's repository without a `@TenantAudited`
   annotation and a reviewer note. A schema-conformance IT per module asserts every non-whitelisted table has the
   column, RLS enabled, RLS forced, and a policy.

### How an entity gets its tenant

Application code never sets `tenantId`; Hibernate stamps it from a request-scoped context that is filled from the
validated JWT. The base class and resolver live once in `pos-tenancy-common`; every scoped entity only extends the
base class.

```java
@MappedSuperclass
public abstract class TenantScopedEntity {
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;   // no setter; ArchUnit forbids assigning it
}

public class TenantContextIdentifierResolver
        implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {
    public UUID resolveCurrentTenantIdentifier() { return TenantContext.require(); }
    public boolean validateExistingCurrentSessions() { return true; }
    public boolean isRoot(UUID tenantId) { return false; }   // never a Hibernate-level bypass; RLS would disagree
    public void customize(Map<String, Object> props) {
        props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
```

With the resolver registered, Hibernate (7.x, shipped with Spring Boot 4.1) writes the bound tenant into the
`@TenantId` field on persist, appends `tenant_id = ?` to every derived and JPQL query, and rejects an update whose
row belongs to another tenant. `TenantContext.require()` throws when nothing is bound, so a code path that forgot
to bind fails loudly; if it somehow reaches the database anyway, RLS returns zero rows and refuses the insert.

### Request path

```text
Browser (slug.durion.app or login form tenant field)
  -> pos-api-gateway: validate JWT, read `tid`, inject X-Tenant-Id (strip any inbound X-Tenant-Id)
  -> service GatewayAuthoritiesFilter: bind TenantContext from X-Tenant-Id (reject if absent on /v1/**)
  -> TenantAwareDataSource: set_config('app.current_tenant', tid) on checkout
  -> Hibernate @TenantId filter + Postgres RLS
```

### Asynchronous path

```text
Producer: DomainEventEnvelope.tenantId (required) + Kafka header `tenantId`
  -> outbox is a global table carrying tenant_id as data; the unbound poller publishes every tenant's rows
  -> Consumer RecordInterceptor reads the header, binds TenantContext, then invokes the @KafkaListener
  -> consumer DB work is scoped exactly like a request
```

### Scheduled jobs

Every one of the 62 `@Scheduled` methods is classified as one of:

- **Per-tenant**: wrapped in `TenantIterator.forEachActiveTenant(tenantId -> ...)`, which reads the module's
  `ext_tenant` replica and binds context per iteration. This is the default.
- **Platform-scoped**: annotated `@PlatformScoped`, runs with no tenant bound, may touch only global tables, and is
  listed in the module README. Outbox pollers, processed-event cleanup, and replica maintenance fall here. RLS makes
  scoped tables invisible to such a job, which is the intended fail-closed behaviour.

### Tenant registry and propagation

- A new **`pos-tenant`** module (package `com.positivity.tenant`, database `pos_tenant_db`, Eureka `TENANT`,
  gateway route `/tenant/**`, built on the `pos-location` skeleton) owns the master `tenant` table (`id`, `slug`,
  `display_name`, `status`, `account_id`, cell or region, lifecycle timestamps) and the customer that owns each
  tenancy: `account`, `account_contact` (`OWNER` / `BILLING` / `TECHNICAL`), and `billing_profile` (billing address,
  payment terms, invoicing email, payment-processor token; never a card number). One account may own several
  tenants. Plans and subscriptions attach to `tenant` later.
- `pos-tenant` runs inside the platform tenant: every row carries the platform tenant's id, so RLS protects account,
  contact, and billing data like any other table. Platform-admin endpoints (`/v1/platform/tenants`,
  `/v1/platform/accounts`) are guarded by the `platform:tenant:*` and `platform:account:*` permission families,
  which exist only in the platform tenant's role template.
- It emits `tenant.created`, `tenant.provisioned`, `tenant.updated`, `tenant.suspended`, `tenant.reactivated`, and
  `tenant.decommissioned` on `tenant.events.v1`, carrying the public projection only (`id`, `slug`, `display_name`,
  `status`). Status machine: `PENDING` → `ACTIVE` (on `tenant.provisioned` from security-service) ⇄ `SUSPENDED`,
  `DECOMMISSIONED` terminal. Login for a tenant that is not `ACTIVE` returns the bad-credentials 401.
- Every module keeps an `ext_tenant` replica (global table) of that projection, consistent with ADR-0044's
  event-only domain walls. Modules never call `pos-tenant` synchronously to resolve tenants; `pos-security-service`
  serves `GET /v1/tenants/me` and login slug resolution from its replica.
- Provisioning is a `pos-security-service` handler of `tenant.created` that runs under
  `TenantContext.runAs(newTenantId)`: it applies the role template (the roles, their permissions, persona fields and
  ADR-0061 location-scope attributes), creates the initial administrator user named in the create request, writes
  the first `role_assignments` row, and emits `tenant.provisioned`. Other modules seed their per-tenant defaults
  (chart of accounts, GL mapping defaults) from the same `tenant.created` event, inside their own domain, not by
  cross-domain writes. Every handler is idempotent on tenant id.

### Performance notes for high volume

- Every tenant-scoped index leads with `tenant_id`; primary keys stay UUID v7 so global uniqueness is preserved.
- The RLS predicate is an equality on a leakproof operator, so Postgres can still use the composite indexes.
- Hash-partition the largest tables (inventory ledger, audit log, event-receiver hypertable) by `tenant_id` only when
  measurements call for it; the column layout makes it a later, mechanical change.
- Tenant tag in MDC, traces, and metrics. Keep tenant out of high-cardinality metric labels by default; use exemplars
  or a bounded top-N.
- Per-tenant rate limiting and connection fairness at the gateway are follow-up work, not prerequisites.

---

## Workstreams and Effort

Effort is in engineer-weeks for one engineer working with the agent tooling this repo already relies on. Ranges reflect
unknowns called out in each row. Calendar time compresses with parallel module waves in the style of the existing
API Orchestrator workflow.

| ID | Workstream | Repo | Effort (eng-weeks) | Depends on |
| --- | --- | --- | --- | --- |
| WS0 | Governance: new ADR superseding ADR-0023; amend tenant-cell doc, ADR-0045, glossary, security decisions, knowledge catalog. **Done 2026-09-09** (ADR-0062 accepted; every row of its "Amends" table applied) | durion | 1 | none |
| WS1 | **Done 2026-09-10** (backend #1923, piloted on `pos-location` with WS4's async path). `pos-tenancy-common`: `TenantContext`, `TenantContextFilter`, `TenantAwareDataSource`, `TenantScopedEntity`, `@TenantGlobal`, `@PlatformScoped`, `TenantIterator`, Hibernate resolver, Kafka interceptor; `pos_app` on the pilot; isolation/conformance ITs; ArchUnit rules | backend | 2 - 3 (spent ~1) | WS0 |
| WS2a | **Done 2026-09-10** (backend #1924). `pos-tenant`: account, contacts, billing profile, tenant, status machine, platform tenant bootstrap (`PlatformTenant.ID`), `tenant.events.v1` outbox producer + `tenant.provisioned` consumer, platform-admin API behind `platform:*`, `/tenant/**` route, `PlatformTenantGuard`, OpenAPI, reactor/Compose/deploy wiring | backend | 1.5 - 2 (spent ~0.5) | WS1 |
| WS2b | **Done 2026-09-10** (backend #1926 identity: `ext_tenant`, login tenant resolution, `tid`, `X-Tenant-Id`, `/v1/tenants/me`; backend #1927 provisioning: `template_key`, platform role template + `PLATFORM_ADMIN` seed, `tenant.created` handler, `tenant.provisioned`, `platform:*` off `ADMIN`). Bulk-loaded roles join the template in WS8 | backend | 2.5 - 3 (spent ~1.5) | WS1, WS2a |
| WS3 | Per-module retrofit x 27. Schema done 2026-09-09 (Appendix B). Done 2026-09-10: waves 1-11 (backend #1928-#1932, #1934; 20 modules), **waves 12-13 (backend #1935): `pos-mcp-server`, `pos-event-receiver`** (batched ingest saves each event under the tenant it was queued with). Left: `pos-bulk-loader` (WS8); per-tenant event stats and tool priorities are WS6 | backend | 8 - 11 (spent ~2) | WS1, WS2 |
| WS4 | *Pilot done with WS1 and WS3 (Kafka header, consumer interceptor, outbox `tenant_id` in every adopted module); the envelope field and the producer signature change remain.* Async platform: envelope `tenantId`, producer signature change (31 sites); per-tenant manifests and replay; `RemoteTenantRegistry` (decided 2026-09-10) | backend | 1 - 2 | WS1 |
| WS5 | Test infrastructure: move the 25 H2-tested modules' database tests to Testcontainers Postgres; CI runner Docker availability; shared `TenantTestSupport` fixture | backend | 2 - 3 | WS1, overlaps WS3 |
| WS6 | Storage, observability, operations: documents/images tenant-prefixed paths, MDC and trace tenant tag, `pos-mcp-server` session scoping and per-tenant tool priorities, `emitted_event_hourly` by tenant with global rollups (decided 2026-09-10), per-tenant export tooling for offboarding (replaces per-cell `pg_dump`), Compose/alpha runbook role changes | backend, durion | 2 - 3 | WS1 |
| WS7 | Frontend and SDK: tenant resolution, `tid` in `JwtClaims`, `AuthService` tenant signal, storage hygiene, header tenant name, platform-admin tenant and account pages, mock-auth token, i18n x 4 locales, specs; regenerate `sdk-security` and generate `sdk-tenant` | frontend, sdk | 2 - 3 | WS2a, WS2b |
| WS8 | Seed and bulk load: alpha seed data tenant-tagged, `pos-bulk-loader` with a target tenant per job, platform-tenant roles join the template via `reconcileTemplate(tenant)`, first-administrator activation token (decided 2026-09-10); provisioning defaults per module, documentation | backend, durion | 1 - 2 | WS2b, WS3 |

**Total: roughly 29 - 40 engineer-weeks; about 32 is the planning figure.** With three backend engineers, one frontend
engineer, and agent-run module waves, that is on the order of 10 - 12 calendar weeks, dominated by WS3.

### How WS3 is sized

| Module class | Modules | Per-module effort | Notes |
| --- | --- | --- | --- |
| Large (40+ entities) | inventory, accounting, workorder, catalog | 1 - 1.5 weeks each | Most unique constraints, native queries, schedulers, and numbering live here |
| Medium (15 - 30 entities) | shop-manager, order, customer, supplier, warranty, people, invoice, security-service | 0.5 - 0.75 week each | security-service is mostly WS2 |
| Small (< 15 entities) | location, marketing, mcp-server, vehicle-fitment, vehicle-inventory, price, nhtsa, carapi, people-contact, event-receiver, bulk-loader, tax, image | 1 - 3 days each | Several are mostly or entirely `@TenantGlobal` |

The per-module checklist in the appendix is what a wave executes; the generic migration template and the scripted
superclass retrofit are what keep the small modules at days rather than weeks.

### What is deliberately *not* in the estimate

- Data migration of any kind. The database is dropped and recreated.
- Per-tenant rate limiting, quotas, or noisy-neighbour controls at the gateway.
- Table partitioning or Citus. The design leaves the door open; the work is not scheduled.
- A tenant switcher for users who belong to more than one tenant. v1 is one user, one tenant.
- Re-platforming the alpha AWS host. Pooled and dedicated cells run the same Compose/ECS definitions.

---

## Backend Requirements

### R-B1 Tenancy library (`pos-tenancy-common`)

- `TenantContext`: thread-bound (and Reactor-context-bound for the gateway) holder with `bind`, `current`,
  `require`, and `runAs(tenantId, Runnable)`. Absent context on a `/v1/**` request is a 401
  (ADR-0017 envelope), never a silent unscoped query.
- `TenantAwareDataSource`: wraps the module's single Hikari `DataSource`; sets and resets `app.current_tenant` per
  checkout; leaves it unset for `@PlatformScoped` work so RLS hides every scoped table.
- Datasource credentials: the application pool connects as `pos_app`; Flyway is configured with the owner
  credential via `spring.flyway.user` / `spring.flyway.password`. No second pool and no `BYPASSRLS` role exist.
- `TenantScopedEntity` (`@MappedSuperclass`, `@TenantId UUID tenantId`, no setter), `@TenantGlobal`,
  `@PlatformScoped`, `@TenantAudited` (for reviewed native queries), `TenantIterator`.
- `CurrentTenantIdentifierResolver` auto-configured in every JPA module; `hibernate.tenant_identifier_resolver`.
- Tenant propagation for `@Async`, `CompletableFuture`, and `TaskDecorator` so background threads inherit context.

### R-B2 Schema conventions (every module)

- Tenant-scoped table: `tenant_id UUID NOT NULL`, RLS enabled and forced, one policy named `tenant_isolation`,
  composite index `(tenant_id, <existing leading column>)` where a hot query exists, unique constraints re-scoped to
  include `tenant_id`, and foreign keys between two tenant-scoped tables widened to `(tenant_id, id)` **by default**.
  Foreign-key checks run with the owner's privileges and bypass RLS, so a single-column FK can be satisfied by another
  tenant's row; the composite key is what makes the reference itself tenant-safe. Each scoped table therefore also
  carries `UNIQUE (tenant_id, id)` as the FK target. Hibernate maps the association with `@JoinColumns` on
  `(tenant_id, <fk>)`, with the `tenant_id` join column `insertable = false, updatable = false`; the retrofit script
  emits this for every `@ManyToOne`/`@OneToOne` whose target extends `TenantScopedEntity`. A single-column FK stays
  only where the target is `@TenantGlobal`.
- Global table: listed in the module's `tenancy-global-tables.txt`, annotated `@TenantGlobal` on the entity, no
  `tenant_id`, no policy. The schema-conformance IT reads that file.
- Migration shape: one new `V<next>__tenancy.sql` per module generated from a shared template that iterates
  `information_schema.tables` minus the global list, plus hand-written unique-constraint and index rewrites.
  Existing migrations are not edited.
- `postgres/init-databases.sql` creates the single `pos_app` role and, per database, grants `CONNECT`, schema
  `USAGE`, DML on tables, `USAGE` on sequences, and `ALTER DEFAULT PRIVILEGES FOR ROLE <owner>` so tables created
  by later migrations are covered. Compose and the alpha runbook inject `pos_app` for `SPRING_DATASOURCE_*` and
  the owner credential for `SPRING_FLYWAY_*`.
- Global tables (`@TenantGlobal`) that platform jobs read without a tenant bound still carry `tenant_id` as data
  where it is meaningful (outbox, processed events) so events and audit records stay attributable.

### R-B3 Identity and gateway

- `ext_tenant` replica in `pos-security-service` fed by `tenant.events.v1` (the master `tenant` table lives in
  `pos-tenant`); `users.tenant_id NOT NULL`; `UNIQUE (tenant_id, username)`; `jwt_token.tenant_id`.
- Roles are tenant-scoped: `roles.tenant_id NOT NULL`, `UNIQUE (tenant_id, name)`; `role_permissions` and
  `role_assignments` carry `tenant_id` and composite foreign keys to `roles (tenant_id, id)`; `permissions` stays a
  global table. The existing seed migrations (V3, V8, V24, `R__seed_reference_security.sql`) are rewritten into a
  role-template table in the platform tenant plus the provisioning handler above; the canonical seeded role names are
  read-only per tenant, custom roles are not. The `roles` claim and `perm_bits` derivation are unchanged in shape.
- Login resolves the tenant from, in order: the gateway-supplied `X-Tenant-Slug` derived from the `Host` header, or
  an optional `tenantSlug` on `LoginRequest`. Unknown slug returns the same 401 as bad credentials.
- Access and refresh tokens carry `tid`. ADR-0040's claim contract gains `tid` as required.
- Gateway: add `X-Tenant-Id` and `X-Tenant-Slug` to the six headers `GatewayAuthProperties.stripInboundIdentityHeaders`
  already strips, inject `X-Tenant-Id` from `tid`, and add `X-Tenant-Slug` only for the `/auth/login` route.
  `GatewaySecurityConstants` gains both names. `GatewayAuthoritiesFilter` binds `TenantContext` from `X-Tenant-Id`.
- Platform-admin endpoints in `pos-tenant` (`/v1/platform/tenants`, `/v1/platform/accounts`) with the
  `platform:tenant:{create,read,update,suspend,reactivate,decommission}` and `platform:account:{create,read,update}` permission
  families registered per ADR-0025 and added to the permission bitset catalog (version bump, fleet-coordinated).
- Tenant events on `tenant.events.v1` from `pos-tenant`; `tenant.provisioned` from `pos-security-service` on the
  same topic; consumer handler template for the `ext_tenant` replica shipped in `pos-tenancy-common`.

### R-B4 Events and jobs

- `DomainEventEnvelope` gains `@NonNull UUID tenantId`; `of(...)` reads it from `TenantContext` when not supplied.
- Kafka header `tenantId` set by the outbox publisher; consumer `RecordInterceptor` binds and clears context.
- The outbox table is `@TenantGlobal` with a `tenant_id` data column; the poller runs unbound and publishes every
  tenant's rows with the `tenantId` header taken from the row.
- Every `@Scheduled` method is annotated either `@PlatformScoped` or wrapped in `TenantIterator`; an ArchUnit rule
  fails the build on an unclassified scheduler.

### R-B5 Numbering and sequences

- Audit the seven database sequences and the application-side counters that generate order, invoice, workorder,
  and similar human-readable numbers. Replace with a per-tenant counter table (`tenant_id, counter_name, next_value`)
  with `SELECT ... FOR UPDATE`, or a per-tenant formatted prefix. Document the choice in each domain's
  `BACKEND_CONTRACT_GUIDE.md`.

### R-B6 Storage, MCP, observability

- Document and image storage paths become `<tenant_id>/<existing path>`; the storage service reads the tenant from
  context, never from the request.
- `pos-mcp-server` conversations, tool traces, and any cached LLM context are tenant-scoped rows; agent tools receive
  the bound tenant and cannot be asked to switch.
- Spring cache: every `@Cacheable`/`@CacheEvict` key is prefixed with the bound tenant through a shared
  `TenantKeyGenerator` in `pos-tenancy-common`; `pos-customer`'s `CacheConfig` and `pos-catalog`'s
  `ProductDetailServiceImpl` are the two users today. Gateway and security-service Redis token-revocation keys are
  already keyed by token id and need no change.
- MDC key `tenantId` on every log line; OpenTelemetry resource attribute on spans; bounded tenant label on metrics.

### R-B7 Tests and verification gates

- Testcontainers Postgres for every module that persists; H2 remains only for pure unit tests with no schema.
- Shared `TenantTestSupport`: `asTenant(id, () -> ...)`, two fixed tenant ids, and a `crossTenantReadIsEmpty`
  assertion helper.
- Per module: a `TenantIsolationIT` that writes as tenant A, reads as tenant B through the repository and through a
  raw `JdbcTemplate`, and expects nothing both times (proves RLS, not just Hibernate).
- Per module: `TenancySchemaConformanceIT` reading the global-table whitelist, and asserting that the application
  connection is `pos_app` with `rolsuper = false`, `rolbypassrls = false`, and no table ownership.
- `pos-archunit`: entity classification rule, scheduler classification rule, native-query annotation rule.
- OpenAPI regeneration and `API Artifacts Sync` for `pos-security-service` (and any module whose DTOs change).

---

## Frontend Requirements

### R-F1 Tenant resolution and login

- `environment.tenantResolution: 'host' | 'form'`. In `host` mode the frontend sends nothing; the gateway derives
  the slug from the `Host` header. In `form` mode (localhost, shared preview hosts) the login page shows a tenant
  field and sends `tenantSlug` on `LoginRequest`.
- The login page treats unknown tenant and bad credentials identically in copy (no tenant enumeration).
- SSR: no change beyond making sure the `Host` header is forwarded to the API proxy unchanged.

### R-F2 Auth state

- `JwtClaims` gains `tid: string` (required for new tokens; optional in the type only until the SDK is regenerated).
- `AuthService` exposes `currentTenantId` and, after a `GET /v1/tenants/me` call via `@durion-sdk/security` (served
  from security-service's `ext_tenant` replica), a `currentTenant` signal with `slug` and `displayName`.
- On login, if the stored token's `tid` differs from the new one, clear all `localStorage`/`sessionStorage` keys
  the app owns before storing the new pair. Cached roles are keyed per tenant.
- The mock-auth token includes a fixed `tid` so `mockAuth: true` still exercises tenant-aware code paths.

### R-F3 UI surfaces

- Shell header shows the tenant display name next to the user; no switcher in v1.
- New platform-admin feature area under `/app/admin/tenants` (list, create, suspend, reactivate, detail) and
  `/app/admin/accounts` (list, create, detail with contacts and billing profile), gated by `ROLE_PLATFORM_ADMIN` via
  `rolesChildGuard`, following the four-file page layout and the two-signal state machine (`state` + `errorKey`,
  ADR-0031, ADR-0033). These pages consume `@durion-sdk/tenant`.
- No domain page ever sends a tenant identifier. The two existing `organizationId` usages (CRM integration events,
  accounting ingestion submit) stay as they are; they are organization scoping, not tenancy.

### R-F4 Quality gates

- All new strings in `en-US`, `es-US`, `fr-CA`, `qps-ploc` (ADR-0030); `npm run i18n:check` clean.
- Accessibility baseline on the new pages (ADR-0029, `npm run a11y:smoke`).
- Typed fixtures updated for `JwtClaims` (ADR-0032); service specs for every new `AuthService` and tenant-admin
  service method (ADR-0035).
- Route guard specs cover the platform-admin gate; interceptor spec confirms no tenant header is ever attached
  client-side.

### R-F5 SDK

- `@durion-sdk/security` regenerates with `LoginRequest.tenantSlug?` and `TenantMeResponse`. A new
  `@durion-sdk/tenant` package is generated from `pos-tenant` (`TenantResponse`, `TenantCreateRequest`,
  `AccountResponse`, `AccountCreateRequest`, contacts, billing profile, the platform tenant and account API services).
  The other packages regenerate with no shape change. Run `API Artifacts Sync` once the backend branch is pushed; the
  frontend consumes only generated SDK types (ADR-0041).

---

## Sequencing

1. **WS0** ADR accepted; tenant-cell doc and ADR-0045 amended. *Done 2026-09-09.*
2. **WS1 + WS4** land together on a single pilot module (`pos-location` is small, has schedulers, listeners, and an
   outbox) to prove the whole path end to end, including the Testcontainers isolation IT. *Done 2026-09-10:
   `pos-tenancy-common`, `pos-location` on `pos_app` with `TenantIsolationIT` and `TenancySchemaConformanceIT`;
   the transitional `pos.tenancy.default-tenant-id` stays until WS2b.*
3. **WS2a** `pos-tenant`, then **WS2b** identity and gateway; from this point every request in the integration cell
   is tenant-bound. *WS2a done 2026-09-10 (backend #1924): the registry runs inside the platform tenant and refuses any
   other binding; `platform:*` permissions are granted to `ADMIN` until WS2b's role template exists. WS2b part 1
   done 2026-09-10: identity adopted, `tid` issued and `X-Tenant-Id` injected; the transitional default now covers
   only tokens issued before it and unbound internal paths. Part 2 done 2026-09-10 (backend #1927): the platform
   role template and `PLATFORM_ADMIN` are seeded, `tenant.created` provisions roles and the first administrator and
   answers `tenant.provisioned`, and `platform:*` left the alpha `ADMIN`.*
4. **WS3 + WS5** module waves, largest first, using the pilot as the exemplar. Each wave: classify, migrate, retrofit,
   audit queries, classify schedulers, convert tests, prove isolation, regenerate OpenAPI where DTOs changed.
5. **WS7** frontend and SDK can start as soon as WS2a's and WS2b's OpenAPI is published; it does not wait for WS3.
6. **WS6 + WS8** operations, storage, seed, and docs close the loop; the alpha cell is rebuilt as a pooled cell with
   two seeded tenants, and the cross-tenant smoke test runs in CI against it.

Exit criterion for the whole effort: the integration cell serves two tenants from one Postgres instance, every
module's `TenantIsolationIT` and `TenancySchemaConformanceIT` pass on Testcontainers, every service connects as
`pos_app`, and a scripted cross-tenant probe (login as tenant A, request every listed GET with tenant B's ids)
returns only 404s.

---

## Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| A service is deployed with the owner/superuser credential instead of `pos_app` | Complete loss of isolation while every test passes | `pos_app` is created in WS1; the conformance IT and a startup check assert the connected role is `pos_app`, not superuser, not `BYPASSRLS`, not owner |
| H2 test profiles hide missing tenancy | False green builds | WS5 makes Testcontainers mandatory for any test that touches a repository |
| Native SQL and `JdbcTemplate` bypass Hibernate | Cross-tenant read if RLS is misconfigured on that table | Conformance IT plus `@TenantAudited` review marker; RLS is authoritative regardless |
| Scheduler runs unscoped and reads nothing (fail closed) | Silent no-op jobs | Mandatory classification enforced by ArchUnit; platform-scoped jobs are listed in module READMEs; a metric counts rows processed per job so a permanently-zero job is visible |
| Kafka consumer processes an event without binding context | Writes rejected by RLS `WITH CHECK`, event lands in DLQ | Interceptor is auto-configured; envelope `tenantId` is non-null so producers cannot omit it |
| Table classification mistakes (scoped data marked global) | Data shared across tenants | Classification is a reviewed artifact per module; default is scoped, global needs a justification line |
| A tenant renames or deletes a seeded role | Frontend role gating (ADR-0040 §6) and `ROLE_*` checks break for that tenant | Seeded role names are read-only per tenant (`roles.template_key NOT NULL` rows reject rename/delete); custom roles are unrestricted |
| Cached rows keyed without the tenant | Cross-tenant read through the cache while every database test passes | `TenantKeyGenerator` is the only key generator; an ArchUnit rule forbids `@Cacheable` without it |
| Session-level `set_config` versus a future PgBouncer | Context leaks across pooled transactions | Documented constraint; switch to `SET LOCAL` with mandatory transactions before adopting PgBouncer |
| Permission catalog version bump for `platform:tenant:*` | Fleet-coordinated deploy, same trap as `CATALOG_VERSION` 41 to 42 (issue #389) | Sequence gateway, security-service, and consumers in one release definition |
| Per-tenant backup/restore is no longer a `pg_dump` | Offboarding and point-in-time restore for one tenant need tooling | WS6 delivers a logical per-tenant export; dedicated cells remain available for customers who need physical restore |

---

## Appendix A: Draft Outline for the Superseding ADR

The full draft is [ADR-0062](../../adr/0062-postgres-row-level-multitenancy.adr.md). The outline below is what it was
written from; the number changed from 0061 to 0062 because ADR-0061 (location scope authorization) was accepted the
same day this plan was written.

```text
ADR-0062: Postgres Row-Level Multitenancy (supersedes ADR-0023)

Status: PROPOSED
Context: ADR-0023 removed tenantId because the platform did not implement tenancy. The platform must now host many
  organizations at high volume on shared Postgres without one deployment per customer.
Decision:
  1. Tenancy model: shared database and schema; tenant_id UUID v7 discriminator on every tenant-scoped table.
  2. Enforcement: Postgres RLS (enabled + forced) is authoritative; every service connects as the single shared
     `pos_app` role, which owns nothing and cannot bypass RLS; the owner credential is used by Flyway only;
     Hibernate @TenantId is defense in depth; ArchUnit and schema-conformance tests are the build gate.
     Customers never hold a database credential; tenants are rows, not roles.
  3. Context source: the validated JWT `tid` claim only; gateway injects X-Tenant-Id; clients never supply it.
  4. Naming: tenantId and organizationId remain distinct (ADR-0023 §2 carried forward).
  5. Global data: explicit per-module whitelist annotated @TenantGlobal.
  5a. Roles are tenant-scoped rows provisioned from a platform template; the permissions catalog stays global;
      platform operators are users of a reserved platform tenant.
  5b. A new pos-tenant module owns the master tenant table and the account (contacts, billing profile) that owns
      each tenancy; security-service consumes tenant.events.v1. The owning customer is an account, never an
      "organization"; existing organizationId fields are a remnant.
  6. Async: tenantId is a required envelope field and Kafka header; consumers bind before processing.
  7. Jobs: per-tenant by default; @PlatformScoped jobs run unbound and may touch only global tables. No runtime
     path bypasses RLS.
  8. Deployment: tenant cells may be pooled (many tenants) or dedicated (one tenant) from the same images.
Amends: FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md ("Decisions This Architecture Locks In"),
  ADR-0045 (cost model per pooled cell), ADR-0040 (tid claim), ADR-0011 (X-Tenant-Id header), ADR-0044 (envelope).
Consequences: breaking contract change (no bridge, alpha); all DB tests on Postgres; per-tenant export tooling.
```

## Appendix B: Per-Module Wave Checklist (WS3)

- [x] Classify every table: scoped (default) or global (justified line in `tenancy-global-tables.txt`). *Done for every module by the 2026-09-09 flatten; re-check when a table is added.*
- [x] ~~Generate `V<next>__tenancy.sql` from the template; add hand-written unique-constraint and index rewrites.~~
      *Done 2026-09-09 for every module: the flattened `V1__baseline_<module>.sql` already carries `tenant_id`, RLS, the
      `tenant_isolation` policy, tenant-leading unique constraints and composite foreign keys, so there is no per-module
      tenancy migration. A new table follows `durion-positivity-backend/docs/TENANCY_SCHEMA.md` in the baseline itself.*
- [ ] Retrofit entities: scoped entities extend `TenantScopedEntity`; global entities carry `@TenantGlobal`.
- [ ] Audit native `@Query` and `JdbcTemplate` usage; annotate `@TenantAudited` with a one-line reason.
- [ ] Replace shared counters or sequences used for business numbering with per-tenant counters.
- [ ] Add `tenant_id` as a data column to the outbox table, mark it `@TenantGlobal`, and verify the poller sets the
      Kafka `tenantId` header from the row.
- [ ] Classify every `@Scheduled` method; wrap per-tenant jobs in `TenantIterator`.
- [ ] Add the `ext_tenant` replica handler if the module iterates tenants.
- [ ] Convert database tests to Testcontainers; add `TenantIsolationIT` and `TenancySchemaConformanceIT`.
- [ ] Regenerate `openapi.yaml` if any DTO changed; run `API Artifacts Sync`.
- [ ] Update the module README and the domain `BACKEND_CONTRACT_GUIDE.md` (numbering, global tables, platform jobs).

## Appendix C: Documents To Update When the ADR Is Accepted

- `docs/adr/0023-remove-tenantid-single-organization-context.adr.md` (Status: SUPERSEDED by ADR-0062)
- `docs/adr/0023-tenantid-removal-checklist.md` (Status: CLOSED, superseded)
- `docs/architecture/deployment/FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md`
- `docs/adr/0045-autonomous-environment-lifecycle-management.adr.md`
- `docs/adr/0011-api-gateway-security-architecture.adr.md`, `docs/adr/0040-roles-jwt-permission-governance-policy.adr.md`,
  `docs/adr/0061-location-scope-authorization-ownership.adr.md` (role attributes live on tenant-scoped role rows)
- `docs/architecture/API_SECURITY_ARCHITECTURE.md`, `docs/architecture/AUTHORIZATION_MODEL.md`
- `.ai/GLOSSARY.md`, `domains/security/security-questions.md`, `knowledge-catalog/adr/index.md`
- `durion-positivity-backend/AGENTS.md`, `durion-positivity-frontend/AGENTS.md`, and `CLAUDE.md` ADR minimum lists
