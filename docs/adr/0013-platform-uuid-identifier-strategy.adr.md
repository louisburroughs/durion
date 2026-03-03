# ADR-0013: UUID v7 Identifier Strategy for Platform Entities

**Status:** ACCEPTED  
**Date:** 2026-02-07  
**Deciders:** Platform Architecture Team, Backend Lead, Frontend Lead  
**Affected Issues:** Platform-wide identifier strategy

---

## Context

The Durion platform consists of multiple microservices (durion-positivity-backend) and a frontend that must coordinate entity identification across distributed systems. The current identifier strategy is inconsistent—some services use auto-incrementing integers, others use strings, and there's no unified approach to distributed ID generation.

**Current State:**

- Mixed identifier types across services (Long, String, Integer)
- Auto-incrementing integers expose record counts and enable enumeration attacks
- Distributed services require coordination for unique ID generation
- Frontend-backend identifier serialization is inconsistent

**The Problem:**

- **Security**: Sequential IDs leak information and enable enumeration attacks
- **Distributed coordination**: Auto-increment requires database coordination, causing bottlenecks
- **Global uniqueness**: No guarantee of uniqueness when merging data across environments
- **Serialization complexity**: Different types require different handling across the stack

**Drivers:**

- Microservices architecture requires distributed ID generation without coordination
- Security best practices demand non-enumerable identifiers
- Frontend (TypeScript/Vue) and backend (Java/Spring Boot) need consistent serialization
- Future cloud deployments may require multi-region identifier generation

**Scope:**

- All new entities across pos-* microservices
- REST API contracts between frontend and backend
- Database schemas for new tables

---

## Decision

**Decision:** ✅ **Resolved** - Adopt **UUID v7** as the standard identifier type for all new platform entities. Use time-ordered UUID v7 to maintain database index performance while providing global uniqueness and security.

### 1. Identifier Type

**Chosen:** UUID v7 (Time-Ordered UUID)

**Rationale:**

- **Time-ordered**: Maintains insert performance by reducing index fragmentation (unlike UUID v4)
- **128-bit global uniqueness**: No coordination needed across services or environments
- **Security**: Non-enumerable, doesn't leak record counts
- **Timestamp prefix**: Enables temporal ordering and range queries
- **RFC 9562 standard**: Well-defined specification with growing library support

**Specification:**

```
UUID v7 format: tttttttt-tttt-7xxx-yxxx-xxxxxxxxxxxx
- t: Unix timestamp (milliseconds)
- 7: Version identifier (v7)
- x, y: Random bits
```

### 2. Backend Implementation (Java/Spring Boot)

**Decision:** ✅ **Resolved** - Use Java `UUID` type with UUID v7 generation library.

**Implementation:**

```java
// Entity definition — use @GeneratedValue + @UUIDv7Id on the ID field and
// @EntityListeners(AuditingEntityListener.class) on the class for Spring Data auditing.
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue          // delegates to Hibernate's @IdGeneratorType mechanism
    @UUIDv7Id                // meta-annotated with @IdGeneratorType(UUIDv7Generator.class)
    private UUID id;

    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedBy
    private String updatedBy;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}

// Repository — ID type is UUID
public interface OrderRepository extends JpaRepository<Order, UUID> {
}
```

> **Note:** Do **not** use `@PrePersist` to assign the UUID. `@GeneratedValue` + `@UUIDv7Id` delegates
> generation to `com.positivity.shared.id.UUIDv7Generator` via Hibernate's `@IdGeneratorType`
> contract. The `@EntityListeners(AuditingEntityListener.class)` annotation is required for
> Spring Data's `@CreatedBy` / `@CreatedDate` / `@LastModifiedBy` / `@LastModifiedDate` fields.

**Libraries:**

- Primary: `com.positivity.shared.id.UUIDv7Generator` (platform-shared Hibernate generator)
- Hibernate `@IdGeneratorType` (Hibernate 6.x, part of Spring Boot 3+) — **do not use** the deprecated `@GenericGenerator`

**Database:**

- PostgreSQL: `UUID` column type (native support)
- MySQL: `BINARY(16)` or `CHAR(36)` depending on version
- H2: `UUID` type for testing

### 3. Frontend Implementation (TypeScript/Vue)

**Decision:** ✅ **Resolved** - Use String representation of UUID in TypeScript/JavaScript. Frontend does not need to generate UUIDs—backend is source of truth for entity IDs.

**Implementation:**

```typescript
// Type definition
interface Order {
  id: string; // UUID as string
  customerId: string; // UUID as string
  status: OrderStatus;
  // ...
}

// API call
const response = await api.get<Order>(`/api/v1/orders/${orderId}`);
// orderId: "018e1c9f-6b5a-7890-abcd-1234567890ab"
```

**Validation:**

```typescript
// UUID v7 regex pattern for validation
const UUID_V7_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isValidUUIDv7(id: string): boolean {
  return UUID_V7_PATTERN.test(id);
}
```

**Rationale:**

- Native `String` type in JavaScript/TypeScript—no conversion needed
- JSON serialization is automatic and lossless
- Simple to display, log, and debug
- Frontend never generates UUIDs; backend owns identity

### 4. REST API Serialization

**Decision:** ✅ **Resolved** - Serialize UUIDs as hyphenated lowercase strings in JSON. Jackson handles conversion automatically.

**Format:**

```json
{
  "id": "018e1c9f-6b5a-7890-abcd-1234567890ab",
  "customerId": "018e1c88-1234-7000-8000-abcdef123456",
  "createdAt": "2026-02-07T12:34:56Z"
}
```

**Spring Boot Configuration:**

```java
// Jackson automatically serializes UUID as hyphenated string
// No custom configuration needed—works out of the box
```

### 5. Migration Strategy for Existing Entities

**Decision:** ✅ **Resolved** - New entities use UUID v7; existing entities remain unchanged unless explicitly migrated. No forced migration—evaluate case-by-case.

**Guidelines:**

- **New services/entities**: Must use UUID v7
- **Existing entities**: Keep current IDs unless there's a compelling reason to migrate
- **Foreign keys**: New relationships to legacy entities continue using legacy ID types
- **Migration triggers**: Security concerns, data merges, or major refactoring

**Migration Pattern (if needed):**

```sql
-- Add UUID column
ALTER TABLE legacy_table ADD COLUMN uuid_id UUID;

-- Generate UUIDs for existing records
UPDATE legacy_table SET uuid_id = gen_uuid_v7();

-- Transition period: support both IDs
-- Eventually: make uuid_id primary, deprecate old id
```

### 6. Addendum (2026-02-23): Hibernate `@IdGeneratorType` standard

**Decision:** ✅ **Resolved** - Standardize UUID v7 ID assignment on Hibernate's `@IdGeneratorType` pattern and deprecate per-entity `@PrePersist` ID generation.

**Policy update:**

- Keep UUID creation logic centralized in `com.positivity.shared.id.UUIDv7Generator`.
- Use the shared `@UUIDv7Id` annotation that is meta-annotated with `@IdGeneratorType(UUIDv7Generator.class)`.
- Apply entity IDs with **all three** of the following annotations:
  - `@Id`
  - `@GeneratedValue` (required for Hibernate to invoke the registered generator)
  - `@UUIDv7Id` (platform annotation wiring the generator)
- Add `@EntityListeners(AuditingEntityListener.class)` to every entity class that uses Spring Data audit annotations (`@CreatedBy`, `@CreatedDate`, `@LastModifiedBy`, `@LastModifiedDate`).
- Do not use `@GenericGenerator` for new code (deprecated since Hibernate 6.5 and marked for removal).
- Do not use `@PrePersist` for ID generation — use the `@GeneratedValue` + `@UUIDv7Id` pattern instead.
- Avoid duplicating UUID generation implementations in domain modules.
- Existing entities using `@PrePersist` for IDs can remain temporarily, but touched entities should migrate to the shared `@IdGeneratorType` pattern.

**Canonical example:**

```java
import com.positivity.shared.id.UUIDv7Id;
import jakarta.persistence.*;
import org.springframework.data.annotation.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "advance_shipping_notice")
@EntityListeners(AuditingEntityListener.class)   // required for @CreatedBy / @CreatedDate etc.
public class AdvanceShippingNoticeEntity {

    @Id
    @GeneratedValue                               // triggers Hibernate @IdGeneratorType hook
    @UUIDv7Id                                     // resolves to UUIDv7Generator
    private UUID asnId;

    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

---

## Alternatives Considered

### 1. UUID v4 (Random UUID)

**Rejected Reason:** Random UUIDs cause index fragmentation in databases, degrading insert performance over time. Time-ordered UUID v7 solves this while retaining all benefits of UUIDs.

### 2. ULID (Universally Unique Lexicographically Sortable Identifier)

**Pros:** Base32-encoded, URL-safe, sortable  
**Rejected Reason:** Less standard than UUID; requires custom parsing/validation on frontend; not natively supported by PostgreSQL or Java without additional libraries. UUID v7 provides similar benefits with broader support.

### 3. Twitter Snowflake IDs

**Pros:** Sortable, smaller than UUIDs (64-bit)  
**Rejected Reason:** Requires centralized ID generation service or careful clock synchronization; 64-bit may not be sufficient for very long-lived systems; not a standard format.

### 4. Auto-Increment Integers

**Pros:** Small size, database-native  
**Rejected Reason:** Security risk (enumeration), requires database coordination, not suitable for distributed systems.

### 5. Sequential GUIDs (COMB)

**Pros:** Similar to UUID v7  
**Rejected Reason:** UUID v7 is now an RFC standard (9562) with better tooling and library support. COMB is Microsoft-specific.

---

## Consequences

### Positive ✅

- ✅ **Security**: Non-enumerable IDs prevent information leakage and enumeration attacks
- ✅ **Distributed generation**: Services generate IDs independently without coordination
- ✅ **Global uniqueness**: No collisions across services, environments, or time
- ✅ **Time-ordered**: UUID v7 maintains database index performance with temporal locality
- ✅ **Cross-platform**: String serialization works seamlessly between Java backend and TypeScript frontend
- ✅ **Standard compliance**: RFC 9562 ensures interoperability and tool support
- ✅ **Future-proof**: Supports multi-region deployments and data migrations

### Negative ⚠️

- ⚠️ **Storage overhead**: 128-bit UUIDs are larger than 32/64-bit integers (mitigated by modern storage costs and compression)
- ⚠️ **Index size**: Larger indexes consume more memory (mitigated by UUID v7's time-ordering reducing fragmentation)
- ⚠️ **Readability**: UUIDs are harder to read/remember than integers (mitigated by copy-paste and logging tools)
- ⚠️ **Library dependency**: UUID v7 requires external library until JDK native support (mitigated by lightweight, well-maintained libraries)
- ⚠️ **Migration complexity**: Existing entities with integer IDs require careful migration planning (mitigated by "new entities only" approach)

### Neutral

- 📝 **String representation on frontend**: TypeScript uses strings naturally—no conversion overhead
- 📝 **Database choice matters**: PostgreSQL has native UUID support; MySQL may need BINARY(16) tuning
- 📝 **URL length**: UUIDs make URLs longer but not prohibitively (36 characters)

---

## Implementation Notes

### Required Components

**Backend (Java/Spring Boot):**

- Library: `com.github.f4b6a3:uuid-creator:5.3.7` (Maven Central)
- JPA: Native `UUID` type support
- Jackson: Default UUID serialization (no config needed)

**Frontend (TypeScript/Vue):**

- No library needed—use native `string` type
- Optional: UUID validation regex for input validation

**Database:**

- PostgreSQL: `UUID` column type (recommended)
- MySQL 8.0+: `BINARY(16)` with custom functions
- H2 (testing): `UUID` type

### Configuration

**Maven dependency:**

```xml
<dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>uuid-creator</artifactId>
    <version>5.3.7</version>
</dependency>
```

**PostgreSQL schema:**

```sql
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- ...
);

-- Index on foreign keys
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
```

### Testing Strategy

**Unit tests:**

- Verify UUID v7 generation produces valid format
- Verify time-ordering property (newer UUIDs > older UUIDs)
- Verify uniqueness across concurrent threads

**Integration tests:**

- Test JSON serialization/deserialization
- Verify frontend receives valid UUID strings
- Test database persistence and retrieval

**Performance tests:**

- Benchmark insert performance vs auto-increment
- Measure index fragmentation over time
- Monitor query performance on UUID foreign keys

### Rollout Plan

1. **Phase 1 (Immediate):** Add UUID v7 library to pos-dependencies
2. **Phase 2 (Sprint 1):** New services (e.g., pos-events) use UUID v7
3. **Phase 3 (Sprint 2-3):** Update REST API standards documentation
4. **Phase 4 (Ongoing):** Existing services adopt UUID v7 for new entities
5. **Phase 5 (Future):** Evaluate migration of legacy entities (case-by-case)

### Metrics & Monitoring

- **Insert performance**: Monitor `INSERT` latency for UUID tables vs legacy tables
- **Index size**: Track index growth over time
- **Query performance**: Monitor JOIN performance on UUID foreign keys
- **Error rates**: Track UUID parsing/validation errors in API gateway

---

## References

- **RFC 9562**: UUID Specification including UUID v7 - <https://www.rfc-editor.org/rfc/rfc9562.html>
- **UUID Creator Library**: <https://github.com/f4b6a3/uuid-creator>
- **Related ADRs**:
  - [ADR-0009: Backend Domain Responsibilities](0009-backend-domain-responsibilities.adr.md)
  - [ADR-0010: Frontend Domain Responsibilities](0010-frontend-domain-responsibilities-guide.adr.md)
  - [ADR-0011: API Gateway Security Architecture](0011-api-gateway-security-architecture.adr.md)
- **Related Documentation**:
  - [Backend Architecture Guide](../../durion-positivity-backend/docs/ARCHITECTURE_GUIDE.md)
  - [Java Instructions](../../.github/instructions/java.instructions.md)
- **External Resources**:
  - PostgreSQL UUID Documentation: <https://www.postgresql.org/docs/current/datatype-uuid.html>
  - Jackson UUID Serialization: <https://github.com/FasterXML/jackson-databind>

---

## Sign-Off

| Role | Name | Date | Notes |
| ------ | ------ | ------ | ------- |
| Architecture | Platform Team | 2026-02-07 | Approved UUID v7 strategy |
| Backend Lead | Platform Team | 2026-02-07 | Confirmed Spring Boot compatibility |
| Frontend Lead | Platform Team | 2026-02-07 | Confirmed TypeScript string representation |

---

## Timeline

- **Proposed**: 2026-02-07
- **Accepted**: 2026-02-07 (immediate adoption for new entities)
- **Implementation Started**: 2026-02-07 (add library dependency)
- **Implementation Ongoing**: New services and entities adopt UUID v7

---

## Changelog

- **2026-02-07**: Initial draft and acceptance—UUID v7 identifier strategy for platform
- **2026-02-23**: Addendum: standardized generator-based assignment using shared `UUIDv7Generator`
