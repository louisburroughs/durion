---
name: Knowledge Catalog Maintenance
description: "Maintain the OKF knowledge catalog so execution and research agents find the right source in one hop."
---

# Knowledge Catalog

Follow `.claude/skills/knowledge-catalog/SKILL.md`.

## Invocation

`/knowledge-catalog [task]` — where task is one of:

- `add <adr|domain|module> <id>` — bring a new source into the catalog
- `fix <entry>` — an entry reads wrong, empty, or stale
- `miss <what an agent could not find>` — diagnose a retrieval failure (SKILL §4.4)
- `regen` — dry-run, regenerate, check
- `audit` — conformance plus retrieval-quality pass over the bundle
- (no argument) — report bundle state: concept counts, `--check` result, thin entries, coverage gaps

## Hard rules

- `knowledge-catalog/` is build output. Never hand-edit a concept note or an `index.md`.
- `log.md` is the only file edited by hand, and only for structural change.
- Always `--dry-run` before regenerating; always `--check` before pushing.

## Report

Sources changed · regeneration evidence · entries affected · `log.md` entry · gaps left open.
