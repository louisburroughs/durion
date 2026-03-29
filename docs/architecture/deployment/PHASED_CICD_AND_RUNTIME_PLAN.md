# Phased CI/CD and Runtime Plan

Created: 2026-03-29  
Status: Draft implementation plan  
Depends on: [Foundation-First Tenant Cell Deployment Architecture](./FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md)

## Purpose

This document turns the tenant-cell deployment architecture into a phased implementation plan. It defines the order of work needed to establish a durable runtime model, real CI/CD behavior, tenant-scoped persistence, accelerated-time support, and agent-compatible operating practices.

This is not a "ship the fastest possible thing" plan. It is a foundation-first sequence intended to launch the prototype in a way that supports realistic end-to-end testing and future growth.

## Outcomes

When these phases are complete, Durion should have:

- a repeatable tenant-cell deployment model
- tenant-scoped persistent storage and backup procedures
- coordinated frontend and backend build pipelines
- controlled promotion from integration to prototype to production tenant cells
- environment-level support for accelerated time
- baseline observability and recovery procedures
- agentic work practices with clear human approval boundaries

## Planning Assumptions

- Frontend and backend remain in separate repositories.
- The current AWS compute instance with Docker remains the first runtime substrate.
- One paying customer equals one isolated runtime cell.
- Prototype environments are used to reveal functional design holes, not just to demo UI.
- Backend services continue to rely on injected time semantics.
- The target operating model should stay portable toward a managed AWS container platform later.

## Phase Overview

| Phase | Name | Primary Goal |
|---|---|---|
| 0 | Control Plane Baseline | Establish versioning, release metadata, environment definitions, and repo workflow guardrails |
| 1 | Deterministic Integration Runtime | Create a reproducible shared integration environment with persistence and observability |
| 2 | Artifact-Centered CI | Build and publish trustworthy, versioned release artifacts from frontend and backend |
| 3 | Controlled CD to Tenant Cells | Promote release definitions into prototype tenant cells with health gates and rollback mechanics |
| 4 | Time Simulation Platform | Make accelerated time a managed tenant-cell capability rather than an ad hoc test tool |
| 5 | Production-Ready Tenant Operations | Add tenant provisioning, backup/restore drills, security hardening, and operational governance |

## Phase 0: Control Plane Baseline

### Goal

Create the minimum control-plane structure required for a real CI/CD lifecycle.

### Why This Comes First

Without a control plane, every later deployment becomes a one-off shell exercise. This phase defines the objects that the rest of the system will operate on.

### Workstreams

#### Release Definition Model

Create a release definition format that captures:

- frontend artifact version
- backend service image versions
- environment configuration references
- migration expectations
- clock mode configuration
- smoke-test requirements
- deployment metadata

Recommended direction:

- keep the release definition in a dedicated deployment or environment repository, or in the shared `durion` repo under a clearly owned deployment path
- treat this definition as the deployable object promoted across environments

#### Environment Inventory

Define and version the initial environment inventory:

- local developer environment
- shared integration environment
- one or more prototype tenant cells
- future production tenant cells

Each environment record should declare:

- tenant identifier
- DNS/subdomain intent
- secret scope
- persistence endpoints
- clock policy
- deployment target host or substrate
- observability tags

#### Branching and Promotion Alignment

Align the plan to the existing branching strategy in [branching-strategy.md](../branching-strategy.md).

Working rule:

- feature work merges into `develop`
- release candidates are assembled from `develop`
- production promotions come from approved release definitions tied to reviewed code states

#### Human Approval Policy

Document approval boundaries for:

- schema changes
- production promotions
- tenant provisioning
- secret rotation
- clock-mode changes outside local development
- destructive restore or cleanup operations

### Deliverables

- release definition schema
- versioned environment inventory
- deployment repository or deployment directory ownership model
- documented promotion policy
- approval matrix for sensitive operations

### Exit Criteria

- a release definition can describe a full tenant-cell deployment without ambiguity
- every non-local environment has a versioned record
- the team knows which actions are automated and which require approval

## Phase 1: Deterministic Integration Runtime

### Goal

Stand up a repeatable shared integration environment that behaves like a tenant cell and is suitable for realistic end-to-end testing.

### Why This Comes Before Full CD

CD is only meaningful if there is a known-good target to promote into. This phase creates that target.

### Workstreams

#### Compose-to-Cell Runtime Assembly

Refine the current runtime assembly so it can represent a single named tenant cell rather than a generic local stack.

Needed changes:

- environment-specific compose overlays or equivalent manifest layering
- explicit tenant naming and tagging
- externalized configuration files
- persistent volume mapping by tenant
- deployment-time service enablement by feature set

#### Tenant-Scoped Persistence

Establish the first tenant storage model with production-compatible boundaries.

Recommended first step:

- one tenant-dedicated Postgres runtime boundary per prototype tenant cell
- dedicated credentials per tenant
- migration history tracked per tenant
- backups automated from day one

Avoid:

- normalizing a shared Postgres instance as the intended production model
- manual database setup with undocumented credentials

#### Runtime Secrets and Configuration

Introduce structured secret and configuration loading:

- per-environment `.env` or secret reference files for bootstrapping only
- migration toward managed secret storage as early as practical
- no secrets inside container images
- no drift-prone hand edits on the runtime host

#### Baseline Observability

Use the existing backend observability scaffolding as the baseline cell telemetry model.

Require:

- release version tagging
- tenant tagging
- environment tagging
- service health and uptime visibility
- gateway and application error visibility

#### Frontend-to-Backend End-to-End Path

Ensure the integration environment exposes a real user path through:

- frontend runtime
- gateway
- required backend services
- persistent data store

This phase should validate that the runtime is suitable for realistic exploratory testing, not just service boot.

### Deliverables

- versioned integration environment definition
- tenant-scoped integration database setup
- initial backup script or managed backup job
- baseline observability dashboards and alert rules
- documented bootstrap procedure

### Exit Criteria

- the integration tenant cell can be rebuilt reproducibly
- data survives container restarts
- the environment can be smoke tested end to end
- logs, metrics, and traces identify tenant and release

## Phase 2: Artifact-Centered CI

### Goal

Upgrade CI from repository-local checks into a coordinated artifact production system.

### Workstreams

#### Frontend CI Hardening

Expand the frontend pipeline to consistently produce:

- tested build artifacts
- container images for runtime delivery
- artifact metadata including commit SHA, branch, and release candidate version

Maintain and extend existing gates:

- accessibility
- i18n validation
- unit tests
- build verification

#### Backend CI Hardening

Build on the existing backend workflows to produce:

- tested service artifacts
- versioned container images
- module-aware change detection
- migration validation where applicable
- release metadata for each service image

Strengthen weak spots:

- image push should be tied to explicit release semantics, not just any `main` push
- changed-service detection should be part of release assembly, not the only deployment input

#### Contract and Assembly Verification

Add integration checks that validate release composition across repos:

- frontend version resolves against expected API routes
- gateway routing and auth boundaries still work
- required backend services for a target feature set are present

Recommended direction:

- a cross-repo release assembly workflow triggered from the control-plane repository

#### Artifact Registry and Naming

Standardize registry and tagging policy:

- immutable image tags by commit SHA
- human-friendly release tags
- explicit environment promotions recorded outside the image tag alone

### Deliverables

- frontend image build and publish workflow
- backend image build and publish workflow with release metadata
- shared artifact naming convention
- release assembly validation workflow

### Exit Criteria

- every deployable component is built as an immutable versioned artifact
- a release definition can reference registry artifacts only, with no host-side rebuild step
- CI outputs enough metadata to support promotion and rollback

## Phase 3: Controlled CD to Tenant Cells

### Goal

Implement real deployment promotion into prototype tenant cells.

### Workstreams

#### Deployment Orchestration

Create a deployment workflow that:

- reads a release definition
- resolves environment configuration and secrets
- pulls approved images
- applies database migrations safely
- deploys services in dependency-aware order
- runs health and smoke checks
- records deployment outcome

The runtime action can initially target the current AWS Docker host, but it should operate from declarative manifests rather than ad hoc shell sessions.

#### Promotion Model

Use explicit promotion stages:

1. CI builds and validates artifacts
2. release definition assembled
3. integration environment promotion
4. prototype tenant-cell promotion
5. production tenant-cell promotion after approval

#### Rollback and Forward-Fix

Define rollback expectations by component type:

- stateless services can usually roll back to the prior release definition
- schema changes require explicit compatibility rules
- destructive migrations require forward-fix or restore playbooks

#### Deployment Verification

Each promotion should run:

- service health checks
- tenant-aware smoke tests
- gateway route validation
- database connectivity checks
- frontend availability validation

### Deliverables

- deployment workflow for prototype tenant cells
- promotion records
- rollback runbook
- smoke-test suite tied to release promotion

### Exit Criteria

- a reviewed release definition can be promoted without manual host surgery
- deployment results are recorded and auditable
- failed promotions stop automatically before a tenant is declared healthy

## Phase 4: Time Simulation Platform

### Goal

Turn accelerated time into an operational capability of the tenant cell.

### Why It Has Its Own Phase

The codebase has already moved toward injected clocks. The remaining challenge is operational consistency and lifecycle control.

### Workstreams

#### Time Authority Design

Define how a tenant cell gets its effective time:

- startup configuration only, or
- runtime-configurable logical clock service, or
- shared configuration backed by a controlled state store

The plan should prefer a model that is:

- auditable
- testable
- consistent across services
- safe to operate in shared environments

#### Clock Modes and Controls

Implement and document:

- real time
- accelerated time
- stepwise simulated time
- frozen time

Each mode change must record:

- who changed it
- when
- from which value to which value
- why
- which tenant cell was affected

#### Time-Sensitive Test Suites

Build end-to-end suites specifically aimed at temporal behavior:

- scheduled work generation
- aging transitions
- reporting windows
- accounting period logic
- SLA or delay-triggered workflows
- audit trail ordering

#### Data Seeding and Scenario Runs

Create repeatable scenario packs that use accelerated time to populate realistic data over simulated elapsed periods.

Examples:

- new tenant first 90 days
- shop workload ramp-up
- accounting month close
- customer re-engagement lifecycle

### Deliverables

- tenant-cell clock control model
- auditable clock change procedure
- time-mode configuration in release and environment definitions
- scenario seed packs for temporal workflows

### Exit Criteria

- one tenant cell can run accelerated time without ambiguity across services
- time changes are visible and auditable
- temporal regression tests catch clock-related design holes before promotion

## Phase 5: Production-Ready Tenant Operations

### Goal

Move from prototype-capable operation to repeatable production tenant operations.

### Workstreams

#### Tenant Provisioning Workflow

Automate provisioning of a new tenant cell:

- allocate tenant identifier
- create DNS records
- create secret scope
- provision storage boundary
- initialize database and migrations
- seed reference data
- deploy approved release definition
- verify observability and backup jobs

#### Backup and Restore Discipline

Formalize:

- backup frequency
- retention rules
- restore testing cadence
- tenant clone process for debugging or training
- recovery objectives by environment class

#### Security Hardening

Add:

- least-privilege service access
- access-controlled deployment credentials
- audited secret rotation
- hardened host baseline
- network exposure review
- image provenance and vulnerability policy

#### Operational Governance

Create and maintain:

- on-call and alert ownership
- production change windows if needed
- incident runbooks
- release manager responsibilities
- tenant offboarding and archival procedures

### Deliverables

- automated tenant provisioning workflow
- tested backup and restore runbooks
- security baseline checklist
- production operations handbook

### Exit Criteria

- a new tenant cell can be provisioned repeatably
- backup restore has been exercised successfully
- production promotion, recovery, and security responsibilities are clearly owned

## Cross-Cutting Work Practices

### Agentic Delivery Model

Durion should adopt an agent-assisted delivery workflow with explicit scopes.

Agents should routinely handle:

- code implementation inside bounded scopes
- test generation and maintenance
- CI workflow suggestions and updates
- Docker and manifest consistency checks
- documentation drafting and drift detection
- release note preparation
- failed pipeline analysis

Humans must approve:

- production promotions
- schema-breaking migrations
- tenant provisioning and retirement
- backup restore into active environments
- secret creation and rotation
- clock mode changes in business-visible environments

### Pull Request Standards

Every infrastructure or deployment PR should include:

- runtime impact summary
- tenant impact statement
- rollback impact statement
- migration impact statement
- observability impact statement
- clock impact statement if time semantics are affected

### Definition of Done for Deployment-Sensitive Changes

A change is not done unless:

- code is merged
- tests and CI gates pass
- artifacts are versioned and publishable
- documentation is updated if runtime behavior changed
- deployment and rollback implications are understood
- tenant or time-simulation impact is declared

### Recommended Sequencing Inside Repositories

### Frontend

Prioritize:

- image build and runtime packaging
- environment injection strategy
- smoke-test automation against deployed environments

### Backend

Prioritize:

- migration discipline
- service image standardization
- health/readiness contracts
- tenant and release tagging in telemetry
- time-sensitive regression coverage

### Shared `durion` Repository

Prioritize:

- deployment manifests or release definitions
- environment inventory
- runbooks
- tenant provisioning definitions
- promotion records

## Risks to Manage Across All Phases

- treating the current Docker host as the permanent architecture
- allowing shared-database shortcuts to harden into production assumptions
- introducing image builds that differ by environment
- letting clock simulation remain partially service-local
- deploying from branch state rather than release definitions
- skipping restore drills because backups appear to exist
- allowing agents to perform irreversible operations without approval gates

## Suggested Delivery Order

If work must be further decomposed into epics, use this order:

1. release definition and environment inventory
2. integration tenant-cell runtime
3. tenant-scoped persistence and backup baseline
4. frontend and backend immutable artifact pipelines
5. deployment orchestration and smoke gating
6. time authority and temporal scenario testing
7. tenant provisioning automation and production hardening

## Summary

The right path for Durion is to build the control plane first, establish a deterministic tenant-cell runtime second, and then layer in artifact-driven CI, controlled CD, time simulation, and production operations. This sequence respects the existing frontend and backend architecture, supports realistic prototype testing, and creates a platform that can grow without changing the fundamental one-organization-per-cell model.
