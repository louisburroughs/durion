# Foundation-First Tenant Cell Deployment Architecture

Created: 2026-03-29
Amended: 2026-09-09 ([ADR-0062](../../adr/0062-postgres-row-level-multitenancy.adr.md) — pooled tenant cells)
Status: Draft reference architecture
Scope: Prototype launch foundation for Durion frontend and backend

## Amendment (2026-09-09 — ADR-0062, pooled and dedicated tenant cells)

[ADR-0062](../../adr/0062-postgres-row-level-multitenancy.adr.md) (ACCEPTED 2026-09-09, superseding ADR-0023) changes the unit of tenant isolation in this document. The
original text locked in "one isolated deployment per organization" and "deliberately avoids a shared multi-tenant
database model". Both are withdrawn. What replaces them:

- The **unit of isolation is the `tenant_id` column plus Postgres row-level security**, enforced on every
  tenant-scoped table and mirrored by Hibernate `@TenantId`. Every service connects as one non-owner `pos_app` role
  that cannot bypass RLS; the owner credential is used by Flyway only.
- A **tenant cell hosts one or more tenants**. A *pooled* cell serves many customers from one runtime and one
  Postgres instance; a *dedicated* cell serves one customer who needs physical isolation. Both run the same images
  and the same release definition; tenancy is a data property, not a build property.
- The **tenant registry** is the `pos-tenant` module (master `tenant` table plus the `account` that owns each
  tenancy). Provisioning a tenant into a pooled cell is a row and an event, not an infrastructure action. Provisioning
  a cell remains an infrastructure action and keeps its human approval.
- **Per-tenant export** (logical, by `tenant_id`) is the offboarding and cloning unit. Per-cell `pg_dump` remains the
  disaster-recovery unit for the whole cell.

Sections below are edited in place where the original wording contradicted this; where a section is unchanged, read
"tenant cell" as "pooled or dedicated tenant cell" and "tenant" as "a tenant row within a cell".

## Purpose

This document defines the target deployment architecture for Durion as a foundation-first system. It is intentionally focused on structural decisions, operating boundaries, and platform responsibilities rather than an implementation sequence.

The goal is to support:

- prototype launch with realistic end-to-end testing
- durable persistent storage
- tenant isolation by `tenant_id` and Postgres RLS inside a cell, with physical isolation by dedicated cell where a customer requires it
- accelerated-clock simulation for realistic temporal data generation
- a true CI/CD lifecycle with controlled promotion
- growth from a single Docker host to a more managed AWS platform without re-architecting the product model

## Context

The current codebase already implies a platform shape larger than a single web app:

- `durion-positivity-frontend` is an Angular SSR application with existing build, accessibility, and i18n quality gates.
- `durion-positivity-backend` is a multi-service Spring Boot platform with API gateway, service discovery, observability scaffolding, Dockerfiles, and CI workflows.
- Backend code has already been remediated for injected time sources rather than direct `now()` usage.

Because of that, the deployment target should be modeled as a repeatable tenant platform cell, not as one generic shared environment.

## Architectural Position

Durion should be deployed as a **tenant cell**: a repeatable runtime unit that hosts one or more tenants.

Each cell consists of:

- one frontend runtime
- one API entry layer
- one set of backend service containers
- one Postgres instance holding the per-service databases, every tenant-scoped table carrying `tenant_id` under
  row-level security (ADR-0062)
- cell-scoped secrets and configuration
- cell-scoped observability identity, with `tenant_id` as a mandatory signal dimension
- cell-scoped backup and recovery boundary, with per-tenant logical export for offboarding and cloning

A **pooled cell** hosts many customers. A **dedicated cell** hosts one customer who needs physical isolation, and
is the same cell definition with one tenant row. The shared-schema model is the default; a dedicated cell is a
premium or regulatory option, not the only option. Isolation between tenants in a pooled cell is enforced by the
database, not by the deployment boundary.

## Primary Design Principles

1. **Tenant isolation first**
   - The `tenant_id` column plus Postgres RLS is the isolation boundary inside a cell; the cell is the operational
     boundary.
   - Compute, configuration, secrets, and persistence must all be attributable to one cell; every business row,
     event, log line, and trace must be attributable to one tenant.

2. **Immutable application artifacts**
   - Frontend and backend services are built once into versioned artifacts and promoted across environments.
   - Runtime environments consume artifacts; they do not rebuild code in place.

3. **Environment configuration outside the image**
   - Images stay generic.
   - Tenant identity, endpoints, secrets, clock mode, and feature flags are injected at deploy time.

4. **Time is a platform concern**
   - Accelerated time must be consistent across the tenant cell.
   - No service should have an independent view of simulated time.

5. **Persistent state is tenant-attributable and recoverable**
   - Every cell must support backup, restore, migration tracking, and auditability.
   - Every tenant must be exportable from its cell without the other tenants' data.

6. **Promotion beats direct deployment**
   - CI proves change quality.
   - CD promotes known-good artifacts into target cells with explicit controls.

7. **Human approval at irreversible boundaries**
   - Schema changes, production promotions, secret rotation, tenant provisioning, and destructive recovery remain human-approved actions even in an agentic workflow.

## Reference Runtime Model

### Layer 1: Control Plane

The control plane is the set of systems that describe and operate tenant cells. It does not serve business traffic directly.

Responsibilities:

- source control and pull request workflow
- CI pipelines
- image registry
- deployment manifests and environment definitions
- secrets management
- tenant inventory
- release records
- backup orchestration
- alert routing and operations metadata

The control plane may initially be lightweight, but it must exist conceptually from day one.

### Layer 2: Tenant Cell

A tenant cell is the smallest independently provisioned Durion runtime unit. It hosts one or more tenants; the
tenant registry (`pos-tenant`) records which tenants live in which cell.

Each cell contains:

- frontend container or frontend web runtime
- API gateway
- required backend domain services
- service discovery if still required by the platform design
- eventing components required by the selected feature set
- the `pos-tenant` registry service alongside the domain services
- one Postgres runtime (or managed data service endpoint) holding the per-service databases; tenant rows are
  separated by `tenant_id` and RLS, not by database or schema
- cell-scoped observability metadata carrying `tenant_id` on every signal

Each cell has its own:

- DNS or subdomain mapping
- TLS termination path
- deployment manifest
- secret set
- clock configuration
- backup policy
- restore procedure

### Layer 3: Shared Platform Services

Some platform services may be shared across tenant cells if they do not break tenant isolation.

Possible shared services:

- container registry
- CI runners
- deployment orchestrator
- centralized logging and metrics backends
- secret management control plane
- artifact storage

Shared services must preserve per-tenant tagging, access control, and audit boundaries.

## Deployment Unit

The deployment unit is not a single container and not an entire Git repository.

The deployment unit is a **versioned tenant-cell release definition** containing:

- frontend image version
- backend service image versions
- environment configuration references
- secret references
- clock mode configuration
- database migration version expectations
- health check policy
- rollback metadata

This release definition should become the object promoted across environments.

## Environments

Durion should be modeled with at least the following logical environments:

1. **Developer local**
   - high flexibility
   - local compose or local service runs
   - frequent simulated-time use

2. **Integration**
   - shared environment for cross-service verification
   - validates release assembly, migrations, and core workflows
   - supports accelerated clock scenarios for end-to-end testing

3. **Prototype / pilot tenant cells**
   - organization-specific cells used for realistic operational testing
   - should be close to production in topology and controls

4. **Production tenant cells**
   - pooled cells hosting many paying customers; dedicated cells for customers who require physical isolation
   - controlled promotion only

Prototype cells should not be treated as disposable sandboxes if they are being used to uncover functional design holes through realistic workflows.

## Compute Strategy

### Near-Term Substrate

The current AWS compute instance with Docker is an acceptable first substrate for early tenant-cell hosting if:

- deployments are declarative and repeatable
- data is persisted outside container layers
- backups are automated
- release versions are recorded
- secrets are not embedded in images or ad hoc shell scripts

The host should be treated as an execution substrate, not as the architecture itself.

### Long-Term Direction

The target architecture should remain portable toward a managed AWS container runtime such as ECS. The delivery model should avoid host-coupled assumptions so the same tenant-cell definition can later move without changing application semantics.

## Persistent Storage Architecture

### Tenant Data Boundary

Tenants in a cell share the cell's Postgres instance and the per-service databases and schemas. Isolation between
them is enforced by the database (ADR-0062):

- every tenant-scoped table carries `tenant_id UUID NOT NULL`, has row-level security enabled and forced, and a
  single `tenant_isolation` policy on `current_setting('app.current_tenant')`; with nothing bound a table reads as
  empty and refuses inserts
- every service connects as the one shared, non-owner `pos_app` role, which cannot bypass RLS; the owner credential
  is used by Flyway only and never by an application datasource
- Hibernate `@TenantId` on every scoped entity is the second, independent layer; ArchUnit and a per-module
  schema-conformance test are the build gate
- global reference data (vehicle reference, fitment, tax rates, the permission catalog, tenant replicas) is an
  explicit per-module whitelist with no policy
- there is no per-tenant database, schema, credential, or role; customers never hold a database credential

Per cell, not per tenant: credentials, the backup chain, and Flyway migration tracking. Per tenant: a logical export
by `tenant_id` for offboarding and cloning, and attributable audit and telemetry.

A customer who requires physical isolation gets a dedicated cell: the same definition, one tenant row. The rule for
the prototype phase is now the inverse of the original: do not build anything that assumes one database per
customer, because the pooled cell is the production model.

### Storage Categories

Each tenant cell should distinguish at least these state classes:

1. **Transactional relational data**
   - orders, customers, work orders, pricing state, permissions, accounting records

2. **Document and blob storage**
   - generated documents, images, attachments, exports

3. **Operational metadata**
   - migration history, deployment state, seed runs, simulation markers

4. **Telemetry retention**
   - logs, metrics, traces tagged to tenant and environment

### Persistence Requirements

The architecture should support:

- point-in-time restore objectives appropriate to prototype operations
- versioned schema migrations
- seeded environment rebuilds
- per-tenant logical export (by `tenant_id`, across every service database) for offboarding or cloning; per-cell
  `pg_dump` for disaster recovery
- explicit retention rules for simulated data

## Time Simulation Architecture

### Why It Is First-Class

Durion needs accelerated time to generate realistic timestamped data and expose design holes in workflows that only emerge across elapsed time.

Because time semantics affect:

- scheduling
- event ordering
- reporting
- SLA logic
- aging and statuses
- audit views
- billing and accounting periods

the clock cannot be left as a per-service convenience setting.

### Required Model

Each tenant cell must have a single logical time authority.

That authority may initially be implemented through shared configuration and injected clocks, but architecturally it should behave as:

- one authoritative cell time mode
- one effective current instant for the cell
- one acceleration policy
- one audit trail of time changes

### Clock Modes

The architecture should support at least:

1. **Real time**
   - standard wall clock behavior

2. **Accelerated continuous time**
   - time advances faster than wall clock using a configured multiplier

3. **Stepwise simulated time**
   - time advances in controlled jumps for deterministic test scenarios

4. **Frozen time**
   - useful for repeatable diagnostics and narrow test cases

Production tenant cells will likely run in real time only, but the platform should not assume that all non-production cells do.

### Time-Safe Design Rules

- Services must obtain time from injected platform-compatible clocks only.
- Scheduled jobs must behave consistently when time advances faster than wall time.
- Event timestamps must reflect the effective tenant-cell clock, not container-local wall time.
- Database records that represent business time should be distinguishable from deployment or infrastructure timestamps when necessary.
- Changes to clock mode must be auditable.
- A shared environment must never silently mix real-time and accelerated-time participants for the same tenant cell.

## CI/CD Architecture Implications

### CI Responsibilities

Continuous Integration should prove that a change is releasable at the code and artifact level.

CI should cover:

- frontend build and tests
- backend unit, integration, and architecture tests
- container image builds
- static analysis and dependency checks
- contract verification where applicable
- migration validation
- simulated-time regression scenarios for time-sensitive workflows

CI should produce versioned artifacts and release metadata, not just pass/fail signals.

### CD Responsibilities

Continuous Deployment should be modeled as controlled promotion of a release definition into a tenant cell.

CD should handle:

- selecting artifact versions
- resolving environment configuration
- applying migrations safely
- deploying services in dependency-aware order
- running smoke and health checks
- recording deployment results
- supporting rollback or forward-fix procedures

### Release Coordination Across Repositories

Because frontend and backend are separate repositories, the deployment architecture should assume a release coordination layer that can compose:

- one frontend artifact version
- one or more backend artifact versions
- one tenant environment definition

This coordination layer may be a dedicated deployment repository, manifest repository, or equivalent control-plane source of truth.

## Observability Architecture Expectations

Each tenant cell deployment must emit observability signals with enough metadata to answer:

- which tenant is affected
- which release is running
- which service version produced the event
- which clock mode was active
- which environment the issue occurred in

Minimum required observability dimensions:

- tenant identifier (from the bound tenant context, never from a client-supplied value; MDC `tenantId`)
- cell identifier
- environment
- service name
- artifact version
- deployment identifier
- trace and correlation identifiers
- clock mode

## Security and Secrets Posture

The deployment architecture assumes:

- secrets are stored outside source control
- each tenant cell has its own secret scope
- production access is role-restricted and auditable
- service-to-service credentials are rotated intentionally
- bootstrap credentials are temporary and replaced after provisioning

No environment should rely on manually edited long-lived secrets on the host as the steady-state model.

## Provisioning and Lifecycle Model

Cells and tenants have separate lifecycles. A tenant's lifecycle (`PENDING` → `ACTIVE` ⇄ `SUSPENDED` →
`DECOMMISSIONED`) is owned by `pos-tenant` and driven through its platform-admin API and `tenant.events.v1`;
provisioning a tenant into a pooled cell creates a row, seeds the tenant's roles and initial administrator in
`pos-security-service`, and lets each domain module seed its own defaults from the event. No infrastructure changes.

Each cell should move through a standard lifecycle:

1. **Provisioned**
   - infrastructure, secrets, storage, DNS, and baseline manifests created

2. **Initialized**
   - migrations applied and base configuration loaded

3. **Seeded**
   - optional reference data and scenario data installed

4. **Activated**
   - traffic enabled and health checks passing

5. **Observed**
   - dashboards, alerts, and backup jobs verified

6. **Changed**
   - ongoing release promotions and configuration updates

7. **Recovered or retired**
   - restore, clone, archive, or decommission actions performed through controlled procedures

## Agentic Workflow Implications

This architecture expects an agent-assisted delivery model, but not unrestricted autonomy.

Agents are well suited for:

- drafting infrastructure and deployment manifests
- generating and updating tests
- checking configuration consistency
- reviewing Docker and CI changes
- preparing migration notes and release notes
- validating documentation drift
- analyzing failed builds and failed deployments

Humans retain explicit approval at these boundaries:

- production promotion
- destructive schema or data operations
- cell provisioning and retirement (tenant provisioning into a pooled cell is a platform-admin business action
  with its own audit trail, not an infrastructure approval)
- secret creation and rotation
- clock mode changes in shared or business-visible environments
- backup restore into an active environment

## Decisions This Architecture Locks In

- Durion is a tenant-cell deployment model with pooled and dedicated cells built from the same images; tenancy is
  a data property enforced by `tenant_id` and Postgres RLS (ADR-0062). The earlier "per-organization isolated
  deployment model" and "deliberately avoids a shared multi-tenant database model" decisions are withdrawn.
- The platform will be release-driven, not host-shell-driven.
- Time simulation is part of the platform contract.
- Persistent storage is cell-scoped and tenant-attributable; every tenant is exportable on its own.
- Prototype launch environments should resemble production cells structurally: the alpha cell is rebuilt as a
  pooled cell with at least two seeded tenants.

## Open Design Questions For The Next Document

The follow-on phased plan should resolve:

1. **RESOLVED — Alpha: tenant-dedicated Postgres container on host.** The alpha cell runs `postgres:16-alpine` as a Docker container with a named volume (`postgres-data`) attached to the host. Data persists outside container layers. Credentials are injected via environment variables. Port binding is restricted to `127.0.0.1:5432` to prevent external exposure. All backend services reach Postgres through the internal `pos-network` bridge only. Migration path to managed Postgres (RDS or equivalent) is a configuration-only change to `SPRING_DATASOURCE_URL` — no schema or application changes required. A backup policy must be defined before prototype-phase business data is written (see question 7). *Amended 2026-09-09 (ADR-0062):* the application services connect as the non-owner `pos_app` role (`SPRING_DATASOURCE_*`); the `POSTGRES_USER` owner credential is injected for Flyway only (`SPRING_FLYWAY_*`). On managed Postgres there is no superuser, so seed migrations that write tenant-scoped rows bind `app.current_tenant` inside the migration transaction; the migration template does this from day one.
2. **RESOLVED — Alpha: retain Eureka in the runtime cell.** "Simplified" would mean removing Eureka and replacing all `lb://SERVICE-NAME` gateway route URIs with static Docker Compose DNS URIs (`http://pos-service:8080`), relying on Compose-native hostname resolution instead of a service registry. This is viable on a single host because there is never more than one instance of each service to balance across. However, removal is deferred for alpha because: (1) it requires touching every service `application.yml` and all gateway routes simultaneously at high change cost; (2) Eureka provides health-aware deregistration — a crashed service stops receiving traffic before the Docker healthcheck removes it; (3) the existing `depends_on: condition: service_healthy` chain already serializes startup correctly. **Revisit for ECS migration**, where Eureka is genuinely redundant and AWS Cloud Map or ALB target-group health management replaces it at the platform level.
3. **RESOLVED — Release definitions live in the `durion` repo under a dedicated deployment path.** `durion` is the master coordination project and is the natural control-plane source of truth for versioned tenant-cell release definitions. A release definition is a versioned manifest that pins one frontend image tag, one set of backend service image tags, environment configuration references, secret references, clock mode, and migration version expectations. Frontend and backend CI pipelines each publish a versioned image artifact; the release definition in `durion` composes those independent artifact versions into a single deployable unit. This keeps application repositories responsible for building and testing their own artifacts, while `durion` is responsible for assembly, promotion, and tenant-cell targeting. The existing `docs/architecture/deployment/manifests/` directory is the initial home for these definitions.
4. **RESOLVED — Alpha production uses default UTC wall clock; shared clock authority is deferred to Phase 4.** The current state is:

   - **Backend**: every service accepts `java.time.Clock` by constructor injection and provides `Clock.systemUTC()` as a default bean using `@ConditionalOnMissingBean(Clock.class)`. This is the correct structural foundation — a test profile or future platform layer can substitute a fixed or offset clock without modifying service code.
   - **Frontend**: no platform clock injection exists or is needed. The frontend renders timestamps it receives from backend responses. Business time originates in backend services; display time follows system locale. No frontend clock authority is required.
   - **Production**: the default `Clock.systemUTC()` bean in each service is correct for production. No operational clock configuration is needed at alpha launch.
   - **End-to-end testing**: a shared accelerated or fixed clock across all services in the cell is the requirement. The `@ConditionalOnMissingBean` hook enables this via a test-profile Spring bean override per service, but a true single-authority cross-service clock (where all services share one injected offset) is **not yet implemented** and is deferred to Phase 4 of the phased runtime plan. Until then, E2E time-sensitive scenarios must be run using direct database seeding with explicit timestamps rather than real-time acceleration.
5. **RESOLVED — Alpha: human-executed provisioning script; full automation deferred to Phase 5.** Full terraform/Ansible provisioning is not yet implemented and is not required for a single alpha cell. The alpha procedure is a documented, repeatable operator runbook executed once by a human. Decisions recorded:

   - **Compute**: `t3.2xlarge` (8 vCPU, 32 GB RAM) on AWS, `us-east-1`. The full stack runs 22 containers (14 Spring Boot microservices, API gateway, Eureka, Postgres, otel-collector, Jaeger, Prometheus, Grafana, frontend Node.js). Estimated RAM footprint at idle is ~10 GB; 32 GB provides comfortable headroom. Storage: 80 GB `gp3` EBS root volume. Attach an Elastic IP so the DNS record survives instance restarts.
   - **OS and runtime**: Amazon Linux 2023 (or Ubuntu 22.04). Install Docker Engine and the Docker Compose plugin. No other runtime dependencies on the host.
   - **IAM**: attach an EC2 instance profile (IAM role) with policies: `AmazonEC2ContainerRegistryReadOnly` (pull images from ECR), `SecretsManagerReadWrite` scoped to the `durion/alpha/` prefix (future), `s3:PutObject`/`s3:GetObject` scoped to the backup bucket. No long-lived AWS access keys on the host.
   - **Security Group**: inbound 443 (HTTPS, `0.0.0.0/0`), inbound 80 (HTTP, `0.0.0.0/0` — for Let's Encrypt challenge redirect), inbound 22 (SSH, operator IP only), all outbound.
   - **Domain and TLS**: `durionpos.org`. Create a Route 53 A record pointing to the Elastic IP. Install `nginx` on the host as a TLS-terminating reverse proxy. Obtain a certificate via `certbot --nginx` (Let's Encrypt). Nginx proxies `443 → localhost:4200` (frontend) and `443/api/ → localhost:8080` (gateway).
   - **Container registry**: AWS ECR. One repository per service image (e.g., `durion/durion-positivity-frontend`, `durion/pos-api-gateway`, `durion/pos-accounting`, etc.). The EC2 instance profile grants pull access without a stored credential. For alpha without CI, images are built locally and pushed manually: `aws ecr get-login-password | docker login`, `docker build`, `docker push`.
   - **Secrets**: stored in a `.env` file at `/opt/durion/alpha/.env` with permissions `600`, owned by the operator user. This file is never committed to source control. Required variables: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `POS_EVENTS_API_SECRET`, `OTEL_EXPORTER_OTLP_ENDPOINT`. The `.env` file is sourced by `docker compose --env-file /opt/durion/alpha/.env up -d`.
   - **CI gap**: neither frontend nor backend has a CI pipeline that builds and pushes images. For alpha, a manual build-and-push step substitutes for CI. This is a known gap — see question 6 and Phase 2 of the phased runtime plan.
   - **Full automation**: tenant provisioning automation (Terraform for EC2/ECR/Route53/IAM, Ansible for host configuration) is a Phase 5 deliverable and is not required before alpha launch.
6. **RESOLVED — Smoke suite is gated by a data load step; both are required before a deployment is considered promoted.** A deployment promotion is not complete until the cell passes a two-phase gate:

   **Phase A — Reference data load (prerequisite to smoke)**
   Before the smoke suite runs, a known reference dataset must be loaded into the tenant cell's database. This is required because several smoke routes address specific entities by ID (e.g., `WO-123`, `EMP-123`) — without matching records the pages render in error state, which makes smoke results meaningless. The reference data load must be:
   - idempotent (safe to re-run on an already-seeded cell)
   - versioned alongside the release definition in `durion`
   - executed as a separate named step in the promotion runbook, not silently bundled into migrations
   - recorded in the deployment log with the data version used
   No reference data load implementation exists yet. For alpha, this step is a manual SQL script or API call sequence run by the operator. A seed runner is a Phase 3 deliverable.

   **Phase B — Smoke suite**
   The existing `scripts/a11y/smoke-routes.mjs` in `durion-positivity-frontend` provides the initial smoke route coverage. It hits 8 routes:
   - `/app` — shell load
   - `/app/crm` — customer list
   - `/app/workexec/estimates/new` — new estimate form
   - `/app/workexec/workorders/WO-123` — workorder detail (requires seeded workorder)
   - `/app/accounting/events` — accounting events list
   - `/app/accounting/vendor-payments` — vendor payments list
   - `/app/people/employees/EMP-123` — employee detail (requires seeded employee)
   - `/app/location/locations` — location list

   For a deployment to be considered promoted, all 8 routes must return HTTP 200 with no `critical` accessibility violations (`A11Y_FAIL_ON_IMPACT=critical`). The script is run with `A11Y_USE_EXISTING_SERVER=1` and `A11Y_BASE_URL` pointed at the live cell URL. A failing smoke run blocks promotion and requires a rollback or forward-fix before the cell is considered activated. Backend service health (`/actuator/health` on all services) must also be fully `UP` before the smoke script is invoked.
7. **RESOLVED — Prototype: daily `pg_dump` to S3; restore is a manual operator step. Production policy deferred to Phase 5.** The prototype backup model is intentionally minimal:

   - **Backup mechanism**: a cron job on the EC2 host runs `pg_dump` once per day and uploads the compressed dump to a dedicated S3 bucket using the instance profile (no stored credentials). Example cron: `0 2 * * * pg_dump ... | gzip | aws s3 cp - s3://durion-backups/alpha/$(date +\%Y-\%m-\%d).sql.gz`
   - **Retention**: keep 14 daily dumps. S3 lifecycle rule deletes objects older than 14 days automatically.
   - **Restore procedure**: manual operator step — stop the stack, drop and recreate the database, restore from the target dump file with `aws s3 cp ... | gunzip | psql`, restart the stack. No automated restore tooling required for prototype.
   - **Recovery objective**: best-effort. For a prototype cell running simulated scenarios, losing up to 24 hours of data is acceptable. No RTO/RPO SLA applies until a paying customer's data is at risk.
   - **Production policy**: formal RTO/RPO targets, point-in-time recovery, automated restore drills, and tenant offboarding exports are Phase 5 deliverables and are not defined here.
   - *Amended 2026-09-09 (ADR-0062):* the per-cell `pg_dump` remains the disaster-recovery mechanism and now covers every tenant in a pooled cell at once. Offboarding, cloning, and single-tenant restore are a **per-tenant logical export** by `tenant_id` across all service databases (plan WS6); restoring one tenant from a cell dump is not a supported operation. Customers who need physical restore semantics take a dedicated cell.

## Summary

The correct foundation for Durion is a tenant-cell platform: a repeatable runtime bundle with controlled time semantics and a release lifecycle that can mature from one Docker host into a broader AWS operating model without changing the product's core deployment assumptions. Within a cell, tenants are isolated by `tenant_id` and Postgres row-level security (ADR-0062); a pooled cell hosts many customers, a dedicated cell hosts one, and both are the same definition.
