# Integration Tenant Cell Notes

Created: 2026-03-29  
Status: Draft reference manifest notes  
Manifest: [integration-tenant-cell.aws-docker-host.yaml](./integration-tenant-cell.aws-docker-host.yaml)

## Purpose

This document explains the first integration tenant-cell manifest for Durion on the current AWS Docker host.

The manifest is designed to be:

- environment-specific
- tenant-aware
- artifact-driven
- portable toward future deployment automation

It is intentionally not a raw `docker-compose.yml` replacement. It is a higher-level release and environment definition that can later render to compose, ECS task definitions, or another runtime target.

## Intended Environment

This manifest represents the shared integration cell described in the phased plan.

It is meant to support:

- realistic end-to-end testing
- controlled release promotion
- tenant-scoped persistence
- observability validation
- accelerated-time testing in a non-production environment

## Key Design Choices

### 1. Single Named Cell

The integration environment is modeled as a single named tenant cell:

- cell id: `int-shared-01`
- tenant id: `integration-shared`
- environment: `integration`

This is not a paying-customer tenant, but it still behaves like a tenant cell so the deployment model stays consistent.

### 2. Minimal Useful Service Set

The manifest includes a baseline runtime service set instead of every backend module.

Selected services:

- `frontend-web`
- `pos-api-gateway`
- `pos-service-discovery`
- `pos-security-service`
- `pos-catalog`
- `pos-customer`
- `pos-inventory`
- `pos-price`
- `pos-order`
- `pos-workorder`
- `pos-accounting`
- `pos-event-receiver`
- `pos-events`

Support services:

- `postgres`
- `otel-collector`
- `prometheus`
- `grafana`
- `jaeger`
- `backup-runner`

This is the first balanced set that should support meaningful UI-driven integration testing without forcing the entire backend surface area into every deployment.

### 3. Tenant-Scoped Persistence on the Current Host

The manifest assumes the current AWS Docker host remains the runtime substrate for now, but persistence is still made tenant-cell-specific through:

- dedicated named Docker volumes per cell
- dedicated Postgres database and credentials per cell
- tenant-specific document storage paths
- tenant-specific backup outputs

This is still weaker than a dedicated managed database, but it preserves the right isolation model.

### 4. Clock Configuration Is Environment-Level

The manifest declares a `clock` block for the entire cell.

Default mode:

- `real_time`

Allowed modes:

- `real_time`
- `accelerated`
- `stepwise`
- `frozen`

This keeps time semantics explicit in the deployment definition rather than hidden inside service-local flags.

### 5. Release Definition Compatibility

The manifest uses version placeholders such as `${RELEASE_VERSION}` and `${IMAGE_TAG}` so it can be driven by a future release assembly workflow.

## Mapping to Current Repositories

### Frontend

The frontend repository does not yet expose a finalized runtime container in the current deployment docs, so this manifest assumes a future SSR-capable container image named `frontend-web`.

### Backend

The backend repository already contains:

- service Dockerfiles
- an existing `docker-compose.yml`
- observability scaffolding

The manifest reuses that shape conceptually but expresses it as a tenant-cell deployment definition instead of a developer-centric compose file.

## What This Manifest Is For

Use this manifest to:

- define the first integration environment contract
- drive future deployment tooling
- create environment inventory entries
- anchor release-definition discussions
- decide which services are mandatory in the first integration cell

## What This Manifest Does Not Yet Solve

- the exact rendering pipeline into Compose or ECS
- the final secret manager integration
- the final image registry naming convention
- dynamic service topology per feature bundle
- production-grade multi-host failover

## Recommended Next Implementation Steps

1. create a renderer that converts this manifest into environment-specific Docker Compose files for the current host
2. define the release-definition file that supplies image tags and manifest variable values
3. add a bootstrap secrets document describing each `secretRef` named in the manifest
