# AGENTS.md — Durion Platform

## Project Overview

Durion is a multi-repo platform that includes:

- `durion-positivity-backend`: POS Spring Boot microservices (Java 21, Spring Boot 4.0.2)
- `durion`: workspace-level coordination, governance, project, and agent docs

This file is a concise, agent-focused guide containing the commands and context agents need to work across frontend and backend.

---

## Quick Prerequisites

- Java 21+ (for Spring Boot services)
- Maven (`mvn` or  `./mvnw`)
- Docker + Docker Compose (for local stacks and collector demos)

---

## Setup Commands

Clone and prepare repositories (from workspace root):

```bash
git clone git@github.com:louisburroughs/durion.git
cd durion
```

Backend (Positivity) quick setup:

```bash
cd durion-positivity-backend
./mvnw -pl pos-api-gateway -am clean package  # build gateway + deps
# Build a specific service:
./mvnw -pl pos-order -am clean package
```

Local stack (compose examples):

```bash
docker-compose -f docker/moqui-postgres-compose.yml up -d
# Start observability demo (if present in docs/compose)
# docker-compose -f docs/observability-compose.yml up -d
```

---

## Development Workflow

- Use `./mvnw -pl <module> -am spring-boot:run` or run the packaged JAR with `java -jar` for a single service.

Examples:

```bash
# Run single Spring Boot service locally
cd durion-positivity-backend/pos-order
./mvnw spring-boot:run
# or
java -jar target/pos-order-*.jar
```

---

## Testing

- Backend (Positivity):

```bash
# Run all tests in the workspace-level backend
cd durion-positivity-backend
./mvnw -DskipTests=false clean test
# Module-only tests
./mvnw -pl pos-order -am test
```

# (If present) Module-specific tests are documented in each project's README or AGENTS.md

---

## Build & Release

- Backend artifacts: `./mvnw -pl <module> -am clean package`
- Ensure `SERVICE_VERSION`/`service.version` is set in CI builds for observability and release tagging.

---

## Observability (instrumentation & runbooks)

Primary, canonical architecture doc: `docs/architecture/observability/OBSERVABILITY.md`
SRE agent runbook: `.github/agents/sre.agent.md`

Guidance for agents:

- Use the OpenTelemetry Collector as the gateway for OTLP from services (gRPC: `otel-collector:4317`, HTTP: `:4318`).

- Backend: prefer OpenTelemetry Java agent for baseline, manual SDK instrumentation for business metrics. Document metrics in `METRICS.md` near each `pos-*` module.

Quick commands/examples:

```bash
# Example env for local OTLP endpoint
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
# Start a simple collector (if a local compose file exists)
docker-compose -f docs/observability-compose.yml up -d
```

---

## Code Style & Quality

-- Spring Boot: use Micrometer/OpenTelemetry integration and standard Actuator endpoints (`/actuator/health`).
-- Run linters before committing (follow component README for specific commands):

```bash
# Backend: rely on mvn formatter/lint steps if configured
cd durion-positivity-backend
./mvnw verify
```

---

## Documentation Hierarchy & README.md Files

**MANDATORY: Always check for and consult README.md files IN YOUR WORKING directory before making changes.**

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
- Feature directories: Any top-level directory may have a README.md

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

## Agents & Where to Find Their Docs

Agent docs live under `.github/agents/` in this repo. Key agent docs and runbooks:

- `.github/agents/sre.agent.md` — SRE/Observability guidance (instrumentation, telemetry contract)
- `.github/agents/coder.agent.md` — Primary software engineer guidance
- `.github/agents/dev-deploy.agent.md` — Deployment/CI/CD guidance
- `.github/agents/api.agent.md` — REST API guidance
- Backend repo test agent: `durion-positivity-backend/.github/agents/test.agent.md`

**Always consult local README.md files first**, then agent docs for cross-cutting concerns. When in doubt about observability, consult `.github/agents/sre.agent.md` and `docs/architecture/observability/OBSERVABILITY.md`.

---

## Pull Request & Commit Guidance

- Preface PR titles with the capability issue number: `cap/<cap-id>`
- Required checks: lint, unit tests, and any CI integration tests configured for the module.
- Document changes to observability (metrics/traces) in `METRICS.md` co-located with the component.

---

## Debugging & Troubleshooting Tips

-- Backend health: hit `http://<host>:<port>/actuator/health` and check logs for DB/connectivity errors.
-- Observability gaps: ensure `SERVICE_VERSION` is set, check OTLP endpoint env vars, and confirm collector is reachable.

Logs & traces correlation:

-- Ensure trace context (W3C) is propagated through gateway → backend services.

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

When asked to “read issues like this”, use:

- `gh issue view <number> --json title,body,labels,assignees,state,url`
- Output format:
  - `Title`
  - `Problem`
  - `Acceptance Criteria`
  - `Risks/Unknowns`
  - `Proposed Next Step`
  - `Link`

For multiple issues:

- `gh issue list --limit <n> --json number,title,labels,state,url`
- Then `gh issue view` per selected issue.

### Pull Request Creation Standard

Use:

1. `git status --short`
2. `git diff --staged --name-only`
3. `gh pr create --base <base> --head <branch> --title "<title>" --body-file <file>`

### Guardrails

- Never use destructive git commands unless explicitly requested (`git reset --hard`, `git checkout --`, force push).
- Never open browser/UI flows for PR/issues when `gh` can do it.
- If required context is missing, ask one concise question, then continue with the same toolset.

## File Read/Write Policy

Use a consistent, low-risk workflow for inspecting and editing files.

### Read Rules

- Prefer fast search tools first:
  - File discovery: `rg --files`
  - Text search: `rg "<pattern>" <path>`
- Read only the smallest necessary scope before editing:
  - Start with targeted files and symbols, not whole-repo scans.
  - Use focused reads (`sed -n 'start,endp'`, `rg -n`) to inspect relevant regions.
- Before modifying a file, confirm:
  - Existing conventions in that module/package
  - Related tests and architecture constraints
  - Whether the file is already dirty (`git status --short`)
- For large files:
  - Read in chunks
  - Avoid loading unrelated sections

### Write Rules

- Make minimal, surgical edits that directly satisfy the task.
- Preserve existing style, package structure, and naming conventions.
- Do not refactor unrelated code in the same change.
- Prefer non-destructive edits:
  - No deleting or moving files unless required by the task.
  - No destructive git/file commands unless explicitly requested.
- Keep ASCII by default unless the file already requires Unicode.
- Add comments only when needed to explain non-obvious logic.

### Safe Edit Workflow (Required Order)

1. Inspect target context (`rg`, `sed`, `cat`)
2. Edit only required files
3. Review changes (`git diff -- <files>`)
4. Validate impacted scope (tests/build/lint for touched module)
5. Report exactly what changed and why

### Validation Scope

- Run the smallest meaningful validation first:
  - File/module-level tests before full-suite tests
- If validation cannot run, state that explicitly and why.

### Patch Hygiene

- One logical change per patch.
- Keep diffs easy to review:
  - Avoid whitespace-only churn
  - Avoid mass reformatting unless requested
- Include file paths when summarizing changes.

### Write Restrictions

- Never write secrets, tokens, or credentials to files.
- Never modify lockfiles/generated files unless the task requires it.
- Never overwrite user-authored unrelated changes.

## Where to Extend

- **Module documentation**: Always create or update `README.md` in the module directory when adding new modules or features
- For monorepo-like per-component agent context, add `AGENTS.md` into subproject roots (e.g., `durion-positivity-backend/AGENTS.md`)
- **Keep README.md files current**: Update them when changing module structure, APIs, or configuration
- Update this file when new developer workflows or CI steps are added that affect multiple projects

**Documentation Priority Order:**

1. Check local `README.md` IN YOUR WORKING FOLDER!
2. Check project-level `AGENTS.md` for project-specific patterns
3. Check workspace-level `AGENTS.md` (this file) for cross-cutting guidance
4. Check `.github/agents/*.agent.md` for specialized guidance

---
