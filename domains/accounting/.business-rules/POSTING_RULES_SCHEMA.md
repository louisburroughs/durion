# Posting Rules Definition Schema

Authoritative schema for the `rulesDefinition` JSON stored on a
`PostingRuleVersion` in `pos-accounting`. This document was commissioned by
questions doc #202 and extended by stories E1 (issue #945, proportional
split lines) and E2 (issue #946, condition predicate grammar). It is the
single reference for rule authors, the publish-time validator, and the
evaluator implementation (`PostingRuleEvaluatorImpl` /
`PostingRuleDefinitionValidator` / `PredicateParser` in `pos-accounting`).

Related references:

- `ERROR_CODES.md` — `UNBALANCED_RULES` (422) and the other posting-rule
  error codes
- `DIMENSION_SCHEMA.md` — dimension keys usable in GL mapping resolution
- `BACKEND_CONTRACT_GUIDE.md` — posting-rule REST endpoints and lifecycle
- Plan `plan-odoo-parity-pos-accounting.md` §6 — workstream E (E1 split
  lines, E2 richer predicates, E3 dry-run endpoint)

---

## 1. Lifecycle context

A `PostingRuleSet` (named, bound to one `eventType`) owns immutable,
numbered `PostingRuleVersion`s. Each version carries one `rulesDefinition`
JSON document and moves `DRAFT → PUBLISHED → ARCHIVED`; at most one version
per rule set is `PUBLISHED` at a time. The posting engine only ever
evaluates `PUBLISHED` versions.

Validation points:

| Stage | What is checked |
| --- | --- |
| create / update draft | none beyond request-shape validation — drafts may hold work in progress |
| **publish** | definition non-empty; definition parses as JSON (`400 VALIDATION_ERROR` otherwise); **all split-group invariants of §4 and all condition-predicate grammar rules of §2.1 (`422 UNBALANCED_RULES` otherwise, every violation listed)** |
| evaluation (runtime) | condition matching, GL account resolution, journal-entry balance; split-group invariants re-checked defensively (violation ⇒ explicit `INTERNAL_ERROR` posting failure, never a silently adjusted amount); unparseable condition text (pre-E2 data only) ⇒ WARN + non-match |

---

## 2. Top-level structure

```json
{
  "conditions": [
    {
      "condition": "eventType == 'billing.invoicePosted'",
      "lines": [ { …line… }, { …line… } ]
    }
  ]
}
```

| Field | Type | Required | Semantics |
| --- | --- | --- | --- |
| `conditions` | array of condition blocks | yes (non-empty for a usable rule set) | Evaluated **in order; first matching condition wins**. Later conditions are ignored once one matches. |
| `conditions[].condition` | string | no | Match expression (see §2.1). Absent/blank ⇒ always matches (catch-all). |
| `conditions[].lines` | array of line objects | yes (non-empty) | Journal-entry line templates emitted when the condition matches. A matched condition with no lines is skipped with a warning. |

A definition that is empty, `{}`, or has no `conditions` array cannot
produce a journal entry; evaluation fails with `UNMAPPED_EVENT_TYPE` (the
publish gate only rejects the fully-empty definition, for compatibility
with existing stub rule sets).

### 2.1 Condition expressions (predicate grammar, E2 — issue #946)

| Expression | Behavior |
| --- | --- |
| absent / blank / `"*"` | always matches (default / catch-all rule) |
| any predicate of the grammar below | matches when **every** clause matches |
| anything else | rejected at **publish** (`422 UNBALANCED_RULES`, locator `conditions[i].condition`); if such text somehow reaches a published version (pre-E2 data), it **does not match** at evaluation (logged WARN and skipped — fail-safe) |

#### Grammar (EBNF — strict whitelist, no expression engine)

```ebnf
predicate  := clause ( '&&' clause )* ;
clause     := lhs op literal ;
lhs        := 'eventType' | 'payload' '.' path ;
path       := identifier ( '.' identifier )* ;
identifier := [A-Za-z_][A-Za-z0-9_]* ;
op         := '==' | '!=' | '>' | '>=' | '<' | '<=' ;
literal    := "'" characters-except-quote "'" | number ;
number     := ['+'|'-'] digits ['.' digits] ;
```

Whitespace between tokens is optional. There are no parentheses, no `||`,
no arithmetic, no functions, no escape sequences inside string literals,
and no other identifiers — the grammar is deliberately declarative
(Odoo's sandboxed-formula addon is the cautionary reference). Parsing is
strict: trailing garbage, unbalanced quotes, unknown identifiers,
malformed numbers, and empty clauses are all parse errors reported with
character position.

#### Operator / literal type rules

| Operator | String literal (`'…'`) | Numeric literal |
| --- | --- | --- |
| `==` `!=` | allowed — exact, case-sensitive string comparison | allowed — numeric comparison |
| `>` `>=` `<` `<=` | **rejected at parse/publish** ("ordering operator requires a numeric literal") | allowed |

Numeric comparison is `BigDecimal`-based and scale-insensitive
(`100 == 100.00`). The left-hand value is coerced: payload `Number`s and
numeric strings (e.g. `"150.25"`) both coerce; anything else is
non-coercible.

#### Evaluation semantics (safe defaults — a clause is false, never an error)

- Clauses are AND-combined with **short-circuit**: the first false clause
  stops evaluation of the predicate.
- `eventType` resolves to the event's type; `payload.<path>` navigates the
  payload map segment by segment.
- **Missing path, `null` value, or a non-map value mid-path ⇒ clause
  false** — not an error. This applies to `!=` too: `payload.x != 'A'`
  does *not* match an event without `payload.x`.
- Non-scalar terminal values (maps, lists) ⇒ clause false.
- String comparison requires the resolved value to be a string; numeric
  comparison requires it to coerce to a number. A type mismatch or
  non-coercible value ⇒ clause false (again, `!=` included).
- Evaluation of a published predicate never throws for any event shape.

#### Examples

```text
payload.paymentMethod == 'CASH'
payload.amount >= 100.00 && eventType == 'PAYMENT_RECEIVED'
payload.totals.tax > 0 && payload.paymentMethod != 'GIFT_CARD'
```

The first two are the canonical E2 acceptance predicates: a CASH payment
routes to a different account than CARD, and a conjunction can gate on a
numeric threshold plus the event type.

#### Validation split

| Stage | Behavior |
| --- | --- |
| **publish** | every non-catch-all `conditions[].condition` must parse under this grammar (including the ordering-op/numeric-literal rule) or the publish fails `422 UNBALANCED_RULES` with one `fieldErrors` entry per bad condition, locator `conditions[i].condition`. A non-string `condition` node is likewise rejected. |
| evaluation | catch-all forms match; parsed predicates evaluate with the safe defaults above; unparseable text (possible only in versions published before E2) is a WARN + non-match, exactly the pre-E2 fail-safe |

#### Backward compatibility

The E2 grammar is a strict superset of the pre-E2 condition language:
`eventType == '<value>'` parses to the same equality match, and the
catch-all forms (absent / blank / `"*"`) are unchanged. First-match-wins
ordering over the `conditions` array (§2) is untouched. Existing
published rule sets — including the E1 split rules — evaluate
byte-for-byte identically.

---

## 3. Line objects

```json
{
  "side": "DEBIT",
  "amountField": "payload.amount",
  "description": "Accounts Receivable",

  "postingCategoryId": "0196…uuid",
  "mappingKeyId": "0196…uuid",

  "glAccountId": "0196…uuid",

  "factorPercent": 60,
  "splitGroup": "revenue"
}
```

| Field | Type | Required | Semantics |
| --- | --- | --- | --- |
| `side` | `"DEBIT"` \| `"CREDIT"` (case-insensitive) | no — defaults to `DEBIT` | Which side of the journal entry this line posts to. |
| `amountField` | string dot-path | effectively yes | Path into the event resolved per §3.1. Missing/unresolvable ⇒ amount `0`. **Mandatory (non-blank) on split-group lines.** |
| `description` | string | no | Copied onto the generated journal-entry line. |
| `postingCategoryId` + `mappingKeyId` | UUIDs | one of the two account forms | Preferred account form: resolved through the GL mapping tables (exact match → fallback → category default), with the event's `payload.dimensions` map as resolution context. Both must be present together. |
| `glAccountId` | UUID | one of the two account forms | Direct GL account reference for simple rules without mapping tables. Used only when the category+key pair is absent. |
| `factorPercent` | decimal, `0 ≤ f ≤ 100`, **max 4 decimal places** | only on split lines | This line's share of the split group's amount, in percent (E1, §4). |
| `splitGroup` | non-blank string | only on split lines | Marker grouping this line with the other lines of the same split group **within the same condition** (E1, §4). |

If any line's GL account cannot be resolved, the whole condition is treated
as unresolved (no partial entries) and evaluation continues/fails exactly as
before E1.

### 3.1 Amount resolution

`amountField` is a dot-path into the event. A leading `payload.` prefix is
stripped and the remainder navigates the event's payload map, so
`payload.amount`, `amount`, and `payload.totals.tax` are all valid forms.
The resolved node may be a JSON number or a numeric string; both parse to an
exact `BigDecimal`. Unresolvable paths and non-numeric values resolve to
`0`.

**No rounding is applied to non-split lines** — the resolved value passes
through unchanged (journal-entry columns store up to 4 decimal places).
Rounding only happens inside split groups (§4.2).

### 3.2 Balance requirement

The generated journal entry must balance: total debits = total credits
within a `0.0001` tolerance. An unbalanced result fails evaluation with
`UNBALANCED_JOURNAL`. The rules author is responsible for making sides
balance (e.g. one credit line carrying the same `amountField` that a debit
split group decomposes).

---

## 4. Proportional split groups (E1, issue #945)

Odoo reference: repartition lines / `_distribute_delta_amount_smoothly`.
Split groups decompose **one** resolved amount across several lines of the
same journal-entry side, with a deterministic guarantee that the pieces sum
**exactly** to the source amount.

### 4.1 Invariants (enforced at publish, re-checked at evaluation)

Within one condition, all lines carrying the same `splitGroup` value form a
split group. Each group must satisfy:

1. Every member declares `factorPercent` (decimal, `0–100`, at most 4
   decimal places) — a member without a factor is invalid.
2. A line with `factorPercent` but no `splitGroup` (orphan factor) is
   invalid.
3. `splitGroup` must be a non-blank string.
4. Every member declares the **same, non-blank** `amountField`.
5. Every member has the **same `side`** — mixed DEBIT/CREDIT groups are
   rejected. (Decision: a split group models one single-sided economic
   amount; mixing sides would make both the sum-to-source guarantee and the
   residual placement ambiguous. Balance the entry with separate lines or a
   second split group on the other side.)
6. The members' `factorPercent` values sum to **exactly 100** (exact
   decimal equality — no tolerance). A single-line group is legal only with
   `factorPercent = 100`.

Split-group names are scoped to their condition: the same `splitGroup`
value in two different conditions denotes two independent groups. Multiple
independent groups may coexist in one condition, and split lines may be
freely interleaved with plain lines (which behave exactly as in §3).

Publishing a definition that violates any invariant fails with **HTTP 422,
error code `UNBALANCED_RULES`**; the response lists *every* violation as a
`fieldErrors` entry whose `field` locates the offending line or group, e.g.
`conditions[0].lines[2].factorPercent` or `conditions[0].splitGroup[tax]`.

### 4.2 Distribution algorithm (deterministic)

For each split group, in first-appearance order:

1. Resolve the group's shared `amountField` **once** → `A`.
2. Raw share per member `i`: `rawᵢ = A × factorᵢ / 100`, computed exactly
   (the ÷100 is a decimal-point shift — no division rounding).
3. Rounded share: `roundedᵢ = round(rawᵢ, 2 dp, HALF_UP)` (currency scale).
4. Residual: `r = A − Σ roundedᵢ`. `r` may be positive or negative and can
   exceed one cent for adversarial factor sets.
5. The **entire** residual is added to the member with the **largest
   absolute raw share**; on a tie, the **first such line in rule order**
   receives it (deterministic tie-break).

Result: `Σ` of the group's final line amounts `= A` exactly, always. If
`A` itself carries more than 2 decimal places, the sub-cent part travels
with the residual onto the largest line (journal-entry columns hold 4 dp),
preserving the exact-sum guarantee.

### 4.3 Worked examples

**60 / 40 over 100.00**

| Line | factor | raw | rounded | residual | final |
| --- | --- | --- | --- | --- | --- |
| 1 | 60 | 60.00 | 60.00 | — | **60.00** |
| 2 | 40 | 40.00 | 40.00 | — | **40.00** |

Σ rounded = 100.00 = A ⇒ residual 0. Final 60.00 + 40.00 = 100.00.

**33.33 / 33.33 / 33.34 over 100.00**

| Line | factor | raw | rounded | final |
| --- | --- | --- | --- | --- |
| 1 | 33.33 | 33.33 | 33.33 | **33.33** |
| 2 | 33.33 | 33.33 | 33.33 | **33.33** |
| 3 | 33.34 | 33.34 | 33.34 | **33.34** |

Exact raws ⇒ residual 0. Σ = 100.00.

**33.33 / 33.33 / 33.34 over 0.01**

| Line | factor | raw | rounded | final |
| --- | --- | --- | --- | --- |
| 1 | 33.33 | 0.003333 | 0.00 | 0.00 |
| 2 | 33.33 | 0.003333 | 0.00 | 0.00 |
| 3 | 33.34 | 0.003334 | 0.00 | **0.01** |

Σ rounded = 0.00; residual = +0.01 → line 3 (largest raw share).
Σ final = 0.01 = A.

**33.33 / 33.33 / 33.34 over 99.99**

| Line | factor | raw | rounded | final |
| --- | --- | --- | --- | --- |
| 1 | 33.33 | 33.326667 | 33.33 | 33.33 |
| 2 | 33.33 | 33.326667 | 33.33 | 33.33 |
| 3 | 33.34 | 33.336666 | 33.34 | **33.33** |

Σ rounded = 100.00; residual = −0.01 → line 3 (largest raw share):
33.34 − 0.01 = 33.33. Σ final = 99.99 = A.

**Tie-break: 50 / 50 over 0.01**

Raws 0.005 / 0.005 both round HALF_UP to 0.01 (Σ = 0.02); residual = −0.01.
Raw shares tie, so the **first** line takes the residual: final
**0.00 / 0.01**, Σ = 0.01 = A. This ordering is part of the contract —
identical inputs always produce identical line amounts.

### 4.4 Full example rule

```json
{
  "conditions": [
    {
      "condition": "eventType == 'workexec.laborCharged'",
      "lines": [
        {
          "side": "DEBIT",
          "amountField": "payload.amount",
          "splitGroup": "labor",
          "factorPercent": 60,
          "glAccountId": "01960003-0000-7000-8000-00000000c0a1",
          "description": "Labor cost — shop floor"
        },
        {
          "side": "DEBIT",
          "amountField": "payload.amount",
          "splitGroup": "labor",
          "factorPercent": 40,
          "glAccountId": "01960003-0000-7000-8000-00000000c0a2",
          "description": "Labor cost — overhead pool"
        },
        {
          "side": "CREDIT",
          "amountField": "payload.amount",
          "glAccountId": "01960003-0000-7000-8000-00000000c0a3",
          "description": "Accrued labor"
        }
      ]
    }
  ]
}
```

---

## 5. Versioning and backward compatibility

- The E1 additions are **strictly additive**: definitions without
  `factorPercent`/`splitGroup` are byte-for-byte unaffected — same
  matching, same amount passthrough (no rounding), same account
  resolution, same generated entries.
- Published versions are immutable; changing rules means creating a new
  `DRAFT` version and publishing it (which archives the previous published
  version). Split-group validation therefore only ever gates *new*
  publishes.
- Versions published **before** E1 cannot contain split fields, so no
  migration is needed. Should invalid split data ever reach a published
  version by other means, the evaluator fails that event explicitly
  (`INTERNAL_ERROR`) rather than distorting amounts.
- The E2 additions (issue #946) are likewise strictly additive: the
  predicate grammar of §2.1 is a superset of the pre-E2 condition
  language, and its validation only gates new publishes. Unparseable
  condition text in versions published before E2 keeps its historical
  WARN + non-match behavior.
- Future schema extensions land as new numbered sections here and must
  keep this additive, first-match-wins, exact-sum contract intact.
