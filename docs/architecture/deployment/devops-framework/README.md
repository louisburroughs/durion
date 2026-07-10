# DevOps Framework — Autonomous Environment Lifecycle Management

Specification suite for autonomous tenant-cell grooming, right-sizing, cache pre-loading,
and lean/peak state management.

## Documents

- [DevOps Framework (master spec)](./DEVOPS_FRAMEWORK.md) — governing decisions, Cell
  Operations Agent architecture, peak-class model, cell operating states, action contract,
  substrate profiles, rollout phasing
- [Grooming Catalog](./GROOMING_CATALOG.md) — off-peak hygiene task catalog: disk hygiene,
  process recycling, datastore maintenance, OS-level memory
- [Right-Sizing Policy](./RIGHTSIZING_POLICY.md) — metric-driven sizing of container
  CPU/memory + JVM heap, replica counts, and storage volumes, with guardrails and cost
  accounting
- [Cache Warm-Up Catalog](./CACHE_WARMUP_CATALOG.md) — warm-up job catalog, profitability
  model, and the 10-minute ramp choreography
- [Reporting and Rollback Contract](./REPORTING_AND_ROLLBACK_CONTRACT.md) — after-action
  record schema, digests, notification levels, health-gate/soak/revert/quarantine mechanics

## Related

- [ADR-0045: Autonomous Environment Lifecycle Management](../../../adr/0045-autonomous-environment-lifecycle-management.adr.md)
- [Foundation-First Tenant Cell Deployment Architecture](../FOUNDATION_FIRST_TENANT_CELL_DEPLOYMENT_ARCHITECTURE.md)
- [Phased CI/CD and Runtime Plan](../PHASED_CICD_AND_RUNTIME_PLAN.md)
- Implementation stories: `docs/stories/devops-*.md`
