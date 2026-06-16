# ADR-0015: Person, Customer, and User Entity Semantics and Relationships

**Status:** ACCEPTED  
**Date:** 2026-02-17 (accepted 2026-06-16)  
**Deciders:** Architecture, Backend Lead, Security Lead  
**Affected Issues:** N/A  
**Realized by:** [PLAN-person-unification](../capabilities/PLAN-person-unification.md)

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

### 6. Person Unification Invariants

Resolving the 2026-02-17 sign-off note (*"revisit people-crm relationship to have
all `Person` relationships in people"*), the following invariants are adopted.
`pos-people.person` is the single source of truth for every individual.

- **I1** — Every `pos-customer.person_party.person_id` references an existing
  `pos-people.person.id`. No orphan links; the `PeopleClient` local-id fallback is
  disabled outside development.
- **I2** — Person name and contact attributes are owned by `pos-people`.
  `pos-customer` references them; it does not master them.
- **I3** — The Customer Directory is the union of `commercial_party` and the
  standalone individual customers in `person_party`, each tagged by `partyType`.
- **I4** — The People Directory lists all `person` rows, filterable by type;
  `status = null` denotes a non-employee person (customer contact, individual).

### Remediation status (per PLAN-person-unification)

| Phase | Scope | Status |
|---|---|---|
| 1 | Customer Directory unions commercial + individual customers (I3) | ✅ implemented |
| 2 | Repair commercial-contact person names + status in `pos-people` (I4) | ✅ implemented |
| 3 | `pos-people` as SoT; demote `person_party` to a link (I1, I2) | ▶ in progress — **OD1 resolved: 3a thin-link** |

#### Phase 3 (3a thin-link) execution sequence

Must be staged; each step ships independently and keeps reads working.

1. **Reconcile identity ids (prerequisite for I1).** Seed/data currently links
   contact `person_party.person_id` to `01960025-*` while the canonical
   `pos-people.person` rows are `01960026-*`. Repoint `person_party.person_id`
   to the canonical `pos-people.person.id`; reconcile orphans. Until this holds,
   demotion cannot read identity from `pos-people`.
2. **Add identity read path.** `PeopleClient` batch fetch of person
   name/contact by id; pos-people exposes a get-by-ids endpoint if absent.
3. **Rewrite pos-customer readers** (contact summaries, `GetPersonResponse`,
   individual-customer directory display) to source name/contact from
   `pos-people` instead of `person_party` columns.
4. **Drop duplicated columns** from `person_party` (name, contact) once no
   reader depends on them.
5. **Disable `PeopleClient` local-id fallback** outside dev; add orphan
   reconciliation/guard (I1).

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
- **2026-06-16**: Accepted. Added Person Unification Invariants (I1–I4) and
  remediation status; linked PLAN-person-unification. Phases 1–2 implemented;
  Phase 3 pending OD1.
