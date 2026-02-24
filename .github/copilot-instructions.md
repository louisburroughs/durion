## GitHub Copilot Instructions – Durion Workspace

This file is a concise Copilot overlay. Canonical engineering rules live in:

- `AGENTS.md` (workspace-level, cross-cutting guidance)
- `durion-positivity-backend/AGENTS.md` (backend-specific implementation rules)
- Local module `README.md` files (module-specific behavior and commands)

If guidance conflicts, follow the closest-scope document in that order.

## Required Copilot Behavior

- Keep changes scoped to the target component/service.
- Follow existing patterns in the touched module instead of introducing new conventions.
- When changing externally visible behavior (APIs, events, configs), update the nearest relevant README/doc.
- Never hardcode secrets (tokens, passwords, API keys). Use environment variables or secret stores.

## Mandatory Naming Convention

- `workorder` MUST be one word in code, comments, docs, API text, and logs.
- Use valid forms such as `workorder`, `Workorder`, `WORKORDER`, `workorderId`, `workorderStatus`.
