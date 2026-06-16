# PLAN: Person Unification — single person identity across pos-people / pos-customer

**Status:** DRAFT
**Date:** 2026-06-16
**Owner:** Architecture / Backend Lead
**Anchors:** [ADR-0015](../adr/0015-identity-entity-relationships.adr.md) (PROPOSED), [ADR-0022](../adr/0022-audit-stable-person-identifier-claim-policy.adr.md), [SPEC-people-directory-backend](SPEC-people-directory-backend.md)

---

## 1. Problem statement

Two observable asymmetries reported on durionpos.org:

1. **Commercial parties appear in the People list.** 3 of 106 people rows are named after companies+departments (`"General Piedmont Freight"`, `"Operations Tarheel Logistics"`, `"Accounts Rowan Road Services"`).
2. **Individual (person) customers do not appear in the Customer list.** The CRM Customer Directory shows only commercial organizations (20 LLCs), never individual person-customers.

Root cause: person identity is split across **three stores with no unified read model**:

| Store | Service | Holds | Authoritative? |
|---|---|---|---|
| `person` | pos-people | employees + non-employee persons (`status=null`) | **intended SoT** (ADR-0015) |
| `person_party` | pos-customer | individual customers + commercial-account contacts | duplicate person data |
| `commercial_party` | pos-customer | organizations | yes (orgs only) |

`AbstractParty` uses `InheritanceType.TABLE_PER_CLASS` → `commercial_party` and `person_party` are physically separate tables with no shared parent table; concrete-typed repositories each see only their own slice.

### Evidence (code)

- `PartyServiceImpl.browseParties()` → `partyRepository.findAll()` where `partyRepository : CommercialPartyRepository` → **commercial_party only**.
- `pos-people/.../R__seed_people_operational_data.sql:125+` seeds `(first='General', last='Piedmont Freight')` style rows (namespace `01960026`) — the role+company names.
- `person_party.person_id` already carries the canonical pos-people `personId` (set via `PeopleClient.resolveOrCreatePersonId`) — **the unifying link already exists, it is just unused for listing**.
- `PeopleClient.resolveOrCreatePersonId` has `allowLocalFallback`: on resolve failure it generates a local UUID, so `person_party.person_id` can orphan from pos-people.

---

## 2. Target model (per ADR-0015 sign-off)

ADR-0015 sign-off note (LMB): *"Have to revisit people-crm relationship to have all `Person` relationships in people."*
SPEC-people-directory confirms `person.status = null` is the intended home for non-employee persons.

```
pos-people.person                ← SINGLE source of truth for every human
   status = null    → non-employee person (customer contact, individual customer)
   status = ACTIVE… → employee
        ▲ personId (canonical UUID v7, stable per ADR-0022)
        │ (referenced, never duplicated)
pos-customer:
   commercial_party               ← organizations          (Customer = Party)
   person_party                   ← "this person is a customer"  (person_id → person.id)
   party_relationship             ← commercial_party ──contact──▶ person_party
   contact_point                  ← email/phone for person_party
```

**Invariants:**
- I1 — Every `person_party.person_id` references an existing `pos-people.person.id` (no orphans; `allowLocalFallback` disabled in non-dev).
- I2 — Person name/contact is owned by pos-people; pos-customer reads it, does not master it.
- I3 — Customer Directory = `commercial_party` ∪ `person_party`, each tagged by `partyType`.
- I4 — People Directory = all `person` rows, filterable by type (existing SPEC-people-directory).

---

## 3. Phased delivery

### Phase 1 — Customer Directory shows all customers *(visible fix; lowest risk)*

**Goal:** individuals appear alongside commercial parties in the CRM Customer Directory.

**Backend (pos-customer):**
- Add `PersonPartyRepository` paged listing (already `JpaRepository<PersonParty>`; add `findAll(Pageable)` use + name/search query exists at `PersonPartyRepository:25`).
- New service method `PartyService.browseCustomers(Pageable, type)` returning a unified `SearchPartiesResponse` where each `PartySummary` carries `partyType` (`COMMERCIAL` | `PERSON`).
  - Implementation option A (preferred): two paged queries (commercial + person), merge + sort in service, single page window. Simpler, avoids `TABLE_PER_CLASS` UNION pitfalls.
  - Option B: native `UNION ALL` projection view `v_customer_directory(party_id, display_name, party_type, primary_contact, vehicle_count)`.
- Keep `browseParties` (commercial-only) for back-compat or redirect callers; prefer replacing per pre-production policy.

**Frontend (durion-positivity-frontend):**
- Customer Directory: add a **type column** + filter chips (All / Commercial / Individual), mirroring People Directory chips.
- Row click routes by type: commercial → `/app/crm/party/{id}`, person → individual detail.

**Acceptance:**
- Customer Directory lists both seed commercial parties and seeded individual person-customers.
- Filter chips narrow correctly; counts match DB.

**Stories:** `CAP-XXX.1` BE unified browse; `CAP-XXX.2` FE directory type filter.

---

### Phase 2 — Fix contact-person naming *(data quality)*

**Goal:** commercial-contact persons read as real humans, not `department + company`.

**Cause:** `pos-people/R__seed_people_operational_data.sql:125+` (and historical `resolveOrCreatePersonId` calls from the deprecated `contact` flow) seeded `first=<dept>, last=<company>`.

**Work:**
- Correct the seed rows (namespace `01960026`) to real contact first/last names (align with `pos-customer` `CUST-CPC-*` person_party rows: Greg Whitfield, Teresa Mullen, …).
- One-shot migration `Vn__fix_commercial_contact_person_names.sql` in pos-people to repair existing rows where `person.id` ∈ `01960026-*` and name looks role+company.
- Verify no employee rows (`status IS NOT NULL`) are touched.

**Acceptance:** People Directory shows named humans; zero company-named persons remain.

**Story:** `CAP-XXX.3` BE seed + migration.

---

### Phase 3 — Person as single source of truth *(ADR-0015 realization; cross-repo)*

**Goal:** stop duplicating person data; pos-people authoritative, pos-customer references.

**Decision required (sub-options):**
- **3a (thin link):** demote `person_party` to `{person_id, customer_number, customer status/tier, party_relationship FKs}`; drop `first_name/last_name/primary_address`; read identity from pos-people via client/projection. Cleanest; biggest change.
- **3b (read cache + events):** keep `person_party` columns as a denormalized cache, but make pos-people authoritative on write and sync via domain events (ADR-0027: `PersonUpdated` → pos-customer updates cache). Lower coupling on read path; eventual consistency.

**Cross-cutting:**
- Disable `PeopleClient.allowLocalFallback` outside dev → enforce I1 (no orphan person_id).
- Backfill/repair: every existing `person_party.person_id` must resolve to a `person` row; reconcile orphans.
- Promote **ADR-0015 PROPOSED → ACCEPTED**, add unification rules (I1–I4) + per-module remediation checklist (model on ADR-0022 checklist).
- Status propagation (ADR-0015 §4): person disable/terminate → customer + user review.

**Acceptance:** single person record per human; pos-customer holds no independent person name/contact; ADR-0015 ACCEPTED with checklist green.

**Stories:** `CAP-XXX.4` ADR-0015 promotion + checklist; `CAP-XXX.5` person_party demotion (3a) or event sync (3b); `CAP-XXX.6` fallback hardening + orphan reconciliation.

---

## 4. Cross-repo story map

| # | Repo | Title | Depends on |
|---|---|---|---|
| 1 | pos-customer | Unified `browseCustomers` (commercial ∪ person) | — |
| 2 | frontend | Customer Directory type filter + routing | 1 |
| 3 | pos-people | Fix commercial-contact person names (seed + migration) | — |
| 4 | durion (docs) | Promote ADR-0015 → ACCEPTED + remediation checklist | — |
| 5 | pos-customer / pos-people | person_party demotion **(3a)** or event sync **(3b)** | 4 |
| 6 | pos-customer | Disable local fallback + reconcile orphan person_id | 4 |

**Critical path:** 4 → 5/6 (Phase 3 gated on the ADR decision). Phases 1 and 2 are independent and shippable immediately.

---

## 5. ADR compliance

| ADR | Bearing |
|---|---|
| ADR-0011 | business logic in service layer (unified browse, name repair) |
| ADR-0013 | UUID v7 person ids preserved |
| ADR-0022 | stable person identifier — do **not** rewrite `person.id`; only names (Phase 2) |
| ADR-0023 | prefer Specification/projection over raw SQL where expressible (Phase 1 Option A) |
| ADR-0026 | paginate unified Customer Directory |
| ADR-0027 | event-driven sync if Phase 3b chosen |

---

## 6. Risks

| Risk | Phase | Mitigation |
|---|---|---|
| `TABLE_PER_CLASS` UNION performance | 1 | prefer two-query merge (Option A) or materialized view |
| Rewriting person.id breaks audit refs | 2 | repair **names only**, never ids (ADR-0022) |
| Orphan `person_id` from prior fallback | 3 | reconciliation pass before disabling fallback |
| Eventual-consistency drift (3b) | 3 | choose 3a if strong consistency required |

---

## 7. Open decisions

- **OD1:** Phase 3 = thin link (3a) or event-synced cache (3b)?
- **OD2:** Phase 1 unified read = two-query merge (A) or DB view (B)?
- **OD3:** Should individual person-customer detail reuse the People detail page or a CRM-specific view?
