# Durion

Durion is the shared workspace for governance, architecture decisions, domain language, and cross-repo coordination. This repo acts as the canonical hub for ADRs, domain docs, shared agent config, and the knowledge catalog.

## Quick Start

```bash
# Review the catalog first
ls knowledge-catalog

# Browse ADRs and domain docs
ls docs/adr
grep -R "type: Domain\|type: ADR" knowledge-catalog
```

## Key Areas

- `docs/adr/` — architecture decisions
- `docs/architecture/` — architecture references and observability guidance
- `domains/` — domain-level documentation and boundaries
- `knowledge-catalog/` — OKF-based navigation layer for docs and modules
- `AGENTS.md` — cross-repo quick-reference guidance

## Related Repos

- `../durion-positivity-backend/` — Java/Spring Boot services
- `../durion-positivity-frontend/` — Angular app
- `../durion-positivity-sdk-angular/` — Angular SDK
