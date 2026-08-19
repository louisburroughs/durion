# Agent Consolidation, Knowledge Catalog, and Doc Slimming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate shared agent configuration under `durion`, establish an OKF knowledge catalog for the Durion repos, slim repo docs to quick-start references, and add per-module index navigation files for discoverability.

**Architecture:** This plan treats `durion` as the single source of truth for agent config and knowledge navigation. All other repos consume shared content via a pinned git submodule and link to the catalog bundle, while module-level docs remain lightweight and human-readable. The work is broken into four coherent phases with explicit handoff checks between them.

**Tech Stack:** Git submodules, Markdown + YAML frontmatter (OKF v0.2), GitHub repo links, shell scripts for file generation, and existing repository docs (`durion/docs/**`, `durion-positivity-backend/pos-*`, project-level README/AGENTS patterns).

---

### Task 1: Consolidate shared agent configuration under `durion`

**Files:**
- Create: `/home/n541342/IdeaProjects/durion/agent-config/agents/.gitkeep`
- Create: `/home/n541342/IdeaProjects/durion/agent-config/commands/.gitkeep`
- Create: `/home/n541342/IdeaProjects/durion/agent-config/instructions/.gitkeep`
- Create: `/home/n541342/IdeaProjects/durion/agent-config/README.md`
- Modify: `/home/n541342/IdeaProjects/durion/.mcp.json`
- Modify: `/home/n541342/IdeaProjects/durion/CLAUDE.md`
- Modify: `/home/n541342/IdeaProjects/durion/.claude/settings.json`
- Modify: `/home/n541342/IdeaProjects/durion-positivity-backend/.claude/settings.json`
- Create: `/home/n541342/IdeaProjects/durion-positivity-backend/.durion-shared` (submodule path)
- Modify: `/home/n541342/IdeaProjects/durion-positivity-backend/.claude/agents` (symlink or path change)
- Modify: `/home/n541342/IdeaProjects/durion-positivity-backend/.claude/commands` (symlink or path change)
- Modify: `/home/n541342/IdeaProjects/durion-positivity-backend/.claude/instructions` (symlink or path change)
- Modify: `/home/n541342/IdeaProjects/durion-positivity-backend/.mcp.json`

- [ ] **Step 1: Inventory canonical content and reconcile duplicates**

```bash
cd /home/n541342/IdeaProjects/durion
find .claude/agents -maxdepth 1 -type f | sort
find .claude/commands -maxdepth 1 -type f | sort
find .claude/instructions -maxdepth 1 -type f | sort

cd /home/n541342/IdeaProjects/durion-positivity-backend
find .claude/agents -maxdepth 1 -type f | sort
find .claude/commands -maxdepth 1 -type f | sort
find .claude/instructions -maxdepth 1 -type f | sort
```

Expected: canonical files in `durion/.claude/*` are identified; backend duplicates are reviewed to avoid losing files like `test-coverage.md` and `validate_api_naming.py` that exist only in `durion`.

- [ ] **Step 2: Create the shared config root and migrate official copies**

```bash
cd /home/n541342/IdeaProjects/durion
mkdir -p agent-config/agents agent-config/commands agent-config/instructions
cp -R .claude/agents/. agent-config/agents/
cp -R .claude/commands/. agent-config/commands/
cp -R .claude/instructions/. agent-config/instructions/
cp .mcp.json agent-config/mcp.json
```

Expected: all durion-authored agent config lands in one shared location without losing repo-specific files.

- [ ] **Step 3: Write the shared README and site-level pointer file**

```markdown
# Shared Agent Configuration

This directory is the canonical source for durion-authored agent instructions, commands, and shared MCP config.

## Contents
- `agents/` — reusable agent definitions consumed by repos in this workspace
- `commands/` — command-level task workflows
- `instructions/` — reusable coding and review instructions
- `mcp.json` — shared MCP server defaults

## Consumer usage
Each repo should include this repo as a git submodule or symlinked path and expose the files at the repo-local `.claude/*` paths expected by Claude/Copilot tooling.
```

Expected: a clear owner and usage contract exists in `durion/agent-config/README.md`.

- [ ] **Step 4: Wire `durion` as the canonical source and update repo-local config entry points**

```bash
cd /home/n541342/IdeaProjects/durion-positivity-backend
git submodule add https://github.com/louisburroughs/durion.git .durion-shared
ln -sfn ../.durion-shared/agent-config/agents .claude/agents
ln -sfn ../.durion-shared/agent-config/commands .claude/commands
ln -sfn ../.durion-shared/agent-config/instructions .claude/instructions
ln -sfn ../.durion-shared/agent-config/mcp.json .mcp.json
```

Expected: the backend repo consumes the shared config through a single pinned dependency, while still preserving local `.claude/settings.json` and `.claude/hooks` files.

- [ ] **Step 5: Validate the agent directory loads correctly**

```bash
cd /home/n541342/IdeaProjects/durion-positivity-backend
ls -l .claude/agents | head
ls -l .claude/commands | head
ls -l .claude/instructions | head
ls -l .mcp.json
```

Expected: symlink targets resolve to `durion/agent-config/...` and the paths remain valid for Claude/Copilot.

- [ ] **Step 6: Commit**

```bash
cd /home/n541342/IdeaProjects/durion && git add agent-config .mcp.json CLAUDE.md .claude/settings.json
cd /home/n541342/IdeaProjects/durion-positivity-backend && git add .claude/settings.json .claude/agents .claude/commands .claude/instructions .mcp.json
git commit -m "feat: consolidate shared agent config under durion"
```

Expected: both repos commit the shared config migration cleanly.

### Task 2: Initialize the OKF knowledge catalog under `durion`

**Files:**
- Create: `/home/n541342/IdeaProjects/durion/knowledge-catalog/index.md`
- Create: `/home/n541342/IdeaProjects/durion/knowledge-catalog/log.md`
- Create: `/home/n541342/IdeaProjects/durion/knowledge-catalog/adr/index.md`
- Create: `/home/n541342/IdeaProjects/durion/knowledge-catalog/domains/index.md`
- Create: `/home/n541342/IdeaProjects/durion/knowledge-catalog/backend/index.md`
- Create: `/home/n541342/IdeaProjects/durion/knowledge-catalog/adr/*.md` (pointer concepts)
- Create: `/home/n541342/IdeaProjects/durion/knowledge-catalog/domains/*.md` (pointer concepts)
- Create: `/home/n541342/IdeaProjects/durion/knowledge-catalog/backend/*.md` (pointer concepts)
- Script: `/home/n541342/IdeaProjects/durion/scripts/generate-knowledge-catalog.py` (one-time generation script)

- [ ] **Step 1: Write the bundle root index and log skeleton**

```markdown
---
okf_version: 0.2
---

# Durion Knowledge Catalog

* [Architecture and ADRs](/adr/)
* [Domains](/domains/)
* [Backend modules](/backend/)
```

```markdown
# Directory Update Log

## 2026-08-19
* **Initialization**: Established the OKF knowledge catalog for `durion` and the backend module suite.
```

Expected: root `index.md` is valid OKF v0.2 and `log.md` records the initial creation.

- [ ] **Step 2: Author the generator script for pointer concepts**

```python
#!/usr/bin/env python3
from pathlib import Path
import re

root = Path('/home/n541342/IdeaProjects/durion')
catalog = root / 'knowledge-catalog'
# Generate pointer concepts for ADRs, domains, and backend modules from existing docs.
# Title extraction: first markdown heading or module directory name.
# Description extraction: first non-empty paragraph or a generated summary line.
# Resource field: GitHub blob URL to the canonical file in the real repo.
```

Expected: the script generates a one-time set of OKF concept docs in `adr/`, `domains/`, and `backend/` without rewriting source content.

- [ ] **Step 3: Generate the v1 pointer set**

```bash
cd /home/n541342/IdeaProjects/durion
python3 scripts/generate-knowledge-catalog.py
find knowledge-catalog -maxdepth 3 -type f | sort
```

Expected: directory structure exists and concept documents are generated with frontmatter and pointer links into the canonical docs.

- [ ] **Step 4: Validate pointer links and catalog navigation**

```bash
cd /home/n541342/IdeaProjects/durion
grep -R "resource:" knowledge-catalog | head -20
grep -R "\[/.*\]" knowledge-catalog | head -20
```

Expected: every concept has a valid GitHub resource URL and internal cross-links resolve to sibling concepts in the bundle.

- [ ] **Step 5: Commit**

```bash
cd /home/n541342/IdeaProjects/durion && git add knowledge-catalog scripts/generate-knowledge-catalog.py && git commit -m "feat: add OKF knowledge catalog bundle"
```

Expected: the catalog is committed and acts as the discoverability layer.

### Task 3: Slim `AGENTS.md` and `README.md` to quick-start + links

**Files:**
- Modify: `/home/n541342/IdeaProjects/durion/AGENTS.md`
- Modify: `/home/n541342/IdeaProjects/durion/README.md`
- Modify: `/home/n541342/IdeaProjects/durion-positivity-backend/AGENTS.md`
- Modify: `/home/n541342/IdeaProjects/durion-positivity-backend/README.md`
- Create: `/home/n541342/IdeaProjects/durion/.agents/skills/pos-events-pattern/SKILL.md`
- Create: `/home/n541342/IdeaProjects/durion/.agents/skills/knowledge-catalog-index/SKILL.md`

- [ ] **Step 1: Rewrite the root `AGENTS.md` to a short quick-start**

```markdown
# AGENTS.md

## Quick Start
- `./mvnw ...` for backend builds
- `docker-compose up -d` for local stack
- use the shared `durion` agent config under `.durion-shared/agent-config/`

## Non-negotiable Rules
- Keep agent config in `durion` as the single source of truth.
- Link to `durion/knowledge-catalog/` for domain and module context.
- Keep module-specific docs and implementation patterns in the repo.

## Where to Look
- Shared config: `agent-config/`
- Knowledge catalog: `knowledge-catalog/`
- Module architecture: `durion/docs/**`
```

Expected: authoritative guidance remains, but the file is shortened to a navigation aid rather than a long embedded manual.

- [ ] **Step 2: Rewrite repo `README.md` to a standard OSS summary**

```markdown
# Durion

Durion is the shared operating context for domain docs, agent config, and platform guidance.

## Quick Start
- Use the shared agent config in `agent-config/`
- Review architecture and ADRs in `docs/`
- Use `knowledge-catalog/` to browse domain/module surfaces
```

Expected: README is now a high-value summary and entry point instead of a huge long-form manual.

- [ ] **Step 3: Slim backend `AGENTS.md` and `README.md` to critical rules and links**

```markdown
# AGENTS.md

## Quick Start
- `./mvnw -pl pos-order -am test`
- `./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test`

## Critical Rules
- `@NonNull` required on service/DAO APIs
- internal packages remain private under `com.positivity.*.internal`
- use `@EmitEvent` on state-changing endpoints

## Related docs
- `durion/knowledge-catalog/backend/`
- `durion/docs/` and ADRs
- `AGENTS` templates now live in skills and catalog concepts
```

Expected: backend docs remain operational but no longer embed every pattern in-line.

- [ ] **Step 4: Move detailed templates into reusable skills or catalog concepts**

```markdown
# POS Events Pattern

Use this skill when wiring new `@EmitEvent` handlers and event-type registries.

## Trigger
- state change in controller or service
- new event type registration required

## Standard pattern
- define `*EventTypes` registry in `internal/config`
- implement `*EventTypeInitializer`
- register type in startup runner and swallow startup failures
```

Expected: the large examples that were previously duplicated in `AGENTS.md` now live in reusable skill files.

- [ ] **Step 5: Commit**

```bash
cd /home/n541342/IdeaProjects/durion && git add AGENTS.md README.md .agents/skills/pos-events-pattern/SKILL.md .agents/skills/knowledge-catalog-index/SKILL.md
cd /home/n541342/IdeaProjects/durion-positivity-backend && git add AGENTS.md README.md
git commit -m "docs: slim AGENTS and README to quick starts and catalog pointers"
```

Expected: repo docs are shorter and more maintainable without losing the actionable guidance.

### Task 4: Add per-module and per-domain `index.md` files

**Files:**
- Create: `/home/n541342/IdeaProjects/durion-positivity-backend/pos-accounting/index.md`
- Create: `/home/n541342/IdeaProjects/durion-positivity-backend/pos-api-gateway/index.md`
- Create: `/home/n541342/IdeaProjects/durion-positivity-backend/pos-order/index.md`
- Create: `/home/n541342/IdeaProjects/durion-positivity-backend/pos-*/index.md` for each service module
- Create: `/home/n541342/IdeaProjects/durion/domains/accounting/index.md`
- Create: `/home/n541342/IdeaProjects/durion/domains/order/index.md`
- Create: `/home/n541342/IdeaProjects/durion/domains/*/index.md` for each domain
- Script: `/home/n541342/IdeaProjects/durion/scripts/generate-module-indexes.py`

- [ ] **Step 1: Build the index-generation script**

```python
#!/usr/bin/env python3
from pathlib import Path

root = Path('/home/n541342/IdeaProjects')
backend = root / 'durion-positivity-backend'
for module in sorted(backend.glob('pos-*')):
    if module.is_dir():
        index = module / 'index.md'
        if not index.exists():
            index.write_text('# ' + module.name + '\n\n* [src/](src/)\n* [service/](service/)\n* [internal/](internal/)\n')

for domain in sorted((root / 'durion' / 'domains').glob('*')):
    if domain.is_dir():
        idx = domain / 'index.md'
        if not idx.exists():
            idx.write_text('# ' + domain.name + '\n\n* [docs](.)\n* [Catalog entry](../../knowledge-catalog/domains/' + domain.name + '.md)\n')
```

Expected: the script produces consistent module and domain indexes that follow the OKF convention.

- [ ] **Step 2: Generate indexes for all modules and domains**

```bash
cd /home/n541342/IdeaProjects/durion
python3 scripts/generate-module-indexes.py
find ../durion-positivity-backend/pos-* -maxdepth 1 -name index.md | sort
find domains -maxdepth 2 -name index.md | sort
```

Expected: every target module and domain has a lowercase `index.md` file with a concise listing.

- [ ] **Step 3: Check module link quality**

```bash
cd /home/n541342/IdeaProjects/durion
grep -R "Catalog concept\|Domain spec" ../durion-positivity-backend/pos-*/*/index.md domains/*/index.md
```

Expected: each index points to at least one real doc or catalog entry and is readable as a navigation page.

- [ ] **Step 4: Validate with architecture checks**

```bash
cd /home/n541342/IdeaProjects/durion-positivity-backend
./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test
```

Expected: docs-only file changes do not disturb architecture or build integrity.

- [ ] **Step 5: Commit**

```bash
cd /home/n541342/IdeaProjects/durion && git add scripts/generate-module-indexes.py domains/*/index.md
cd /home/n541342/IdeaProjects/durion-positivity-backend && git add pos-*/index.md
git commit -m "docs: add module and domain index navigation"
```

Expected: end users can navigate module structure and domain boundaries without reading the full repository tree.

### Task 5: End-to-end verification and release gate

**Files:**
- No new files; verification only.

- [ ] **Step 1: Run repo-level validation commands**

```bash
cd /home/n541342/IdeaProjects/durion
python3 - <<'PY'
from pathlib import Path
for p in sorted(Path('knowledge-catalog').rglob('*.md')):
    print(p)
PY

echo '---'
cd /home/n541342/IdeaProjects/durion-positivity-backend
./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test
```

Expected: knowledge catalog files exist, links are sane, and the backend still passes architecture validation after docs-only changes.

- [ ] **Step 2: Check `AGENTS.md` / `README.md` slimming results**

```bash
wc -w /home/n541342/IdeaProjects/durion/AGENTS.md /home/n541342/IdeaProjects/durion/README.md
wc -w /home/n541342/IdeaProjects/durion-positivity-backend/AGENTS.md /home/n541342/IdeaProjects/durion-positivity-backend/README.md
```

Expected: word counts are materially reduced and the docs point to the new shared references rather than containing long-form parallel content.

- [ ] **Step 3: Validate the final handoff to execution**

Expected: the implementation walk is complete, and the system is ready for the user to choose either subagent-driven execution or inline execution via `executing-plans`.
