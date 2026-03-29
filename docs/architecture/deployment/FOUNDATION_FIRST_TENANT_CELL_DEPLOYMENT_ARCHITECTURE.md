# Foundation-First Tenant Cell Deployment Architecture

Created: 2026-03-29  
Status: Draft reference architecture  
Scope: Prototype launch foundation for Durion frontend and backend

## Purpose

This document defines the target deployment architecture for Durion as a foundation-first system. It is intentionally focused on structural decisions, operating boundaries, and platform responsibilities rather than an implementation sequence.

The goal is to support:

- prototype launch with realistic end-to-end testing
- durable persistent storage
- tenant isolation by organization
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

Durion should be deployed as an **organization-isolated tenant cell**.

Each paying customer receives a dedicated runtime instance consisting of:

- one frontend runtime
- one API entry layer
- a dedicated set of backend service containers
- tenant-scoped persistent storage
- tenant-scoped secrets and configuration
- tenant-scoped observability identity
- tenant-scoped backup and recovery boundary

This architecture deliberately avoids a shared multi-tenant database model.

## Primary Design Principles

1. **Tenant isolation first**
   - The organization boundary is the core deployment boundary.
   - Compute, configuration, secrets, and persistence must all be attributable to one tenant cell.

2. **Immutable application artifacts**
   - Frontend and backend services are built once into versioned artifacts and promoted across environments.
   - Runtime environments consume artifacts; they do not rebuild code in place.

3. **Environment configuration outside the image**
   - Images stay generic.
   - Tenant identity, endpoints, secrets, clock mode, and feature flags are injected at deploy time.

4. **Time is a platform concern**
   - Accelerated time must be consistent across the tenant cell.
   - No service should have an independent view of simulated time.

5. **Persistent state is tenant-scoped and recoverable**
   - Every tenant cell must support backup, restore, migration tracking, and auditability.

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

A tenant cell is the smallest independently provisioned Durion runtime unit.

Each cell contains:

- frontend container or frontend web runtime
- API gateway
- required backend domain services
- service discovery if still required by the platform design
- eventing components required by the selected feature set
- tenant database runtime and storage attachment, or tenant-dedicated managed data service endpoints
- tenant-scoped observability metadata

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
   - one cell per paying customer
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

A production organization must not share its primary database with other organizations.

Recommended boundary:

- one tenant-specific Postgres instance or one tenant-dedicated Postgres service boundary
- separate credentials per tenant
- separate backup chain per tenant
- separate migration tracking per tenant

For the prototype phase, the main rule is not “use the perfect managed database immediately.” The rule is “do not normalize a shared-database production model that you already know you do not want.”

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
- exportability for tenant offboarding or cloning
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

- tenant identifier
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

Each tenant cell should move through a standard lifecycle:

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
- tenant provisioning and retirement
- secret creation and rotation
- clock mode changes in shared or business-visible environments
- backup restore into an active environment

## Decisions This Architecture Locks In

- Durion is a per-organization isolated deployment model.
- The platform will be release-driven, not host-shell-driven.
- Time simulation is part of the platform contract.
- Persistent storage is tenant-scoped.
- Prototype launch environments should resemble production cells structurally.

## Open Design Questions For The Next Document

The follow-on phased plan should resolve:

1. whether the first tenant data boundary is tenant-dedicated Postgres on host or tenant-dedicated managed Postgres
2. whether service discovery remains in the runtime cell for prototype launch or is simplified
3. where the release definition lives and how frontend/backend versions are composed
4. how clock authority is implemented operationally
5. how tenant provisioning is automated on the current AWS host
6. what minimum smoke suite gates a deployment promotion
7. what backup frequency and restore objectives apply to prototype and production cells

## Summary

The correct foundation for Durion is not a single shared application environment. It is a tenant-cell platform where each organization receives an isolated runtime bundle, isolated persistence, controlled time semantics, and a release lifecycle that can mature from one Docker host into a broader AWS operating model without changing the product's core deployment assumptions.
