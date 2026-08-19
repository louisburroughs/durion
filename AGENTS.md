# AGENTS.md — Durion Platform

## Quick Start

- Use `durion/knowledge-catalog/` as the first stop for architecture, ADR, and module navigation.
- Use `durion-positivity-backend/AGENTS.md` for backend build/test/run guidance.
- Use `durion-positivity-frontend/AGENTS.md` for frontend guidance.
- Keep repo-local README files current when you change setup, structure, or APIs.

## Non-negotiable Rules

- Keep `durion` as the shared source of truth for cross-repo agent config and governance docs.
- Check applicable ADRs before changing architecture or behavior.
- Prefer local module docs and the knowledge catalog over repeating long-form architecture prose in repo root docs.
- Do not duplicate authoritative guidance in multiple places when a pointer or catalog entry is enough.

## Where to Look

- Shared agent configuration: `agent-config/`
- Knowledge catalog: `knowledge-catalog/`
- Architecture and ADRs: `docs/`
- Repo-specific implementation: `durion-positivity-backend/`, `durion-positivity-frontend/`, and each module README

## Typical Workflow

1. Read the nearest README.
2. Check the relevant ADR if behavior or architecture is changing.
3. Use `knowledge-catalog/` to find the domain and module context.
4. Keep the implementation, docs, and navigation layers aligned.
