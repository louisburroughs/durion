# ADR-NNNN: [Brief Decision Title]

**Status:** PENDING DECISION *(or PROPOSED, ACCEPTED, DEPRECATED, SUPERSEDED)*  
**Date:** YYYY-MM-DD  
**Deciders:** [Role 1], [Role 2], [Role 3]  
**Affected Issues:** [Link to related GitHub issues, e.g., #123, #456]

---

## Context

Describe the situation and the problem that triggered the need for this decision. Include:

- **Current State**: What is the existing situation?
- **The Problem**: What issue or challenge needs to be addressed?
- **Drivers**: What forces or constraints are motivating this decision?
- **Scope**: What systems or domains does this decision affect?

Example:
> The order service currently processes refunds synchronously during order cancellation. High-volume days cause request timeouts and poor user experience. We need a strategy to decouple refund processing from the cancellation workflow.

---

## Decision

State the decision clearly and concisely. Include:

- **What was chosen**: Be specific about the approach, technology, or pattern selected
- **Key Details**: Implementation approach, configuration, or setup
- **Why this choice**: Justification relative to alternatives considered
- **Scope & Boundaries**: What this decision covers and what it doesn't

### Format for Sub-Decisions (Recommended: Option 2)

When an ADR contains multiple related decisions, use subsections with a clear ✅ **Resolved** marker:

```markdown
### 1. Route Pattern

**Proposed:**
[proposed options]

**Decision:** ✅ **Resolved** - Use `/crm/parties/{partyId}` format (proposed). Update legacy services for backward compatibility.
```

This format allows reviewers to quickly:

- See which decisions are finalized (✅ marker)
- Understand the resolution in 1-2 sentences
- Track any implementation notes or trade-offs

Example from real ADR:
> **Decision:** ✅ **Resolved** - Implement asynchronous refund processing using Spring Cloud Stream and RabbitMQ. Decouples order and refund domains, improves responsiveness, allows retry logic and backpressure handling.

---

## Alternatives Considered

List and briefly describe alternatives that were evaluated but rejected:

1. **Option A**: [Description and why it was rejected]
2. **Option B**: [Description and why it was rejected]
3. **Option C**: [Description and why it was rejected]

Example:
>
> - **Synchronous HTTP**: Maintains tight coupling; doesn't scale under load
> - **Custom queue**: Reinvents the wheel; maintenance burden
> - **Database polling**: High latency and DB overhead

---

## Consequences

Describe both positive and negative outcomes of this decision:

### Positive ✅

- **Benefit 1**: Description
- **Benefit 2**: Description
- **Benefit 3**: Description

Example:

- ✅ Improved user experience: cancellations return immediately
- ✅ Better fault isolation: refund failures don't crash order service
- ✅ Easier to scale: each service scales independently

### Negative ⚠️

- **Drawback 1**: Description and mitigation
- **Drawback 2**: Description and mitigation

Example:

- ⚠️ Eventual consistency: refund status updates asynchronously (mitigated by UI polling + status page)
- ⚠️ Operational complexity: additional service and queue to monitor (mitigated by observability/alerting)
- ⚠️ Message delivery guarantees needed: must handle duplicates (mitigated by idempotency keys)

### Neutral

- **Note 1**: Neutral impact or trade-off
- **Note 2**: Neutral impact or trade-off

---

## Implementation Notes

Include practical details for implementing this decision:

- **Required Components**: Libraries, frameworks, infrastructure
- **Configuration**: Key settings, environment variables
- **Testing Strategy**: How this will be validated
- **Rollout Plan**: Phasing, canary, feature flags
- **Metrics & Monitoring**: What will be observed

Example:
>
> - **Components**: Spring Cloud Stream, RabbitMQ, Spring Data JPA
> - **Configuration**: `spring.cloud.stream.bindings.refundChannel` → AMQP connection
> - **Testing**: Integration tests with Testcontainers; chaos tests for queue failures
> - **Rollout**: Feature flag `ASYNC_REFUNDS`; canary at 10% of cancellations first week
> - **Monitoring**: Message lag, refund success rate, end-to-end latency

---

## References

- **Related Issues**: [#123 - Order Refund Performance](link), [#456 - Scalability Planning](link)
- **Related ADRs**: [ADR-0007: Event-Driven Architecture](0007-event-driven-architecture.adr.md)
- **Related Documentation**: [Order Service README](../../durion-positivity-backend/pos-order/README.md), [Observability Guide](../architecture/observability/OBSERVABILITY.md)
- **External Resources**: [Spring Cloud Stream Guide](https://spring.io/projects/spring-cloud-stream), [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | [Name] | YYYY-MM-DD | [Approval notes] |
| Backend Lead | [Name] | YYYY-MM-DD | [Approval notes] |
| Database Admin | [Name] | YYYY-MM-DD | [Approval notes] |

---

## Timeline

- **Proposed**: [Date]
- **Under Review**: [Date] — [Details about feedback]
- **Accepted**: [Date]
- **Implementation Started**: [Date]
- **Implementation Complete**: [Date]
- **Deployed to Production**: [Date]

---

## Changelog

- **2026-01-24**: Initial draft
- **2026-01-25**: Added alternative options and metrics section
- **2026-01-26**: Approved by architecture team
