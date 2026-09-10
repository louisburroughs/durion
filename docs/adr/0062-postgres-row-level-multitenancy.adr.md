---
title: 'ADR-0062: Postgres Row-Level Multitenancy'
created: 2026-09-09
status: accepted
supersedes: ADR-0023
---

## ADR-0062: Postgres Row-Level Multitenancy

**Status:** ACCEPTED 2026-09-09
**Date:** 2026-09-09
**Deciders:** Chief Architect, Security & Authorization Domain, Platform Engineering, Domain Leads
**Affected Issues:** [louisburroughs/durion#438](https://github.com/louisburroughs/durion/issues/438) (epic)
**Supersedes:** [ADR-0023](0023-remove-tenantid-single-organization-context.adr.md)
**Plan:** [ADR-0023 Suppression: Postgres Row-Level Multitenancy Plan](../architecture/plans/adr-0023-suppression-postgres-multitenancy-plan.md) (v0.3)

---

### Context

- **Current state.** [ADR-0023](0023-remove-tenantid-single-organization-context.adr.md) (2026-02-21) removed
  `tenantId` from every contract and declared the platform single-organization. The
  [Foundation-First Tenant Cell Deployment Architecture](../architecture/deployment/FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md)
  and [ADR-0045](0045-autonomous-environment-lifecycle-management.adr.md) then built on that: one isolated deployment
  per customer, and a cost model per tenant cell. The backend has 26 Postgres databases owned by one superuser, 493
  `@Entity` classes with no shared superclass, 415 Flyway migrations, 116 Kafka listener methods, 67 scheduled methods,
  and 25 modules whose database tests run on H2. Nothing in code, schema, token, or gateway knows what a tenant is.
- **The problem.** One deployment per customer does not scale to a high-volume customer base: every customer
  multiplies 26 databases, 26 Flyway runs, 26 connection pools, and one full runtime. The platform must host many
  organizations from one runtime and one Postgres instance without a shared-schema leak becoming possible.
- **Drivers.** Cost per customer; provisioning time; a single fleet to operate and upgrade; the alpha stage, which
  allows breaking contract changes with no data migration (every database is dropped and recreated between builds).
- **Scope.** Every persisting backend module, `pos-security-service` and `pos-api-gateway` in particular, a new
  `pos-tenant` module, `pos-domain-events`, the frontend and the `@durion-sdk/security` and `@durion-sdk/tenant`
  packages, the tenant-cell deployment architecture, and the documents listed under "Amends".

The plan referenced above measured the footprint, compared the isolation strategies, and sized the work at roughly
28 to 39 engineer-weeks across eight workstreams. This ADR records the decisions the plan asked for; the plan holds
the workstreams, sequencing, per-module checklist, and risk register.

---

### Decision

#### 1. Tenancy model

**Decision:** ✅ **Resolved** - Shared database, shared schema. Every tenant-scoped table carries
`tenant_id UUID NOT NULL` (UUID v7 per [ADR-0013](0013-platform-uuid-identifier-strategy.adr.md)). A tenant is a row in
the tenant registry, never a database, schema, or role. Primary keys stay globally unique UUID v7, so identifiers do
not change shape.

Database-per-tenant (today's tenant cell) and schema-per-tenant (Hibernate `SCHEMA` strategy) are rejected as the
default; see Alternatives.

#### 2. Enforcement: Postgres RLS is authoritative, Hibernate `@TenantId` is defense in depth

**Decision:** ✅ **Resolved** - Two independent layers, with the database holding the final word.

- **Row-level security.** Every tenant-scoped table has `ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`,
  and one policy named `tenant_isolation`:
  `USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid) WITH CHECK (same)`.
  With nothing bound the predicate is NULL and the table reads as empty and refuses inserts: fail closed. The
  `NULLIF` guards the case where the setting was cleared to `''` rather than reset, which would otherwise raise a
  cast error instead of hiding rows.
- **One application role.** Every service connects as the single shared `pos_app` role: not a superuser, no
  `BYPASSRLS`, owns nothing, granted `CONNECT`, schema `USAGE`, DML, and sequence `USAGE` on all databases with
  `ALTER DEFAULT PRIVILEGES` for tables created by later migrations. The owner credential is used by Flyway only
  (`spring.flyway.user`), never by the application datasource. There is no per-tenant and no per-service credential,
  and no runtime role that can bypass RLS. Customers never receive a database credential.
- **Connection binding.** A `TenantAwareDataSource` in a new `pos-tenancy-common` library runs
  `set_config('app.current_tenant', ?, false)` on checkout and `RESET app.current_tenant` on return. This is
  session-level because services connect to Postgres directly; adopting PgBouncer transaction pooling later requires
  switching to `SET LOCAL` inside mandatory transactions and is a recorded constraint.
- **Hibernate.** A `TenantScopedEntity` `@MappedSuperclass` carries `@TenantId UUID tenantId` with no setter, and a
  `CurrentTenantIdentifierResolver<UUID>` reads the bound context. Verified on the shipped `hibernate-core`
  7.4.5.Final: the `_tenantId` filter applies to load-by-key (`find()`, `getReferenceById()`, lazy loads) and to bulk
  HQL `update`/`delete`; Hibernate stamps the tenant on persist and forces the property non-updatable. The resolver's
  `isRoot()` returns `false` unconditionally and an ArchUnit rule pins it: a Hibernate-level bypass would disagree
  with RLS. Spring Boot 4.1 does not auto-register the resolver, so `pos-tenancy-common` registers it through a
  `HibernatePropertiesCustomizer`.
- **Build gate.** ArchUnit in `pos-archunit`: every `@Entity` extends `TenantScopedEntity` or is annotated
  `@TenantGlobal`; every `@Scheduled` method is per-tenant or `@PlatformScoped`; `nativeQuery = true` and
  `JdbcTemplate` on scoped data require `@TenantAudited`; `@Cacheable` requires the tenant key generator. A
  per-module `TenancySchemaConformanceIT` asserts every non-whitelisted table has the column, RLS enabled and
  forced, and the policy, and that the connected role is `pos_app` with `rolsuper = false`, `rolbypassrls = false`,
  and no table ownership. A per-module `TenantIsolationIT` writes as tenant A and reads as tenant B through the
  repository and through raw JDBC, expecting nothing both times.

#### 3. Tenant context source

**Decision:** ✅ **Resolved** - The validated access token's `tid` claim only. `pos-api-gateway` strips inbound
`X-Tenant-Id` and `X-Tenant-Slug` (added to the `stripInboundIdentityHeaders` list of
[ADR-0011](0011-api-gateway-security-architecture.adr.md)), injects `X-Tenant-Id` from `tid`, and derives
`X-Tenant-Slug` from the `Host` header for the login route only. `GatewayAuthoritiesFilter` binds `TenantContext`
from `X-Tenant-Id`; a `/v1/**` request without it is a 401 in the [ADR-0017](0017-api-controller-http-response-codes.adr.md)
envelope, never a silent unscoped query. Clients, request bodies, and query parameters never carry a tenant
identifier (DECISION-INVENTORY-008 carried forward).

Login resolves the tenant from the gateway-supplied slug or an optional `tenantSlug` on `LoginRequest` (localhost
and shared preview hosts). An unknown slug returns the same 401 as bad credentials. Access and refresh tokens carry
`tid`; [ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md)'s claim contract gains `tid` as required.

#### 4. Naming: `tenant`, `account`, and the `organizationId` remnant

**Decision:** ✅ **Resolved** - `tenantId` is the isolation boundary. The customer that owns one or more tenancies
is an **`account`** (§7), never an "organization". ADR-0023 §2 is carried forward in its prohibition: nothing is
ever populated from, or mapped onto, the tenant under another name.

The `organizationId` fields that exist today (accounting GL mapping defaults and event DTOs, the customer module's
`ext_organization_postal_address` replica, the location `ORGANIZATIONAL` parent type) are a remnant of an earlier
model, not a designed concept: no module owns an organization aggregate and nothing provisions one. They are not
tenancy, not account, and are not touched by this ADR. Their removal is separate cleanup, tracked outside this
decision, and no new `organizationId` field is introduced anywhere.

#### 5. Global data is an explicit whitelist

**Decision:** ✅ **Resolved** - A table is tenant-scoped by default. A module lists its global tables in
`tenancy-global-tables.txt` with a one-line justification, and the entity carries `@TenantGlobal`. Global tables
have no `tenant_id` requirement and no policy. Expected members: vehicle reference data (NHTSA, CarAPI), fitment,
tax rate tables, the `permissions` catalog, Flyway history, the `ext_tenant` replicas (the public projection of the
registry, §7), and the outbox and processed-event ledgers, which carry `tenant_id` as plain data so events stay
attributable. The registry's own tables in `pos-tenant` are not global; see §7.

#### 6. Roles are tenant-scoped

**Decision:** ✅ **Resolved** - `roles`, `role_permissions`, and `role_assignments` in `pos-security-service` are
tenant-scoped tables. `roles.name` is unique per `(tenant_id, name)`. The `permissions` catalog stays global: it is
code-first, versioned by `CATALOG_VERSION`, and encoded into `perm_bits` ([ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md) §2, [ADR-0025](0025-permissions-yaml-registration-policy.adr.md)).

- **Template.** Today's role seed migrations (`V3__seed_candidate_roles`, `V8__seed_self_service_customer_role`,
  `V24__seed_controller_role`, `R__seed_reference_security.sql`) become a role template held as data in the platform
  tenant. Provisioning applies the template to the new tenant: roles, their permission grants, MCP persona
  attributes, and the [ADR-0061](0061-location-scope-authorization-ownership.adr.md) `location_scope` and
  `location_hierarchy` attributes.
- **Canonical names are immutable per tenant.** A role created from the template carries `template_key NOT NULL`
  and rejects rename and delete, because the frontend gates navigation on role names
  ([ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md) §6) and `ROLE_*` checks exist in code. A tenant
  may change a template role's permission grants and may add custom roles without restriction.
- **Claims are unchanged in shape.** The `roles` claim carries the tenant's role names; `perm_bits`, `perm_ver`,
  `loc_fin_bits`, `loc_oth_bits`, and `loc_scope` derive exactly as today from the tenant's rows.
  `role_assignments` remains the only store of a user's roles (ADR-0061 amendment of 2026-09-09).
- **Foreign keys.** `role_permissions` and `role_assignments` reference `roles (tenant_id, id)`; see §9.

#### 7. Tenant registry: the `pos-tenant` module, accounts, and the platform tenant

**Decision:** ✅ **Resolved** - A new domain module **`pos-tenant`** (package `com.positivity.tenant`, database
`pos_tenant_db`, Eureka `TENANT`, gateway route `/tenant/v1/**`) owns the master tenant table and the account that
owns each tenancy. `pos-security-service` does not own the registry; it consumes it.

- **Aggregates.** `account` (the customer of Durion: legal name, trading name, status, tax id, home country and
  currency), `account_contact` (name, role `OWNER` / `BILLING` / `TECHNICAL`, email, phone; several per account),
  `billing_profile` (billing address, payment terms, invoicing email, payment-processor customer token; never a card
  number), and `tenant` (`id`, `slug`, `display_name`, `status`, `account_id`, cell or region, and timestamps for
  created, activated, suspended, decommissioned). One account may own several tenants. Plans and subscriptions
  attach to `tenant` later without changing this shape.
- **Access posture.** `pos-tenant` runs inside the platform tenant: every one of its rows carries `tenant_id` equal
  to the platform tenant's id, so RLS protects account, contact, and billing data by the same mechanism as every
  other table, and there is still no unbound path. Only platform staff can reach it. A tenant reading
  `GET /v1/tenants/me` is served from the `ext_tenant` replica in `pos-security-service`, never from `pos-tenant`.
  Tenant self-service edits to contacts or billing are out of scope for v1; if added, they arrive as command
  events on `tenant.commands.v1`, not as tenant-bound reads of platform rows.
- **Public projection and events.** `tenant.events.v1` (keyed by tenant id, [ADR-0044](0044-platform-event-only-domain-walls.adr.md))
  carries `tenant.created`, `tenant.provisioned`, `tenant.updated`, `tenant.suspended`, `tenant.reactivated`, and
  `tenant.decommissioned` with the public projection only: `id`, `slug`, `display_name`, `status`. Every module keeps
  that projection in a global `ext_tenant` replica. Account, contact, and billing data never leave `pos-tenant`.
- **Status machine.** `PENDING` on create; `ACTIVE` once `pos-security-service` has emitted `tenant.provisioned`;
  `SUSPENDED` and back to `ACTIVE`; `DECOMMISSIONED` is terminal. Login for a tenant that is not `ACTIVE` returns the
  same 401 as bad credentials. Every handler is idempotent on tenant id, so retries are safe.
- **Provisioning.** The create request carries the initial administrator's email. `pos-security-service` handles
  `tenant.created` under `TenantContext.runAs(newTenantId)`: applies the role template (§6), creates the initial
  administrator, writes the first `role_assignments` row, and emits `tenant.provisioned`. Other modules seed their
  own per-tenant defaults (chart of accounts, GL mapping defaults) from `tenant.created`, inside their own domain.
- **Platform tenant.** A `pos-tenant` migration bootstraps one reserved tenant (slug `platform`) under a constant
  UUID published by `pos-tenancy-common`, so `pos-security-service` can bootstrap its own platform users and role
  template before the first event flows. That tenant's role template is the only one containing
  `ROLE_PLATFORM_ADMIN` and the `platform:tenant:{create,read,update,suspend,reactivate}` and
  `platform:account:{create,read,update}` permission families ([ADR-0025](0025-permissions-yaml-registration-policy.adr.md)).
  There is no unbound session, no root tenant, and no "see every tenant" mode anywhere in the runtime; platform
  work reads platform-tenant rows and global replicas under a normal binding.
- **Frontend.** The platform-admin pages (`/app/admin/tenants`, and accounts alongside them) consume a generated
  `@durion-sdk/tenant` package; `@durion-sdk/security` changes only for `tid` and `tenantSlug`.

#### 8. Asynchronous work and scheduled jobs

**Decision:** ✅ **Resolved** - `DomainEventEnvelope` gains a required `tenantId`, also emitted as a Kafka header
`tenantId` by every outbox publisher. A consumer `RecordInterceptor` in `pos-tenancy-common` binds and clears
`TenantContext` around every `@KafkaListener`; a record without the header is rejected to the dead-letter path.
Every `@Scheduled` method is either per-tenant (`TenantIterator.forEachActiveTenant`, reading the module's
`ext_tenant` replica) or annotated `@PlatformScoped`, which runs unbound and may touch only global tables. A
misclassified job reads zero rows and cannot insert; it is a no-op, never a leak. `TenantContext` propagates through
`TaskDecorator` for `@Async` and `CompletableFuture` work.

#### 9. Schema conventions

**Decision:** ✅ **Resolved** - Per tenant-scoped table: unique constraints re-scoped to `(tenant_id, ...)`; a
composite index `(tenant_id, <hot column>)` where a hot query exists; `UNIQUE (tenant_id, id)` as the foreign-key
target; and foreign keys between two tenant-scoped tables widened to `(tenant_id, id)` **by default**, because
foreign-key checks run with the owner's privileges and bypass RLS. Hibernate maps such associations with
`@JoinColumns` on `(tenant_id, <fk>)`. A single-column foreign key remains only where the target is `@TenantGlobal`.
Business numbering (order, invoice, workorder numbers) moves from shared sequences and counters to per-tenant
counters. One `V<next>__tenancy.sql` per module is generated from a shared template; existing migrations are not
edited. Seed migrations that write scoped rows bind `app.current_tenant` inside the migration transaction so they
behave the same whether the owner is a superuser (Compose, alpha) or not (managed Postgres, where `FORCE` applies to
the owner).

#### 10. Deployment: pooled and dedicated cells from the same images

**Decision:** ✅ **Resolved** - A tenant cell hosts one *or more* tenants. A pooled cell serves many customers on one
Postgres instance; a dedicated cell serves one customer who needs physical isolation. Both run the same images;
tenancy is a data property, not a build property. The unit of isolation moves from the deployment boundary to
`tenant_id` plus RLS. Dedicated cells become a premium or regulatory option, not the only option.

#### 11. Caching, storage, and observability

**Decision:** ✅ **Resolved** - Every Spring cache key is prefixed with the bound tenant through a shared
`TenantKeyGenerator` (`pos-customer`, `pos-catalog` today). Document and image storage paths become
`<tenant_id>/<existing path>`. `pos-mcp-server` conversations, tool traces, and cached LLM context are tenant-scoped
rows and agent tools cannot be asked to switch tenant. Every log line carries an MDC `tenantId`; spans carry it as a
resource attribute; metrics use a bounded tenant label or exemplars, never a high-cardinality label by default.

#### 12. Test infrastructure

**Decision:** ✅ **Resolved** - Every test that touches a repository runs on Testcontainers Postgres. H2 has no RLS and
no `current_setting()`, so an H2 test cannot prove isolation and would pass while it is missing. H2 remains only for
schema-free unit tests. A scripted cross-tenant probe (log in as tenant A, request every listed GET with tenant B's
ids, expect only 404s) runs in CI against the integration cell.

---

### Amends

These documents change as follows. ADR-0023 and its checklist were updated in the change that accepted this ADR;
every other row was applied on 2026-09-09 as plan WS0 (each amended document carries a dated amendment block
pointing back here):

| Document | Change |
| --- | --- |
| [ADR-0023](0023-remove-tenantid-single-organization-context.adr.md) | Status `SUPERSEDED BY ADR-0062`; §2 (distinct `tenantId` / `organizationId`) survives as §4 here |
| [ADR-0023 removal checklist](0023-tenantid-removal-checklist.md) | Status `CLOSED, superseded` |
| [Foundation-First Tenant Cell Deployment Architecture](../architecture/deployment/FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md) | "Decisions This Architecture Locks In": "per-organization isolated deployment model" becomes "pooled or dedicated tenant cells"; "deliberately avoids a shared multi-tenant database model" is withdrawn; Tenant Data Boundary section rewritten around `tenant_id` + RLS |
| [ADR-0045](0045-autonomous-environment-lifecycle-management.adr.md) | Cost model and lifecycle actions per *pooled* cell; per-tenant export replaces per-cell `pg_dump` for offboarding |
| [ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md) | §2 and §3: `tid` required in access and refresh tokens; §1: roles are tenant-scoped rows from a template |
| [ADR-0011](0011-api-gateway-security-architecture.adr.md) | `X-Tenant-Id` / `X-Tenant-Slug` in the injected and stripped header lists |
| [ADR-0044](0044-platform-event-only-domain-walls.adr.md) | Envelope gains required `tenantId`; Kafka header contract |
| [ADR-0061](0061-location-scope-authorization-ownership.adr.md) | Role attributes (`location_scope`, `location_hierarchy`) live on tenant-scoped role rows; location ids in `loc_scope` are tenant-scoped |
| `.ai/GLOSSARY.md`, `domains/security/security-questions.md`, `knowledge-catalog/adr/index.md` | `tenantId` is implemented; DECISION-INVENTORY-008 wording updated |
| `durion-positivity-backend/AGENTS.md`, `durion-positivity-frontend/AGENTS.md`, `CLAUDE.md` minimum ADR lists | Add ADR-0062; add `pos-tenant` to the module tables |

---

### Alternatives Considered

1. **Keep ADR-0023 and scale by deploying one tenant cell per customer.** Rejected: 26 databases, pools, Flyway
   runs, and one full runtime per customer; provisioning and upgrades multiply by customer count. Retained only as
   the dedicated-cell option (§10).
2. **Schema per tenant (Hibernate `SCHEMA` multitenancy).** Rejected: 26 services x N tenants x ~500 tables per
   schema; Flyway runs per schema; `pg_catalog` bloat; connection pools cannot be shared efficiently; every
   cross-tenant platform job becomes a schema loop.
3. **Shared schema with application-side filtering only (Hibernate `@TenantId` or `@Filter` alone).** Rejected: one
   missed predicate in a native query or `JdbcTemplate` call is a cross-tenant leak, and the code base already has 21
   such files. Hibernate stays as the second layer, not the only one.
4. **Per-tenant or per-service database roles for RLS (`SET ROLE`).** Rejected: role churn per tenant, grants to
   maintain per role, a credential per customer to protect, and no benefit over a session setting for a
   discriminator-column design. Tenants are rows, not roles.
5. **A root or platform tenant that sees every row (Hibernate `isRoot`, a `BYPASSRLS` role).** Rejected: any
   unbound path is a leak waiting for a misclassified job; platform work reads global tables under a normal
   binding instead (§7).
6. **Registry inside `pos-security-service`.** Rejected in favour of §7: account, contact, and billing data is a
   vendor-side bounded context with its own callers and change cadence, security-service is already one of the
   heaviest retrofit targets, and every module needs an event-fed `ext_tenant` replica regardless of who owns the
   source. Security-service becomes one more consumer.
7. **Global roles with per-tenant assignments.** Rejected in favour of §6: tenants need their own roles, custom
   roles, persona attributes, and location-scope settings without a platform release; a global catalog would force
   every tenant onto one role set and make `roles` the one table that cannot be provisioned or exported per tenant.
8. **Naming the owning customer an "organization", or mapping `organizationId` onto `tenantId`.** Rejected, as in
   ADR-0023: the existing `organizationId` fields are a remnant with no owner, and reusing the word would tie a new
   aggregate to them. The owning customer is an `account` (§4, §7).

---

### Consequences

#### Positive ✅

- ✅ One runtime and one Postgres instance serve many tenants; provisioning is a row plus an event, not a deployment.
- ✅ Two independent enforcement layers; the database one applies to native SQL, `JdbcTemplate`, bulk HQL, and
  load-by-key alike, and fails closed when nothing is bound.
- ✅ No customer-held database credential and no runtime path that can bypass RLS; the conformance IT proves it.
- ✅ Tenants own their role model; the platform owns the permission catalog. Frontend role gating keeps working
  because template role names cannot change.
- ✅ UUID v7 keys, the event-only domain walls of ADR-0044, and the location-scope model of ADR-0061 carry over
  unchanged in shape.
- ✅ Forward-compatible with hash partitioning by `tenant_id` and with Citus-style sharding if one instance is
  outgrown.

#### Negative ⚠️

- ⚠️ Breaking contract change across every module, the envelope, the token, and the SDK; accepted because the
  platform is in alpha with no data to migrate.
- ⚠️ Roughly 28 to 39 engineer-weeks, dominated by the 26-module retrofit. Mitigated by the shared migration
  template, the scripted superclass retrofit, and agent-run module waves with `pos-location` as the pilot.
- ⚠️ All database tests move to Testcontainers; CI runners need Docker. Mitigated by the existing Testcontainers
  pattern in six modules.
- ⚠️ One `set_config` round trip per connection checkout. Negligible on a local network; measured in the pilot.
- ⚠️ Session-level binding is incompatible with PgBouncer transaction pooling. Recorded constraint; switch to
  `SET LOCAL` before adopting it.
- ⚠️ Per-tenant restore is no longer a `pg_dump` of a cell. Mitigated by a logical per-tenant export (WS6) and by
  dedicated cells for customers who need physical restore.
- ⚠️ Composite foreign keys add a column to every scoped-to-scoped association mapping. Mitigated by the retrofit
  script emitting the `@JoinColumns` form.

#### Neutral

- ℹ️ `tenantId` and `organizationId` remain distinct; nothing that uses `organizationId` today changes.
- ℹ️ Tenant-scoped roles mean a permission catalog change (`CATALOG_VERSION` bump) still ships fleet-wide, exactly as
  today; only the grants that reference the catalog are per tenant.
- ℹ️ Dedicated cells remain possible; a dedicated cell is a pooled cell with one tenant row.

---

### Implementation Notes

- **Components.** New `pos-tenancy-common` (`TenantContext`, `TenantAwareDataSource`, `TenantScopedEntity`,
  `@TenantGlobal`, `@PlatformScoped`, `@TenantAudited`, `TenantIterator`, `TenantKeyGenerator`, the platform tenant
  id constant, the resolver and its `HibernatePropertiesCustomizer`, the Kafka `RecordInterceptor`, the `ext_tenant`
  replica handler template); new `pos-tenant` module (account, contact, billing profile, tenant, status machine,
  `tenant.events.v1` producer, platform-admin API) built on the `pos-location` skeleton; `pos_app` role and grants
  in `postgres/init-databases.sql`; `ext_tenant` consumer, role template, and provisioning handler in
  `pos-security-service`; gateway header changes and the `/tenant/v1/**` route; `DomainEventEnvelope.tenantId`.
- **Configuration.** `SPRING_DATASOURCE_*` = `pos_app`; `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` = owner;
  `environment.tenantResolution: 'host' | 'form'` in the frontend.
- **Sequencing.** WS0 (this ADR and the amendments) → WS1 + WS4 on the `pos-location` pilot → WS2a `pos-tenant`
  and WS2b identity →
  WS3 + WS5 module waves, largest first → WS7 frontend as soon as WS2's OpenAPI is published → WS6 + WS8. Detail in
  the plan.
- **Exit criterion.** The integration cell serves two tenants from one Postgres instance; every module's
  `TenantIsolationIT` and `TenancySchemaConformanceIT` pass on Testcontainers; every service connects as `pos_app`;
  the cross-tenant probe returns only 404s.
- **Monitoring.** MDC `tenantId` on every log line; rows-processed-per-job metric so a permanently zero
  `@PlatformScoped` or per-tenant job is visible; `auth.header.strip.count` already covers stripped tenant headers.
- **Permission catalog.** `platform:tenant:*` and `platform:account:*` are a `CATALOG_VERSION` bump and ships fleet-coordinated, the same trap
  as the 41 to 42 bump in [#389](https://github.com/louisburroughs/durion-positivity-backend/issues/389).

---

### References

- **Related Issues:** [louisburroughs/durion#438](https://github.com/louisburroughs/durion/issues/438) (epic),
  [louisburroughs/durion#439](https://github.com/louisburroughs/durion/pull/439) (plan v0.2)
- **Related ADRs:** [ADR-0011](0011-api-gateway-security-architecture.adr.md),
  [ADR-0013](0013-platform-uuid-identifier-strategy.adr.md),
  [ADR-0017](0017-api-controller-http-response-codes.adr.md),
  [ADR-0023](0023-remove-tenantid-single-organization-context.adr.md),
  [ADR-0025](0025-permissions-yaml-registration-policy.adr.md),
  [ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md),
  [ADR-0044](0044-platform-event-only-domain-walls.adr.md),
  [ADR-0045](0045-autonomous-environment-lifecycle-management.adr.md),
  [ADR-0061](0061-location-scope-authorization-ownership.adr.md)
- **Related Documentation:**
  [ADR-0023 Suppression: Postgres Row-Level Multitenancy Plan](../architecture/plans/adr-0023-suppression-postgres-multitenancy-plan.md),
  [Foundation-First Tenant Cell Deployment Architecture](../architecture/deployment/FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md)
- **External Resources:** [PostgreSQL Row Security Policies](https://www.postgresql.org/docs/16/ddl-rowsecurity.html),
  [Hibernate ORM 7 User Guide: Multitenancy](https://docs.jboss.org/hibernate/orm/7.0/userguide/html_single/Hibernate_User_Guide.html#multitenacy)
  (the `#multitenacy` fragment is the guide's own anchor id, spelled that way upstream; do not "correct" it)

---

### Sign-Off

| Role | Name | Date | Notes |
| --- | --- | --- | --- |
| Chief Architect | | | |
| Security & Authorization Domain | | | |
| Platform Engineering | | | |

---

### Timeline

- **Proposed:** 2026-09-09
- **Under Review:** 2026-09-09 (PR [louisburroughs/durion#444](https://github.com/louisburroughs/durion/pull/444))
- **Accepted:** 2026-09-09
- **Implementation Started:** 2026-09-09 (schema baselines); 2026-09-10 runtime (`pos-tenancy-common`, `pos-location` pilot)

---

### Changelog

- **2026-09-09:** Initial draft from the plan's Appendix A, with the 2026-09-09 decisions: roles tenant-scoped,
  reserved platform tenant, composite foreign keys by default, `NULLIF` policy, verified Hibernate 7.4.5 semantics.
- **2026-09-09 (amendment, same day):** §7 rewritten: the tenant registry moves from `pos-security-service` to a
  new `pos-tenant` module that also owns the `account`, contacts, and billing profile of the customer owning each
  tenancy; the existing `organizationId` fields are recorded as a remnant (§4); alternatives 6 and 8 added.
- **2026-09-09:** Accepted. ADR-0023 marked SUPERSEDED BY ADR-0062; its removal checklist closed.
- **2026-09-09:** WS0 applied: every row of the "Amends" table carries its amendment.
- **2026-09-09:** Schema landed ahead of WS1: the backend's 25 persisting modules were flattened to one
  `V1__baseline_<module>.sql` each, generated from the migrated schema with `tenant_id`, forced RLS and the
  `tenant_isolation` policy, tenant-leading unique constraints and composite foreign keys folded in, plus a
  per-module `tenancy-global-tables.txt`; the WS3 "tenancy migration" step is therefore closed for existing
  tables. The `pos_app` role exists but services still connect as the owner, which carries a transitional
  `app.current_tenant` default until `TenantAwareDataSource` (WS1). Conventions:
  `durion-positivity-backend/docs/TENANCY_SCHEMA.md`.
- **2026-09-10:** WS1 runtime landed (louisburroughs/durion-positivity-backend#1923): `pos-tenancy-common`
  with `TenantContext`, `TenantContextFilter` (401 `TENANT_REQUIRED` in strict mode), `TenantAwareDataSource`
  (`set_config` per checkout, `RESET` on return, PostgreSQL only), `TenantScopedEntity` / `@TenantGlobal` /
  `@PlatformScoped` / `@TenantAudited`, the Hibernate resolver (`isRoot` never true), `TenantIterator`,
  `TenantKeyGenerator` as the default cache key, `TenantRecordInterceptor` plus the `tenantId` record header,
  and the ArchUnit rules; `pos-location` is the pilot, connecting as `pos_app` with Flyway on the owner
  credential and proving isolation on Testcontainers. §9's transitional default now lives in
  `pos.tenancy.default-tenant-id` for adopted modules; the gateway strips inbound `X-Tenant-Id`.
