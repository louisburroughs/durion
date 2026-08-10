# Architecture Documentation

This directory contains cross-repo architecture documentation for the active Durion platform: backend microservices, the Angular frontend, gateway/security boundaries, deployment target architecture, and supporting implementation plans.

Use this index to find the current architectural source before consulting older notes elsewhere in the workspace.

## How To Use This Directory

- Start with ADRs in [`../adr/`](../adr/) for binding decisions.
- Use the documents below for current architecture, target-state deployment, and execution plans.
- Treat implementation plans as time-bound coordination artifacts, not permanent source-of-truth replacements for ADRs.
- If a document describes an outdated stack or deleted workflow, update or replace it rather than extending the stale version.

## Current Document Index

### Core Architecture

- [Authorization Model](./AUTHORIZATION_MODEL.md) - Canonical explanation of how roles, permissions, `perm_bits`, token claims, gateway decoding, and downstream `@PreAuthorize` checks work together today
- [API Security Architecture](./API_SECURITY_ARCHITECTURE.md) - Current frontend, gateway, JWT, role, and permission boundary aligned to ADR-0011, ADR-0014, ADR-0040, and ADR-0041
- [Backend Contract Global Standards](./api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md) - Normative rules for backend contract guides and OpenAPI source-of-truth boundaries
- [GitHub Branching Strategy](./branching-strategy.md) - Branch naming, protection, PR flow, and release branching conventions

### Integration

- [Supplier Integration Architecture — EDIWheel and Beyond](./integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md) - Proposed ports-and-adapters architecture for outbound tire-manufacturer connectivity over the EDIWheel standard (Michelin first), with configuration-driven vendor profiles, multi-norm version support, and reuse for non-EDIWheel parts distributors

### Deployment And Runtime

- [Deployment Architecture Index](./deployment/README.md) - Entry point for deployment-focused architecture and migration planning
- [Foundation-First Tenant Cell Deployment Architecture](./deployment/FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md) - Reference target architecture for isolated tenant cells and release boundaries
- [Phased CI/CD and Runtime Plan](./deployment/PHASED_CICD_AND_RUNTIME_PLAN.md) - Sequenced rollout plan for runtime, deployment, and promotion capabilities
- [Alpha AWS Provisioning Runbook](./deployment/ALPHA_AWS_PROVISIONING_RUNBOOK.md) - Environment bootstrap runbook for the current AWS provisioning path

### Observability And Request Tracing

- [Observability Architecture](./observability/OBSERVABILITY.md) - Telemetry pipeline, collector role, and tracing/metrics/logging architecture
- [X-Correlation-Id Implementation Plan](./plans/X-Correlation-Id-Implementation-Plan.md) - Platform request-correlation rollout and propagation guidance

### Security And Auth Plans

- [Roles, JWT, and Permissions Implementation Plan](./plans/roles-jwt-permissions-implementation-plan.md) - Cross-repo execution tracker for ADR-0040 token and authorization alignment

### Cloud And Infrastructure References

- [AWS Architecture Overview](./AWS/AWS_DIAGRAM.md) - Diagram-oriented infrastructure overview
- [AWS Network](./AWS/NETWORK.md) - Network-layer notes for the AWS target environment
- [AWS Containers](./AWS/CONTAINERS.md) - Container runtime notes
- [AWS Datastore](./AWS/DATASTORE.md) - Data-layer notes
- [AWS Messaging](./AWS/MESSAGING.md) - Messaging-layer notes
- [AWS CDN](./AWS/CDN.md) - Edge/CDN notes

### Visual References

- [Domain Diagram - POS](./DomainDiagram_POS.drawio.png)
- [Domain Diagram - Domain Layers](./DomainDiagram_POS-Domain%20Layers.drawio.png)
- [Domain Diagram - With External Systems](./DomainDiagram_POS-DomainLayerWExternal.drawio.png)
- [Observability Architecture Diagram](./observability/observability-architecture.png)
- [AWS Fargate Architecture Diagram](./AWS/aws-fargate-architecture.png)

## Status

### Current And Actively Useful

- API security architecture
- Authorization model
- Backend contract standards
- Deployment architecture and provisioning runbooks
- Observability architecture
- Active cross-repo implementation plans under [`plans/`](./plans/)

### Needs Review For Freshness

- AWS overview documents
  - The top-level AWS architecture overview still contains older stack assumptions and should be checked against the current Angular frontend and tenant-cell direction.
- Branching strategy
  - Confirm it still matches the actual repo workflow and contribution process across all sibling repositories.

## Missing Or Underdocumented Areas

The following gaps are visible from the current directory structure and should be treated as documentation TODOs:

- `TODO`: Replace the deleted `COMPONENT_CREATION_WORKFLOW.md` with a current component/service/module creation workflow covering backend services, Angular features, SDK regeneration, and required docs/ADR checks.
- `TODO`: Add a `plans/README.md` that explains what belongs in `docs/architecture/plans/`, how plans differ from ADRs, and when a completed plan should be archived or folded into steady-state docs.
- `TODO`: Restore a current architecture ownership map or service/module catalog to replace the removed `projectOrgCharts/` material.
- `TODO`: Refresh the AWS documentation set so it no longer describes the older Vue/static-asset architecture where the active platform uses Angular plus gateway-backed APIs.
- `TODO`: Add a canonical “developer runtime topology” document that explains local and deployed request flow across frontend, SDKs, gateway, security service, and downstream microservices.
- `TODO`: Add a current procedure for introducing a new externally exposed gateway route, including ADR checks, security review points, OpenAPI expectations, and documentation updates.
- `TODO`: Add a current procedure for auth contract changes covering token claims, frontend role gating impact, gateway authority derivation, and downstream compatibility validation.
- `TODO`: Add an architecture doc for SDK lifecycle and regeneration flow across `durion-positivity-backend`, `durion-positivity-sdk-angular`, `durion-positivity-sdk`, and `durion-positivity-frontend`.

## Contributing

When adding or updating architecture documentation:

1. Prefer updating the existing canonical document instead of creating a parallel note.
2. Link the governing ADRs directly when the document implements or operationalizes a decision.
3. Update this README when adding, removing, renaming, or relocating an architecture document.
4. If a procedure is missing, add a TODO entry here until the canonical document exists.
5. When retiring stale docs, remove them from this index in the same change.

## Related Documentation

- [Workspace README](../../README.md)
- [ADRs](../adr/README.md)
- [ONBOARDING](../ONBOARDING.md)
- [Observability Canonical Doc](./observability/OBSERVABILITY.md)

---

_Last Updated: 2026-04-26_
