# ADR-0019: Persistence Strategy for Short-Lived Operational State

**Status:** ACCEPTED  
**Date:** 2026-02-19  
**Deciders:** Architecture, Backend Lead, SRE Lead  
**Affected Issues:** N/A

---

## Context

Multiple backend workflows create high-churn operational records that are usually short-lived (for example active sessions, transient workflow state, and in-flight process markers).

- **Current State**: Some implementations keep state in memory for speed.
- **The Problem**: In-memory state is lost on restart and can create correctness gaps.
- **Drivers**: Fast reads/writes, restart durability, low coupling, and simple operations.
- **Scope**: Platform-wide pattern for short-lived operational state in backend services.

---

## Decision

### 1. System of Record

**Decision:** ✅ **Resolved** - Use a service-owned relational database table as the primary persistence layer for short-lived operational state.

- Keep schema lightweight and self-contained.
- Do not introduce cross-service foreign key dependencies.
- Enforce invariants with local constraints/indexes (for example uniqueness of active state).

### 2. Events Role

**Decision:** ✅ **Resolved** - Publish events as a side effect of committed state changes, not as the source of truth.

- Events support audit, integration, and observability.
- Event delivery and replay complexity is avoided for primary state persistence.
- Consumers must handle duplicate/out-of-order events using idempotency practices.

### 3. Redis Role

**Decision:** ✅ **Resolved** - Redis is optional and may be used for acceleration (cache/coordination), but not as canonical storage.

- Redis can improve hot-path latency and contention handling.
- Durable correctness remains anchored in the relational store.
- Any Redis entry loss must not cause permanent data loss.

### 4. Lifecycle Management

**Decision:** ✅ **Resolved** - Apply retention/archival policies to keep operational tables small and performant.

- Define TTL windows per domain.
- Use scheduled cleanup/archival jobs.

---

## Alternatives Considered

1. **In-memory only**: Rejected because restart loses active state and breaks durability requirements.
2. **Event sourcing as primary persistence**: Rejected due to replay/projection/idempotency complexity for this class of use case.
3. **Redis as primary store**: Rejected because durability and operational guarantees are weaker than a relational system of record.

---

## Consequences

### Positive ✅

- ✅ Durable state across restarts and rolling deployments.
- ✅ Lightweight implementation with clear ownership boundaries.
- ✅ Fast enough performance with proper indexing and small tables.
- ✅ Reusable platform pattern across multiple services.

### Negative ⚠️

- ⚠️ Requires schema management and cleanup jobs for high-churn data.
- ⚠️ Adds write-path responsibility for reliable event publishing after commit.
- ⚠️ Redis optimization introduces an additional moving part when enabled.

### Neutral

- Neutral impact on broader event-driven architecture; events remain important for integration, but not primary persistence for this state class.

---

## Implementation Notes

- Model operational state with narrow tables and essential indexes only.
- Keep write paths transactional and deterministic.
- Emit events after successful commit (transactional outbox pattern recommended where needed).
- Add metrics:
  - active record count
  - write/read latency percentiles
  - cleanup lag
  - event publish failures/retries
- Validate with:
  - restart resilience tests
  - duplicate request/idempotency tests
  - concurrent update tests

---

## References

- [ADR-0013: Platform UUID Identifier Strategy](0013-platform-uuid-identifier-strategy.adr.md)
- [ADR-0014: Gateway Internal Service Security](0014-gateway-internal-service-security.adr.md)
- [ADR-0018: Audit Actor Fields from Security Context as Strings](0018-audit-actor-fields-from-security-context.adr.md)

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | LMB | 2026-02-19 | Approved |
| Backend Lead | LMB | 2026-02-19 | Approved |
| SRE Lead | LMB | 2026-02-19 | Approved |

---

## Timeline

- **Proposed**: 2026-02-19
- **Accepted**: 2026-02-19

---

## Changelog

- **2026-02-19**: Initial draft
- **2026-02-19**: Marked ACCEPTED
