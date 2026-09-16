---
type: ADR
title: 'ADR-0056: Platform Global Exception Handling and Persistence Error Mapping'
description: ADR-0017 requires every non-2xx response to carry the ApiError envelope (code, message, status, timestamp, correlationId) and to echo/generate X-Correlation-Id.
resource: 'https://github.com/louisburroughs/durion/blob/main/docs/adr/0056-platform-global-exception-handling.adr.md'
tags: [adr, platform]
status: stable
sources: [docs/adr/0056-platform-global-exception-handling.adr.md]
generated: { by: script:generate-knowledge-catalog.py, at: 2026-09-15T21:10:50-04:00 }
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0056-platform-global-exception-handling.adr.md) — `docs/adr/0056-platform-global-exception-handling.adr.md`

**Status:** Accepted since 2026-08-23
**Related:** [ADR-0011](/adr/0011-api-gateway-security-architecture.md), [ADR-0017](/adr/0017-api-controller-http-response-codes.md), [ADR-0018](/adr/0018-audit-actor-fields-from-security-context.md), [ADR-0024](/adr/0024-entity-createdat-updatedat-population-policy.md), [ADR-0046](/adr/0046-environment-log-level-policy.md)
