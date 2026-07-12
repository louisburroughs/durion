# ADR-0046: Environment Log Level Policy

**Status:** ACCEPTED **Date:** 2026-07-12 **Deciders:** Tech Lead, Backend Lead, Architecture **Affected Issues:** N/A

---

## Context

- **Current State**: Every `pos-*` module in `durion-positivity-backend` sets `logging.level.root: INFO` in its base `application.yml`. The `application-alpha.yml` profile
  files only override a single noisy Eureka logger (`ConfigClusterResolver: WARN`) and otherwise inherit the base configuration. As a result, the alpha environment runs the
  entire fleet at INFO, with several packages at DEBUG — including `org.hibernate.SQL: DEBUG` in pos-inventory (logs every SQL statement), `spring.cloud.gateway: DEBUG` in
  pos-api-gateway, and `com.positivity.mcp: DEBUG` in pos-mcp-server (plus an explicit alpha-profile DEBUG for `com.positivity.mcp.internal.orchestration`).
- **The Problem**: There is no policy governing log verbosity per environment. Deployed environments accumulate high-volume, low-value log output, which inflates log
  storage/ingestion cost, drowns actionable signals, and risks leaking payload data (e.g., SQL statement logging) into shared environments.
- **Drivers**: Log volume and cost control; signal-to-noise for on-call and troubleshooting; incident forensics (precursor context must exist *before* the failure);
  consistency across ~25 Spring Boot modules; preventing "temporary" debug settings from becoming permanent; compliance expectations for production observability.
- **Guiding principle (industry norm)**: Control log *cost* at the ingestion/retention layer (sampling, index exclusion, retention tiers), not by suppressing logs at the
  source. Level suppression at source is irreversible — data that was never written cannot be recovered for a post-incident investigation. This is the prevailing practice at
  large-scale operators (Google SRE, Netflix, AWS guidance): production runs at INFO for application code, with noisy frameworks capped at WARN, and volume managed downstream.
- **Scope**: All Spring Boot services in `durion-positivity-backend` (every `pos-*` module) across all Spring profiles: local/default, `dev`, `docker`, `alpha`, `indus`
  (industrialization), and `prod` (production). Frontend and infrastructure logging are out of scope.

---

## Decision

Adopt a per-environment log level baseline, enforced via each module's `application-{profile}.yml`, paired with ingestion-side volume controls:

| Environment            | Profile  | Root level | Application packages (`com.positivity.*`) | Framework/third-party | Notes                                   |
| ---------------------- | -------- | ---------- | ----------------------------------------- | --------------------- | --------------------------------------- |
| Local / default        | (none)   | INFO       | DEBUG permitted                           | INFO                  | Developer machines only                 |
| Dev                    | `dev`    | INFO       | DEBUG permitted                           | INFO                  | Developer iteration environment         |
| Docker (local compose) | `docker` | INFO       | INFO                                      | WARN for noisy libs   | Parity with deployed shape, still local |
| Alpha                  | `alpha`  | INFO       | INFO                                      | **WARN** (named)      | See sub-decision 1                      |
| Industrialization      | `indus`  | INFO       | INFO                                      | **WARN** (named)      | See sub-decision 2                      |
| Production             | `prod`   | INFO       | INFO                                      | **WARN** (named)      | See sub-decisions 2–4                   |

DEBUG/TRACE is prohibited as a standing default in every deployed profile (`alpha`, `indus`, `prod`) — for any logger, application or framework.

### 1. Alpha baseline

**Decision:** ✅ **Resolved** — Alpha runs at INFO for application packages, matching production shape. Alpha is the last cheap place to observe full application behavior
before production, so it is not made quieter than production. Noisy framework loggers (Eureka resolvers, Hibernate internals, gateway wiretap, etc.) are capped at WARN via a
shared named-logger list. DEBUG/TRACE is permitted only for active troubleshooting, must be scoped to the specific logger under investigation (never root), and **must be
temporary**: reverted as soon as the investigation ends, and never merged as a standing default in `application-alpha.yml`.

### 2. Industrialization and Production baseline

**Decision:** ✅ **Resolved** — Industrialization and Production run at INFO for application packages with named framework loggers capped at WARN. INFO output must be
*operationally meaningful* (state transitions, external calls, business events with correlation IDs) — chatty per-iteration or per-row logging belongs at DEBUG and therefore
does not ship from deployed environments. Cost control is achieved at the ingestion layer (sub-decision 5), not by raising the source level.

### 3. Temporary elevation: incident vs. standing changes

**Decision:** ✅ **Resolved** —

- **During an active incident**, on-call may elevate any named logger (never root) to DEBUG/TRACE in any environment, including production, via the Spring Boot actuator
  (`POST /actuator/loggers/{name}`) **without prior signoff**. Actuator changes do not survive restart and cannot become standing defaults. The elevation is recorded in the
  incident ticket and reviewed post-hoc in the incident retro. Elevations are reverted when the investigation ends.
- **Standing changes** finer than the baseline in `indus`/`prod` profile YAML require explicit Tech Lead (or above) signoff, recorded in the PR that introduces them, plus an
  inline YAML comment naming the justification and this ADR.

### 4. Pinned loggers (all deployed environments)

**Decision:** ✅ **Resolved** — The baseline must not suppress operationally or regulatorily required output:

- **Security and audit loggers**: authentication/authorization decisions, audit trail emitters. These must never be silenced below their designed level, and are excluded from
  ingestion sampling (sub-decision 5).
- **Application lifecycle**: startup/shutdown banner and port/profile confirmation (Spring Boot's own startup INFO lines), so deploy verification does not require log-level
  changes.
- **Health-signal WARNs from platform components** where WARN is the designed early-warning channel (e.g., connection-pool exhaustion warnings). Any logger pinned above or
  below the baseline must be a named logger, documented inline in the profile YAML with a comment, and reviewed like any other override.

### 5. Cost control at ingestion, not at source

**Decision:** ✅ **Resolved** — Log volume and cost in deployed environments are managed in the log pipeline, mirroring large-scale industry practice:

- **Retention tiers**: ERROR/WARN retained at full fidelity for the compliance-mandated period; INFO retained short-term (target: 7–14 days) sufficient for incident forensics.
- **Sampling**: if a service's INFO volume exceeds its ingestion budget, INFO lines may be sampled (e.g., 10%) at the pipeline — never ERROR/WARN, never audit/security
  loggers. Sampling rates are pipeline configuration, tunable without code change or redeploy.
- **Ingestion budgets**: per-service log volume is tracked; a service that blows its budget gets its *logging fixed* (demote chatty lines to DEBUG) rather than its level
  raised.

### 6. Structured logging and correlation

**Decision:** ✅ **Resolved** — All deployed profiles emit structured JSON logs (Spring Boot `logging.structured.format.console: ecs` or equivalent) carrying the correlation
ID already propagated by the platform (`correlationId` in the `ApiError` envelope). Level policy without structure makes routing, deduplication, sampling, and retention
tiering impractical — structure is a prerequisite for sub-decision 5. Local/default and `dev` profiles may keep human-readable console output.

Observability in deployed environments does not depend on log verbosity alone: alerting relies primarily on metrics and traces (see Observability Guide); logs provide the
forensic narrative — including the WARN/INFO precursors leading up to a failure.

---

## Alternatives Considered

1. **ERROR root baseline in indus/prod (previous draft of this ADR)**: Maximizes volume reduction at the source, but destroys incident forensics — the WARN/INFO precursors to
   a failure are never written, and no temporary-elevation process can recover data that never existed. Also stricter than prevailing industry practice (production INFO with
   downstream sampling/retention). Rejected in favor of INFO baseline + ingestion-side cost controls (sub-decision 5).
2. **WARN root baseline in prod**: More conservative than ERROR, still loses the INFO narrative (state transitions, external-call outcomes) that makes post-incident
   reconstruction possible. Rejected for the same forensics reason; WARN is instead applied as a cap on *named framework loggers* only.
3. **INFO everywhere with no ingestion controls**: Simplest, but fails the cost driver — ~25 services at unmanaged INFO inflate ingestion spend indefinitely; rejected.
4. **Mandate alpha only, leave other environments to team discretion**: Fails the consistency driver — 25 modules drift independently; rejected.
5. **Centralized logging configuration (shared config server / common library)**: Better long-term enforcement but a larger infrastructure change than this decision requires;
   deferred, can be a follow-up ADR without changing the policy here.

---

## Consequences

### Positive ✅

- ✅ Full forensic narrative (INFO/WARN precursors) exists for every incident in every deployed environment — no data gaps to reconstruct around.
- ✅ Predictable log ingestion cost via retention tiers, sampling, and per-service budgets — tunable in the pipeline without redeploys.
- ✅ WARN/ERROR entries remain actionable: framework noise capped at WARN by named logger, chatty application lines demoted to DEBUG by budget review.
- ✅ Eliminates standing payload-leaking loggers (e.g., `org.hibernate.SQL: DEBUG`) from shared environments.
- ✅ Incident response is not slowed by approval gates: on-call elevates named loggers immediately, review happens post-hoc.
- ✅ Alpha behaves like production (same levels, same structure), so log-dependent tooling is validated before prod.

### Negative ⚠️

- ⚠️ Requires a log pipeline capable of sampling and retention tiering; until that exists, INFO volume from ~25 services is ingested at full fidelity (mitigated by demoting
  chatty lines to DEBUG during rollout — see Implementation Notes).
- ⚠️ "Operationally meaningful INFO" is a judgment call; enforcement relies on ingestion-budget review and PR discipline rather than a hard rule.
- ⚠️ One-time effort to add/adjust `application-alpha.yml`, `application-indus.yml`, and `application-prod.yml` across all `pos-*` modules, remove standing DEBUG defaults
  from base `application.yml`, and enable structured output in deployed profiles.

### Neutral

- Base `application.yml` (local/default profile) may keep INFO root and developer-oriented DEBUG loggers; the policy only constrains deployed profiles.

---

## Implementation Notes

- **Configuration**: Each `pos-*` module's deployed profiles (`application-alpha.yml`, `application-indus.yml`, `application-prod.yml`) set `logging.level.root: INFO`,
  structured JSON output, and the shared named-logger WARN cap list for noisy frameworks (Eureka `ConfigClusterResolver`, Hibernate internals, gateway wiretap, etc.). Pinned
  loggers (sub-decision 4) are listed explicitly under `logging.level` with a YAML comment naming this ADR.
- **Cleanup**: Remove standing DEBUG loggers from base `application.yml` where they would leak into deployed profiles (pos-inventory `org.hibernate.SQL`, pos-api-gateway
  `spring.cloud.gateway`, pos-mcp-server `com.positivity.mcp`, pos-accounting `security`, pos-archunit), or move them into `application-dev.yml` where they belong. During
  rollout, audit each service's highest-volume INFO lines and demote per-iteration/per-row logging to DEBUG.
- **Pipeline**: Configure retention tiers (ERROR/WARN long-term, INFO 7–14 days) and per-service ingestion budgets; add INFO sampling only where a budget is exceeded and the
  logging cannot reasonably be demoted. Audit/security loggers are excluded from sampling.
- **Temporary elevation procedure**: scoped named logger only (never root); via actuator `POST /actuator/loggers/{name}` (does not survive restart, cannot become a standing
  default); linked to an incident/investigation ticket; reverted at close; reviewed post-hoc in the incident retro (sub-decision 3). Standing prod/indus overrides require Tech
  Lead+ signoff in the PR.
- **Enforcement**: CI check (ArchUnit or profile-YAML lint) asserting per-profile `logging.level.root` values, absence of standing DEBUG/TRACE in deployed profiles, and
  structured-output settings. This check is mandatory, not advisory; PR review checklist supplements it for the "meaningful INFO" judgment.
- **Metrics & Monitoring**: track per-service log ingestion volume before/after rollout; alert on services exceeding ingestion budget; alerting continues to be
  metrics/trace-driven.

---

## References

- **Related ADRs**: [ADR-0018: Audit Actor Fields from Security Context](0018-audit-actor-fields-from-security-context.adr.md) (audit logging must not be suppressed),
  [ADR-0045: Autonomous Environment Lifecycle Management](0045-autonomous-environment-lifecycle-management.adr.md)
- **Related Documentation**: Observability Guide (`docs/architecture/observability/`), Spring Boot logging documentation (`logging.level.*`, structured logging, actuator
  loggers endpoint), Google SRE Workbook (alerting on metrics; logs for forensics), `docs/ERROR_ENVELOPE.md` (`correlationId` propagation)

---

## Sign-Off

| Role         | Name | Date | Notes |
| ------------ | ---- | ---- | ----- |
| Architecture |      |      |       |
| Backend Lead |      |      |       |
| Tech Lead    |      |      |       |

---

## Timeline

- **Proposed**: 2026-07-12
- **Accepted**: 2026-07-12

---

## Changelog

- **2026-07-12**: Initial draft (ERROR baseline in indus/prod)
- **2026-07-12**: Revised to align with large-scale industry practice: INFO baseline in all deployed environments with named framework loggers at WARN; cost control moved to
  ingestion layer (retention tiers, sampling, budgets); structured JSON logging made a prerequisite; incident elevation no longer requires prior signoff (post-hoc review);
  alpha aligned to production shape instead of WARN
- **2026-07-12**: Accepted
