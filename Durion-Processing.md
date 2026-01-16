# Durion Processing Log

## Request
Update the SRE agent to cover frontend and backend scopes.

## Context
- Repo: /home/louisb/Projects/durion
- Current file in editor: .github/agents/sre.agent.md
- Desired change: Expand SRE agent guidance/checklists to explicitly cover both frontend (Vue/Quasar/TypeScript) and backend (Moqui + POS Spring Boot microservices) operational scopes.

## Action Plan

### 1) Assess current SRE agent scope
- [x] Read current `.github/agents/sre.agent.md` and identify existing sections and gaps.
- [x] Confirm what “frontend” means in this repo (Moqui UI assets, Vue/Quasar app) and what “backend” means (Moqui runtime, POS Spring Boot services).

### 2) Extend SRE agent content for Frontend scope
- [x] Add frontend-specific SLO/SLI suggestions (Core Web Vitals, JS error rate, API latency from browser perspective).
- [x] Add frontend observability guidance (RUM, logging, release/version tagging, source maps).
- [x] Add frontend incident runbooks (blank screen, broken auth, CDN/cache issues, config/env mismatch).

### 3) Extend SRE agent content for Backend scope
- [x] Add Moqui runtime ops guidance (war startup, DB connectivity, job scheduling, service health, logs).
- [x] Add POS microservices ops guidance (per-service health, actuator, dependency chain, message/event receiver).
- [x] Add backend incident runbooks (latency spikes, DB pool exhaustion, event backlog, auth/permissions).

### 4) Align with repo conventions
- [x] Ensure references match repo docs: `docs/OPERATIONS_RUNBOOK.md`, module docs, and existing scripts.
- [x] Keep language consistent with Durion domains (security, RBAC, deployments).

### 5) Validate and finalize
- [x] Quick scan for completeness and duplication.
- [x] Add final summary notes.

## Summary
- Updated `.github/agents/sre.agent.md` to be explicitly platform-wide, covering frontend (browser/UI) and backend (Moqui + Spring Boot) observability.
- Added a standard telemetry contract for shared attributes, plus frontend SLIs (Web Vitals, JS errors, API failures) and backend hotspots.
- Added cross-stack triage flow to encourage correlation from frontend issues through gateway/services/DB.

