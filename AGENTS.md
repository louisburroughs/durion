# AGENTS.md — Durion Platform

## Project Overview

Durion is a multi-repo platform that includes:

- `durion-positivity-backend`: POS Spring Boot microservices (Java 21, Spring Boot 4.0.2)
- `durion`: workspace-level coordination, governance, project, and agent docs

This file provides cross-repo guidance only. For backend implementation details, use `durion-positivity-backend/AGENTS.md`.

---

## Documentation Hierarchy & README.md Files

**MANDATORY: Always check for and consult README.md files in your working directory before making changes.**

Documentation follows a strict hierarchy:

1. **Workspace-level**: `durion/AGENTS.md` (this file) — cross-cutting guidance
2. **Project-level**: `durion-positivity-backend/AGENTS.md` — project-specific setup and patterns
3. **Module/Component-level**: `README.md` files in each module directory — module-specific documentation

### README.md Requirements

**Before working in any directory:**

- Check if a `README.md` exists in that directory
- Read and follow the guidance in that README.md
- Consult parent README.md files for context

**When making changes:**

- Update the local `README.md` when adding new features, changing APIs, or modifying module structure
- Document new configuration options, environment variables, or setup steps
- Add examples for new functionality
- Keep setup instructions current

**README.md locations to check:**

- Module root: `pos-accounting/README.md`, `pos-order/README.md`, etc.
- Component root: `runtime/component/durion-*/README.md`
- Feature directories: any top-level directory may have a README.md

**If no README.md exists:**

- Create one when adding significant new functionality
- Include: purpose, setup, key files, common commands, testing instructions

---

## ADR Compliance (Mandatory)

**Before writing or modifying code, agents MUST read applicable ADRs in `docs/adr/`.**

Required workflow:

1. Identify relevant ADRs by scope and date (prioritize latest `ACCEPTED` ADRs).
2. Confirm implementation approach aligns with those ADRs before coding.
3. If code and ADR conflict, follow ADR and document required migration changes.
4. If no ADR covers the decision, proceed conservatively and propose a new ADR when architecture-impacting changes are introduced.

Minimum ADRs to check for backend work:

- `docs/adr/0011-api-gateway-security-architecture.adr.md`
- `docs/adr/0014-gateway-internal-service-security.adr.md`
- `docs/adr/0017-api-controller-http-response-codes.adr.md`
- `docs/adr/0018-audit-actor-fields-from-security-context.adr.md`

This ADR compliance requirement applies to all agents and subagents (including coder/test/deploy/sre).

---

## Agents & Canonical Docs

Agent docs live under `.github/agents/` in this repo. 

Canonical observability architecture doc:

- `docs/architecture/observability/OBSERVABILITY.md`

For backend setup, build, run, testing, package conventions, and event logging requirements, defer to `durion-positivity-backend/AGENTS.md`.

---

## Pull Request & Commit Guidance

- Preface PR titles with the capability issue number: `cap/<cap-id>`
- Required checks: lint, unit tests, and any CI integration tests configured for the module
- Document observability changes (metrics/traces) in `METRICS.md` co-located with the component

---

## Security Considerations

- Never commit secrets to repo. Use environment variables or secret stores for credentials/tokens.
- Avoid logging PII in telemetry attributes or logs. Follow `docs/` security policies and OWASP guidance.

---

## Command Execution Policy (GitHub + Git)

Use one consistent CLI toolset for SCM and GitHub workflows.

### Allowed Tools

- Git CLI for repo operations: `git ...`
- GitHub CLI for GitHub operations: `gh ...`
- Shell text tools for read-only parsing: `rg`, `sed`, `awk`, `cat`

### Tool Preference Rules

- Do not switch to alternate tools if the task can be done with `git`/`gh`.
- Prefer non-interactive commands.
- Reuse previously approved command prefixes whenever possible.
- If a command is blocked, retry with escalation for the same command family instead of changing tools.

### Git Workflow (Required Order)

1. `git status --short`
2. `git diff -- <files>` (or `git diff --staged`)
3. `git add <files>` (or `git add -p`)
4. `git commit -m "<type(scope): summary>"`
5. `git push`

### Issue Reading Standard

When asked to "read issues like this", use:

- `gh issue view <number> --json title,body,labels,assignees,state,url`
- Output format:
  - `Title`
  - `Problem`
  - `Acceptance Criteria`
  - `Risks/Unknowns`
  - `Proposed Next Step`
  - `Link`
