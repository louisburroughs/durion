# ADR-0047: Ledger Inalterability and Fiscal Positions Are Accounting Non-Goals

**Status:** ACCEPTED **Date:** 2026-07-17 **Deciders:** Architecture, Backend Lead, Accounting Domain **Affected Issues:** durion#357 (story A4 of the accounting parity plan;
transferred from durion-positivity-backend#938)

---

## Context

The accounting parity plan surveyed external accounting parity expectations against `pos-accounting` and produced an evidence-based gap register. Two gaps were resolved as
deliberate non-goals rather than build items, and this ADR is the durable record of those decisions so they are not re-litigated story by story:

- **G4 — no inalterability/hash chain.** Some accounting stacks provide chained hashing of posted moves for jurisdictions whose fiscal law mandates tamper-evident ledgers.
  `pos-accounting` today enforces immutability of posted journal entries only through service-layer guards ("soft immutability").
- **G15 — no fiscal-position analog.** Fiscal position models remap GL accounts and taxes per counterparty (e.g. exempt entities, special program accounts). positivity's
  `GLMappingResolver` keys on posting category, mapping key, `dimensions`, and date only — there is no per-counterparty account substitution layer.

Drivers: positivity is a single-currency, event-driven POS platform. Plan ground rule 6 (scope guard) already excludes statutory localization; both features primarily satisfy
compliance or business situations that do not apply here. The plan's decisions table entries D-2 and D-8 resolved both gaps as non-goals and directed that they be recorded via
ADR (story A4, scheduled in Wave 1 so the record exists before ledger-hardening work lands).

Scope: `durion-positivity-backend/pos-accounting` and its ledger/posting-configuration surface.

---

## Decision

Two non-goal decisions, each with an explicit revisit trigger. "Non-goal" means: do not build, do not scaffold, and reject story-level scope creep toward these features unless
the revisit trigger fires and this ADR is superseded or amended.

### 1. D-2 — No chained-hash ledger inalterability in pos-accounting

**Decision:** ✅ **Resolved** — Do not build a chained-hash tamper-evidence mechanism for posted journal entries.

- **Why:** Chained-hash inalterability exists for jurisdictions whose fiscal law mandates tamper-evident ledgers. No such statutory or compliance mandate applies to this
  platform today.
- **What covers the operational need instead:**
  - `AccountingAuditLog` — actor-attributed audit trail of accounting mutations;
  - event lineage — the transactional outbox (`EventOutbox`/`OutboxProcessor`) and `ProcessedEvent` dedup rails give an at-least-once, replayable record of every
    posting-relevant event (per [ADR-0044](0044-platform-event-only-domain-walls.adr.md) reliability mechanisms);
  - soft immutability of posted journal entries, hardened by DB-level balance enforcement (plan story A1, deferrable constraint trigger) and the completed reversal lifecycle
    (plan story A3, "reverse, never delete" with two-way linkage).
- **Revisit trigger:** a statutory or compliance requirement for tamper-evident ledgers appearing (new jurisdiction, regulatory change, or a customer/audit contractual
  mandate). If triggered, implement per the appendix sketch below and amend or supersede this ADR.

### 2. D-8 — No fiscal-position analog (per-counterparty GL account substitution)

**Decision:** ✅ **Resolved** — Do not build a per-counterparty account/tax remapping layer in pos-accounting.

- **Why:** Fiscal position remapping targets accounts and taxes per counterparty. positivity has no current use case: single-currency, jurisdiction-first tax delegation, and
  no counterparty-driven posting requirements.
- **Recorded escape hatch:** the `dimensions` map on accounting `MappingKey` records is the designated implementation path if fleet- or national-account-specific posting is
  ever needed — a counterparty-classifying dimension (e.g. `accountProgram`) on the relevant mapping keys resolves to substitute accounts through the existing
  `GLMappingResolver` without a new substitution layer. (This mirrors the mechanism recorded for plan decision D-4's per-jurisdiction backlog path.)
- **Revisit trigger:** a product requirement for counterparty-specific posting (e.g. fleet or national-account programs demanding distinct GL treatment). If triggered, prefer
  the `dimensions` escape hatch before considering a dedicated fiscal-position construct.

---

## Alternatives Considered

1. **Build those features for parity's sake** — rejected: both exist to satisfy compliance or business contexts absent here; they would add schema, posting-rule, and
   operational surface with no capability gain (violates the plan's scope guard, ground rule 6).
2. **Leave the gaps undocumented (silent backlog)** — rejected: without a durable record the questions recur in every ledger-adjacent story review; an ACCEPTED ADR with
   revisit triggers ends the discussion until conditions actually change.
3. **Partial builds** (hash column without verification job; counterparty field without resolver support) — rejected: half-built tamper-evidence is worse than none (false
   assurance), and speculative schema violates the pre-production policy against unused surface.

---

## Consequences

### Positive ✅

- ✅ Ledger-hardening stories (A1–A3) proceed without hash-chain coupling; no restrict-mode operational flag to manage.
- ✅ COA and posting-rule surface stays minimal; `GLMappingResolver` remains the single account resolution mechanism.
- ✅ Both decisions carry explicit, checkable revisit triggers and (for D-2) a ready implementation sketch, so a future reversal is cheap to scope.

### Negative ⚠️

- ⚠️ If a tamper-evidence mandate later appears, historical entries predate the chain — the chain can only be anchored from its activation point forward (mitigated: the
  appendix sketch anchors at activation).
- ⚠️ Counterparty-specific posting, if ever required, must fit the `dimensions` mechanism or force a larger design effort then (accepted: no current requirement justifies
  pre-building).

### Neutral

- Audit posture is unchanged: `AccountingAuditLog` and event lineage remain the evidence trail for operational disputes; they provide accountability, not cryptographic
  tamper-evidence.

---

## Appendix — D-2 "if ever built" sketch

Recorded so a future compliance-triggered implementation starts from an agreed shape, not a blank page. Non-normative until the revisit trigger fires.

- **Chain:** `hash = SHA-256(prev_hash ‖ entry_number ‖ canonical JSON of the posted payload)`, computed at POST time inside the posting transaction (after gapless number
  assignment per plan story A2), stored as a column on `journal_entry`. `prev_hash` is the hash of the previously posted entry in the same sequence scope; the first chained
  entry anchors on a fixed genesis value.
- **Verification:** a scheduled integrity job re-walks the chain and alerts on any break (mismatched hash, missing link, out-of-sequence entry).
- **Activation:** a one-way per-deployment "restrict mode" flag — once enabled it cannot be disabled; entries posted before activation remain unchained.

---

## References

- **Related Issues:** durion#357 — parity-A4: record inalterability and fiscal-position non-goals (transferred from durion-positivity-backend#938)
- **Related Plans:** Accounting parity plan documents — story A4 (§2), decisions D-2/D-8 (§12), gap register rows G4/G15 (§1), plus the companion tax-parity plan
  (jurisdiction-first tax delegation context)
- **Related ADRs:** [ADR-0044: Event-Only Domain Walls](0044-platform-event-only-domain-walls.adr.md) — outbox/idempotency rails cited as the event-lineage evidence trail

---

## Timeline

- **Proposed**: 2026-07-17 (plan decisions D-2/D-8, resolved 2026-07-17)
- **Accepted**: 2026-07-17

---

## Changelog

- **2026-07-17**: Initial record of D-2 and D-8 as accounting non-goals (durion#357)
