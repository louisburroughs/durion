---
repo: louisburroughs/durion
title: "[STORY] NLTI Observability & Operations (Metrics, Tracing, Alerts, Runbooks)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As an operator, I want NLTI to be observable and supportable so that failures can be detected quickly and resolved with clear runbooks.

## Functional Behavior
- Collect metrics and traces (correlationId)
- Dashboard for NLTI KPIs
- Alerts for failure rates and latency
- Document runbooks for common failure scenarios

## Acceptance Criteria
- Given NLTI traffic, when observed, then dashboards show request volume, latency, and error rate.
- Given failure spikes, when thresholds are breached, then alerts fire with actionable context.
```

---

## 9) Detailed Backend Story — Observability & Ops (durion-positivity-backend)

```markdown
---
repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Observability: metrics/tracing/alerts + runbooks for top failure modes"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:sre
  - agent:backend
  - agent:story-authoring