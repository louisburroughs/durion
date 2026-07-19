# CLAUDE.md — Durion Platform

Cross-repo platform guidance for Claude Code. The canonical engineering policy lives in `AGENTS.md`; read that first. This file adds Claude Code-specific context, command
discovery, and hook references.

---

## Canonical Guidance (mandatory reading)

- **`AGENTS.md`** — workspace-level, cross-cutting rules (ADR compliance, git workflow, PR conventions, security, command execution policy)
- **`../durion-positivity-backend/AGENTS.md`** — backend setup, service conventions, test policy
- **`../durion-positivity-frontend/AGENTS.md`** — frontend conventions, routing, PR checklist
- Module `README.md` files in each service directory — module-specific commands and patterns

Guidance hierarchy: workspace → project → module. Closer scope wins on conflict.

---

## Code Intelligence (TokenSave) — server routing

This workspace is cross-repo, so **two** TokenSave MCP servers are configured. Pick by which repo's code you are querying:

| Query target | Tools to use |
| ------------ | ------------ |
| This durion orchestration repo (docs, plans, domain business-rules, stories) | `mcp__tokensave__*` (default) |
| `durion-positivity-backend` Java code (controllers, services, entities, handlers, tests) | `mcp__tokensave-backend__*` |

The default `mcp__tokensave__*` / `tokensave_context` is indexed on the **durion** repo only — it does **not** see backend Java. For any backend code research (symbol search, callers/callees, impact, reading service/handler logic) use the `mcp__tokensave-backend__*` equivalents. Same tool names, backend graph.

Keep the backend graph fresh with `tokensave sync` in `../durion-positivity-backend`.

**When delegating to a subagent for backend code work**, state this explicitly in the agent prompt, e.g.:
> Backend code lives in `durion-positivity-backend`. Use the `mcp__tokensave-backend__*` tools (e.g. `mcp__tokensave-backend__tokensave_context`) as your ONLY code-exploration tools for backend Java — the default `mcp__tokensave__*` server is indexed on the durion orchestration repo, not the backend. Do not use Read/glob/grep for backend code research. Pass `seen_node_ids` forward via `exclude_node_ids`.

(A frontend server is not yet configured; use Read/tokensave on `durion-positivity-frontend` directly until one is added.)

---

## Platform Rules

**Naming** — `workorder` is one word everywhere: code, comments, docs, API text, logs. Valid: `workorder`, `Workorder`, `workorderId`, `WorkorderStatus`.

**Pre-production policy** — prioritize clean, correct code over backward-compatibility workarounds. Remove deprecated members; do not add migration shims unless explicitly
requested.

**Scope** — keep changes scoped to the target component/service. Follow existing patterns in the touched module instead of importing new conventions.

**Secrets** — never hardcode tokens, passwords, or API keys. Use environment variables or secret stores.

**Docs** — when changing externally visible behaviour (APIs, events, config), update the nearest relevant `README.md`.

**Controller changes** — any change to a Controller must propagate through the full contract chain: update the OpenAPI annotations on the controller, regenerate `OpenAPI.yaml`, then update the Angular SDK. Never stop at the Java change — the controller is the API contract source, and the generated spec and SDK must stay in sync or the frontend drifts from the backend.

---

## ADR Compliance (mandatory)

Before writing or modifying code, identify applicable ADRs in `docs/adr/` and confirm the approach aligns with `ACCEPTED` status records.

Minimum for backend work: ADR-0011, ADR-0013, ADR-0014, ADR-0017, ADR-0018, ADR-0023, ADR-0024, ADR-0026, ADR-0027. Minimum for frontend work: ADR-0010, ADR-0029, ADR-0030,
ADR-0031, ADR-0032, ADR-0033, ADR-0034, ADR-0035, ADR-0037, ADR-0038.

---

## Available Slash Commands

Commands live in `.claude/commands/`. Invoke with `/command-name [arguments]`.

| Command                  | Purpose                                                             |
| ------------------------ | ------------------------------------------------------------------- |
| `/pull-request`          | Create a PR using the repo template and capability metadata         |
| `/create-adr`            | Generate a new Architecture Decision Record                         |
| `/adr-compliance`        | Audit backend code for ADR non-compliance                           |
| `/planning`              | Produce a full implementation-ready planning document for a story   |
| `/backend-story`         | Implement a backend capability story end-to-end                     |
| `/backend-story-rewrite` | Rewrite/refine an existing backend story                            |
| `/frontend-story`        | Edit and refine a frontend story for implementation-readiness       |
| `/backend-contract`      | Generate or update a backend contract guide entry                   |
| `/capability-completion` | Close out a capability (all stories done, create PR)                |
| `/api-orchestrate`       | Run API Orchestrator: full backend wave execution loop              |
| `/ui-orchestrate`        | Run UI Orchestrator: full frontend wave execution loop              |
| `/angular-orchestrate`   | Angular-specific UI orchestration                                   |
| `/freestyle-orchestrate` | Freestyle frontend orchestration                                    |
| `/pr-review`             | Orchestrate a PR review pass                                        |
| `/sonarqube-fix`         | Remediate SonarQube findings on the backend                         |
| `/generate`              | Generate backend code from spec (full autonomous generation)        |
| `/apply`                 | Apply a planned code generation workset                             |
| `/format-java`           | Format Java code with Palantir/Spotless style                       |
| `/story-update`          | Update an existing story's content or labels                        |
| `/story-extract`         | Extract structured stories from raw capability input                |
| `/code-exemplars`        | Generate code exemplar blueprints for a pattern                     |
| `/jpa-plan`              | Produce an autonomous plan for a standalone JPA entity relationship |
| `/java-mcp`              | Generate a Java MCP server module                                   |
| `/hard-questions`        | Run hard-question adversarial review against a spec or design       |

---

## Available Hook Scripts

Hook scripts live in `.claude/hooks/` (and the originals in `.github/hooks/`). Call them via Bash when running orchestrated workflows.

| Script                                                                                                              | Purpose                                                   |
| ------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| `create-branch-hook.sh --repo <path> --base <branch> --branch <name>`                                               | Create or switch to the execution branch                  |
| `test-run-hook.sh --repo <path> --module <mod> [--goal test\|verify] [--test <pattern>]`                            | Run Maven tests or Jest for a module                      |
| `lint-run-hook.sh --repo <path> --module <mod> [--files <csv>]`                                                     | Run Semgrep (Java) or ESLint (TS) on touched files        |
| `module-verify-hook.sh --repo <path> [--modules <csv>] [--base-ref <ref>]`                                          | Full verify for all touched modules                       |
| `pull-request-hook.sh --repo <path> --story <id> --base <b> --head <h> --title <t> --body-file <f>`                 | Create PR via `gh` and optionally run post-create script  |
| `post-test-coverage-commit.sh --repo <path> --story <id> --module <mod> --coverage-before <n> --coverage-after <n>` | Commit coverage improvements                              |
| `post-code-review-pass-commit.sh`                                                                                   | Commit after code review passes                           |
| `jacoco-hook.sh`                                                                                                    | Parse JaCoCo coverage report and emit structured evidence |
| `plan-acceptance-hook.sh`                                                                                           | Validate plan acceptance criteria                         |
| `init-capability-runs-hook.sh`                                                                                      | Initialise capability run tracking                        |
| `safe-delete-PRP.sh`                                                                                                | Safely delete a processing run artifact                   |
| `safe-delete-DP.sh`                                                                                                 | Safely delete a durion-processing artifact                |

---

## Agent Registry

Agent definitions live in `.claude/agents/`. They describe the role, scope, and authority of each specialised agent. Orchestrators refer to these when delegating work.

**Orchestrators** (coordinate, never write code directly):

- `.claude/agents/api-orchestrator.md` — backend wave execution
- `.claude/agents/ui-orchestrator.md` — frontend capability crawl
- `.claude/agents/frontend-orchestrator.md` — lightweight frontend orchestration

**Coding specialists**:

- `.claude/agents/anvil.md` — evidence-first senior coder with adversarial review
- `.claude/agents/lead-coder.md` — frontend sub-orchestrator, produces assignment cards
- `.claude/agents/api-surface-coder.md` — controllers, DTOs, service interfaces, OpenAPI
- `.claude/agents/domain-data-coder.md` — service logic, entities, repositories
- `.claude/agents/client-coder.md` — outbound RestClient integration
- `.claude/agents/typescript-specialist.md` — Angular TS layer
- `.claude/agents/html-specialist.md` — Angular templates, CSS, accessibility

**Review & quality**:

- `.claude/agents/code-review.md` — backend acceptance review against ADRs
- `.claude/agents/pr-review-orchestrator.md` — full PR review orchestration
- `.claude/agents/pr-reviewer.md` — PR diff review
- `.claude/agents/pr-code-reviewer.md` — final remediation review
- `.claude/agents/pr-fix-coder.md` — applies PR review fixes
- `.claude/agents/pr-test-fixer.md` — fixes failing tests
- `.claude/agents/test.md` — backend TDD agent
- `.claude/agents/test-coverage.md` — coverage improvement
- `.claude/agents/sonarqube-fix.md` — SonarQube remediation

**Architecture**:

- `.claude/agents/architecture.md` — chief architect, DDD, agent framework
- `.claude/agents/adr-generator.md` — creates ADR documents
- `.claude/agents/adr-compliance-crawler.md` — audits ADR compliance

**Support**:

- `.claude/agents/designer.md` — design decisions, accessibility, i18n
- `.claude/agents/story-authoring.md` — story creation and refinement
- `.claude/agents/pull-request.md` — PR creation (sole authority)
- `.claude/agents/docs.md` — documentation updates
- `.claude/agents/janitor.md` — cleanup and tech-debt reduction
- `.claude/agents/java-mcp-expert.md` — Java MCP server expert
- `.claude/agents/markdown-crawler.md` — markdown audit

**Domain context** (`.claude/agents/domains/`): accounting, audit, billing, crm, inventory, location, nlti, order, people, positivity, pricing, product, security, shopmgmt,
workexec.

---

## Language Instructions

Coding guidelines for each language/framework live in `.claude/instructions/`:

| File                | Applies to                        |
| ------------------- | --------------------------------- |
| `java.md`           | `**/*.java`                       |
| `springboot.md`     | `**/*.java`, `**/*.kt`            |
| `typescript.md`     | `**/*.ts`                         |
| `security-owasp.md` | all languages                     |
| `angular-i18n.md`   | Angular i18n with ngx-translate   |
| `html-css.md`       | HTML/CSS style and colour         |
| `docker.md`         | Dockerfile, docker-compose        |
| `shell.md`          | Shell scripts                     |
| `markdown.md`       | `**/*.md`                         |
| `performance.md`    | Performance optimisation patterns |
| `github-actions.md` | CI/CD workflows                   |
| `localization.md`   | Localisation patterns             |
| `collections.md`    | Collection usage guidelines       |
| `inner-classes.md`  | Inner class patterns              |

---

## Git Workflow

See `AGENTS.md` §Command Execution Policy for the full required workflow. Summary:

```bash
git status --short
git diff -- <files>
git add <files>
git commit -m "<type(scope): summary>"
git push
```

Branch naming: `cap/<cap-id>-<short-slug>` (preferred) or `feat/<short-slug>`. PR titles: `[CAP:<cap-id>] <short summary>`.
