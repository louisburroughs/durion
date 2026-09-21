---
type: Domain Guide
title: Positivity (Integrations and Orchestration) Documentation
description: Owns POS-facing orchestration across module walls (notably order cancellation), composed read models (notably product detail), and the wire contracts with external systems the platform integrates with but does not implement.
status: reference
---

# Positivity (Integrations and Orchestration) Documentation

Positivity documentation lives in this domain directory. The backend modules retain executable source and runtime
configuration; edit canonical documentation here. The generated
[positivity catalog entry](../../knowledge-catalog/domains/positivity.md) indexes these documents. The modules
this domain's contracts land in are [pos-marketing](../../knowledge-catalog/backend/pos-marketing.md) (the
platform-sender contract below), [pos-catalog](../../knowledge-catalog/backend/pos-catalog.md),
[pos-supplier](../../knowledge-catalog/backend/pos-supplier.md) and
[pos-event-receiver](../../knowledge-catalog/backend/pos-event-receiver.md).

## What this domain owns

Orchestration that crosses module walls without owning any of the aggregates involved, the composed read models
that serve the POS surface, and the **wire contracts with external systems** — the shape of what the platform
sends and what it must accept back. A contract belongs here precisely when no module in the backend implements
the other end of it: there is no code to read instead, so the document is the contract.

## Authority and status

Accepted [ADRs](../../docs/adr/) govern architecture. The business rules below define current domain contracts,
subject to later accepted ADRs. For event boundaries and what may cross a module wall, read
[ADR-0044](../../docs/adr/0044-platform-event-only-domain-walls.adr.md); for tenancy,
[ADR-0062](../../docs/adr/0062-postgres-row-level-multitenancy.adr.md). Backend modules own executable API
definitions, physical schema and runtime configuration.

## Business rules and contracts

- [Agent guide and positivity decisions](.business-rules/AGENT_GUIDE.md)
- [Backend contract guide](.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Design rationale and domain notes](.business-rules/DOMAIN_NOTES.md)
- [Story validation checklist](.business-rules/STORY_VALIDATION_CHECKLIST.md)

## Current documents

| Document | Status and use |
| --- | --- |
| [Platform Sender Contract (FI-2)](PLATFORM_SENDER_CONTRACT.md) | **Current.** The wire contract between `pos-marketing` and the external shared platform sender: the send API (§1), the `sender.outcomes.v1` outcome events (§2), the suppression hand-off to `pos-customer` (§3), the facts `pos-marketing` republishes (§4) and its config keys (§5). The sender is external — no backend module produces `sender.outcomes.v1` — so this document is the only in-workspace copy of the shape, and §6 names the two unit tests that hold the consumer and client to it. Cited from `PlatformSenderClient`, `DeliveryOutcomeListener`, `MessageChannelPort`, `pos-marketing`'s `application.yml`, `PlatformSenderContractTest` and `pos-archunit`'s `TopicInventoryTest` external-topic allowlist. |

## Maintaining this documentation

Add canonical documentation here and link it from this index. Every visible Markdown document under this
directory is included automatically in the positivity knowledge-catalog entry because this index declares
`type: Domain Guide`, so give each new document `type`, `title`, `description` and `status` frontmatter. Hidden
directories (`.business-rules/`) are not swept; business rules keep their own catalog section.

Regenerate with `python3 scripts/generate-knowledge-catalog.py` from the `durion` root after source changes;
`--dry-run` first, `--check` before pushing. Do not hand-edit generated catalog entries.
