## 🏷️ Labels (Proposed)

### Required

- type:story
- domain:platform
- status:needs-review

### Recommended

- agent:architecture
- capability:devops-framework
- phase:F1

### Blocking / Risk

- Blocks all other `devops-*` stories (decision-input foundation)

**Story Intent**

As the platform operator, I want complete, peak-class-labeled utilization metrics collected
per tenant cell into Prometheus, so that the DevOps framework's grooming, right-sizing, and
warm-up decisions have an authoritative, auditable input source.

**Framework reference**

[DEVOPS_FRAMEWORK.md](../architecture/deployment/devops-framework/DEVOPS_FRAMEWORK.md)
(decision D6, phase F1) and the metric-input table in
[RIGHTSIZING_POLICY.md](../architecture/deployment/devops-framework/RIGHTSIZING_POLICY.md).

**Actors & Stakeholders**

- **Primary Actor:** Cell Operations Agent (consumer)
- **Secondary:** Backend services (Micrometer producers), Prometheus (per-cell), node
  exporter / container metrics (EC2 profile), CloudWatch (AWS-native series)

**Preconditions**

- Cell runs the standard compose stack with Actuator enabled on all services.
- Observability scaffolding per `docs/architecture/observability/OBSERVABILITY.md`.

**Functional Behavior**

1. Audit every backend service for Micrometer/Actuator exposure; close gaps so all services
   emit: JVM memory (heap by area, post-GC live set), GC pauses, `http_server_requests`
   with route tags, HikariCP pool metrics, container CPU/memory.
2. Deploy per-cell Prometheus scrape configuration covering all services, node exporter, and
   container metrics; retention ≥ 21 days (supports the 14-day lookback + margin).
3. Define the **route→peak-class mapping** recording rules attributing request metrics to
   the four peak classes (`pos-floor`, `back-office`, `batch`, `integration-external`).
4. Define the decision-input recording rules named in the Right-Sizing Policy (utilization
   percentiles by window class, volume growth rate/days-to-full, GC health, baseline error
   rate and latency per service).
5. Version recording rules in the `durion` repo (control-plane configuration) so decision
   inputs are reviewable.
6. Provide a per-cell Grafana dashboard: utilization vs. limits per service, peak-class
   demand curves, disk projections.

**Acceptance Criteria**

- Every backend service exposes the required series; a coverage check script reports zero
  missing series per cell.
- Peak-class recording rules attribute > 95% of request volume to a class (unmatched routes
  fall to a visible `unclassified` bucket).
- The recording rules referenced by the Right-Sizing Policy resolve with data on a live cell.
- Accelerated-time cells: lookback windows verified to use cell-clock day boundaries.
- Dashboard renders for the integration cell.

**Out of Scope**

- Any autonomous action (later stories); alerting routes beyond existing scaffolding.
