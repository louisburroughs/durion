# ADR-0039: User–Person Linkage Authority and Translation

**Status:** PROPOSED
**Date:** 2026-06-19
**Deciders:** Architecture, Backend Lead, Security Lead
**Affected Issues:** durion-positivity-backend#714 (seed: user without person)

---

## Context

[ADR-0015](0015-identity-entity-relationships.adr.md) §3 requires that *"a `User`
must be linked to a `Person`"* and §7 (I5–I7) makes that enforceable. [ADR-0022](0022-audit-stable-person-identifier-claim-policy.adr.md)
requires tokens to carry a stable `personId` claim for audit identity. Neither ADR
specifies **how** the link is stored authoritatively, how the token `personId` is
derived, or how services translate between a `userId` and a `personId`.

The 2026-06-19 investigation found concrete drift:

- The user↔person relationship is recorded in **two** places that can diverge:
  - `pos-people.user_person_links` (dedicated table + `UserPersonTranslationService`,
    with `UNIQUE(user_id)` and FK `person_id → person(id)`), and
  - `pos-security-service.users.person_id` (a column on the `User` entity).
- `admin.alpha` was seeded with **neither** populated → a user with no person, and a
  token with **no `personId` claim** (`JwtServiceImpl` emits it only when non-null).
- No cross-service caller uses the translation service; person-scoped flows accept a
  client-supplied `personId` and rely solely on the `person(id)` FK as a backstop.

Without a single authority and a derivation rule, `userId` and `personId` are mixed
across stories, and the ADR-0015 §3 / ADR-0022 invariants cannot hold.

---

## Decision

### 1. Single authoritative link store
✅ **Resolved** — `pos-people.user_person_links` is the **sole source of truth** for
the user↔person relationship (ADR-0015 I5). All reads/writes of the link go through
`pos-people`.

### 2. `users.person_id` is a derived cache (or is removed)
✅ **Resolved** — `pos-security-service.users.person_id` MUST NOT be authored
independently. It is either:
- **(preferred)** removed, and the security service resolves `personId` from
  `pos-people` (`GET /v1/people/users/{userId}/person`) at token-issue time, caching
  only within the request/token lifecycle; or
- retained strictly as a denormalized mirror that is written **only** as a
  projection of the active `user_person_link` (e.g. via the link event), never by
  user-CRUD code.

Implementations MUST choose one and document it; divergent dual-writes are prohibited.

### 3. Token `personId` derivation
✅ **Resolved** — the `personId` claim (ADR-0022) MUST be derived from the active
`user_person_link`. Token issuance for a user with no active link MUST follow
ADR-0022 §4 fallback **and** emit a structured warning/metric; once §7/I6 enforcement
is live, issuance MUST treat "no person" as a policy failure rather than silently
omitting the claim.

### 4. Translation mandatory; no conflation
✅ **Resolved** — no service may pass a `userId` where a `personId` is expected, or
vice-versa (ADR-0015 I7). Crossing the boundary MUST use
`UserPersonTranslationService.getPersonUuidForUser` (in-process, pos-people) or the
`GET /v1/people/users/{userId}/person` contract (cross-service). Person-owned tables
retain their FK to `pos-people.person(id)` as a defense-in-depth backstop, not as the
primary guard.

### 5. Enforcement and reconciliation
✅ **Resolved** —
- A reconciliation endpoint/job MUST report every ACTIVE `User` with no active person
  link (analogous to the ADR-0015 I1 person-link reconcile), surfaced in CI/health.
- Seeds MUST NOT create users without persons; seed validation fails the build
  otherwise.
- DB/uniqueness: `user_person_links` keeps `UNIQUE(user_id)`; at most one `ACTIVE`
  link per `user_id`, and (ADR-0015 §1/§3) at most one ACTIVE user per person.

### 6. Self-service ownership check
✅ **Resolved** — endpoints that act on a caller's own person (timekeeping, work
sessions, self-service profile) MUST verify the supplied `personId` equals the
caller's linked person (`isUserLinkedToPerson(userId, personId)`); FK validity is not
sufficient authorization.

---

## Alternatives Considered

1. **Keep dual stores, reconcile periodically.** Rejected — drift windows produce
   exactly the userId/personId confusion this ADR removes.
2. **Make `pos-security-service` the authority for the link.** Rejected — conflicts
   with ADR-0015 (pos-people is the person SoT) and splits identity ownership.
3. **Derive `personId` lazily in each consumer instead of the token.** Rejected —
   every consumer would re-call pos-people; ADR-0022 already standardizes the claim.

---

## Consequences

### Positive ✅
- Single authority eliminates userId/personId drift and conflation.
- Token `personId` becomes reliable, satisfying ADR-0022 end to end.
- Clear, testable invariants (reconcile job + seed validation).

### Negative ⚠️
- Token issuance gains a dependency on pos-people link resolution (mitigate with
  caching + ADR-0022 fallback during migration).
- Removing/decoupling `users.person_id` is a migration with compatibility windows.

### Neutral
- No change to external API contracts beyond the existing translation endpoint.

---

## Implementation Notes

- Modules affected: `pos-security-service` (token issuance, `users.person_id`),
  `pos-people` (link authority, reconcile), all consumers of person-scoped flows.
- Add reconcile coverage for "ACTIVE user without person"; wire into health/CI.
- Add seed validation asserting every ACTIVE user has a person link.
- Fix the admin.alpha seed gap (durion-positivity-backend#714) as the first step.

---

## References

- [ADR-0015: Person, Customer, and User Entity Semantics and Relationships](0015-identity-entity-relationships.adr.md) (§3, §7 I5–I7)
- [ADR-0018: Audit Actor Fields From Security Context](0018-audit-actor-fields-from-security-context.adr.md)
- [ADR-0022: Audit Stable Person Identifier Claim Policy](0022-audit-stable-person-identifier-claim-policy.adr.md)
- [ADR-0013: Platform UUID Identifier Strategy](0013-platform-uuid-identifier-strategy.adr.md)

---

## Sign-Off

| Role         | Name | Date       | Notes |
|--------------|------|------------|-------|
| Architecture |      |            | Pending review |
| Backend Lead |      |            | Pending review |
| Security Lead|      |            | Pending review |

---

## Timeline

- **Proposed**: 2026-06-19

---

## Changelog

- **2026-06-19**: Initial draft. Establishes pos-people `user_person_links` as the
  sole link authority, derives token `personId` from it, mandates translation (no
  userId/personId conflation), and adds reconcile + seed-validation enforcement.
  Prompted by durion-positivity-backend#714.
