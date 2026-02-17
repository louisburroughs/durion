# ADR-0015: Person, Customer, and User Entity Semantics and Relationships

**Status:** PROPOSED  
**Date:** 2026-02-17  
**Deciders:** Architecture, Backend Lead, Security Lead  
**Affected Issues:** N/A

---

## Context

The Durion platform manages several core identity and relationship concepts: `Person`, `Customer`, and `User`. These entities are referenced across multiple modules:

- `pos-people`: Manages internal and external individuals (`Person`)
- `pos-customer`: Manages external relationships and parties (`Customer`, `Party`)
- `pos-security-service`: Manages authentication and authorization (`User`)

Ambiguity in the definitions and relationships of these entities has led to confusion in data modeling, security, and business logic. A clear, system-wide definition is required to ensure consistent handling of identity, access, and relationship status.

---

## Decision

### 1. Person

**Definition:**
A `Person` is a unique individual who has a relationship with the Organization. This includes employees, contractors, and any individual with an internal or external association.

- Each `Person` is uniquely identified in the system.
- A `Person` may have one or more relationships (roles, assignments) within the Organization.
- A `Person` can only have **one ACTIVE User** at a time. An "ACTIVE" user is defined as one who can authenticate and whose account is not locked, disabled, or expired.

### 2. Customer

**Definition:**
A `Customer` is a unique individual, Party, or member of a Party that has an **external** relationship with the Organization.

- A `Customer` may be a `Person`, a `Party` (group, company, household), or a member of a `Party`.
- `Customer` status is managed in the `pos-customer` module.
- A `Customer` (not `Party`) may or may not have a corresponding `User` or `Person` entity. A `Party` has a `Person` as a primary contact

### 3. User

**Definition:**
A `User` is an entity with a security relationship to the system (i.e., can authenticate and is subject to authorization policies).

- A `User` is managed in the `pos-security-service` module.
- A `User` must be linked to a `Person`. The concept of a `User` not linked to a `Person` is deprecated.
- Multiple `User` entities can exist for a single `Person` for legacy, migration, or administrative reasons (e.g., a test account).
- However, a `Person` can only have **one ACTIVE User** at a time. An "ACTIVE" user is defined as one who can authenticate and whose account is not locked, disabled, or expired.

### 4. Status and Relationship Impacts

- Changes in status for a `Person`, `Customer`, or `Party` **must impact** the status of associated `User` entities. For example:
  - Disabling or archiving a `Person` MUST disable their active `User` account.
  - Terminating a `Customer` relationship MUST trigger a review and potential disabling of the associated `User` account.
- The system will attempt to reconcile duplicate `Person`, `Customer`, and `Party` records, but **not duplicate `User` entities**.
- Only one `User` can be ACTIVE for a given `Person` at a time; others must be INACTIVE or ARCHIVED.

### 5. Reconciliation and Uniqueness

- The system will attempt to merge/reconcile duplicate `Person`, `Customer`, and `Party` records to maintain a single source of truth.
- Duplicate `User` entities are allowed for migration/legacy, but only one can be ACTIVE per `Person`.

**Decision:** ✅ **Resolved** - Adopt the above definitions and constraints for `Person`, `Customer`, and `User` entities across all modules.

---

## Alternatives Considered

1. **Single Entity for All Roles:** Use a single `Party` or `Person` entity for all relationships (internal, external, security). Rejected due to complexity and loss of domain clarity.
2. **Allow Multiple Active Users per Person:** Rejected for security and audit reasons.
3. **No Reconciliation of Duplicates:** Rejected due to data quality and operational risk.

---

## Consequences

### Positive ✅

- ✅ Clear separation of concerns between identity, relationship, and security
- ✅ Improved data quality and auditability
- ✅ Enables consistent enforcement of security and business rules

### Negative ⚠️

- ⚠️ Complexity in reconciliation logic for merging duplicates (mitigated by clear rules and tooling)
- ⚠️ Migration/legacy scenarios may require manual intervention to enforce single active user per person

### Neutral

- Neutral impact on existing API contracts; changes are internal to entity management

---

## Implementation Notes

- Modules affected: `pos-people`, `pos-customer`, `pos-security-service`
- Enforce one ACTIVE User per Person in business logic and database constraints
- Document reconciliation and status propagation logic in module-level READMEs
- Add integration tests for status change propagation

---

## References

- [pos-people](../../durion-positivity-backend/pos-people/README.md)
- [pos-customer](../../durion-positivity-backend/pos-customer/README.md)
- [pos-security-service](../../durion-positivity-backend/pos-security-service/README.md)
- [ADR-0009: Backend Domain responsibilities](0009-backend-domain-responsibilities.adr.md)
- [ADR-0013: UUID v7 Identifier Strategy](0013-uuid-v7-identifier-strategy.adr.md)

---

## Sign-Off

| Role            | Name         | Date       | Notes                |
|-----------------|--------------|------------|----------------------|
| Architecture    | LMB        | 2026-02-17 | Have to revisit people-crm relationship to have all `Person` relationships in people|
| Backend Lead    | LMB       | 2026-02-17 |                      |
| Security Lead   | LMB        | 2026-02-17 |                      |

---

## Timeline

- **Proposed**: 2026-02-17
- **Accepted**: 2026-02-17

---

## Changelog

- **2026-02-17**: Initial draft
