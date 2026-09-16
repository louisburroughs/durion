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

The catalog is generated: run `python3 scripts/generate-knowledge-catalog.py` after changing canonical sources,
and `python3 scripts/generate-knowledge-catalog.py --check` to validate OKF structure. A domain's `index.md` can declare
`type: Domain Guide` in frontmatter to include its visible Markdown documents recursively, with titles and lifecycle
status, in the generated domain entry. Hidden business rules keep their existing section; link other hidden artifacts
from the domain index. See the [accounting documentation index](domains/accounting/index.md) for this convention.

## Related Repos

- `../durion-positivity-backend/` — Java/Spring Boot services
- `../durion-positivity-frontend/` — Angular app
- `../durion-positivity-sdk-angular/` — Angular SDK
