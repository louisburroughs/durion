# ADR-0038: Frontend Date-Only String Handling Policy

**Status:** ACCEPTED **Date:** 2026-04-02 **Deciders:** Frontend Architecture Team **Affected Issues:** PR #15 review findings — threads r3027589778 (UTC off-by-one in
`isScheduledDateValid()`), r3027589900 (Angular `date` pipe on YYYY-MM-DD string); CAP-219 cycle count plans

---

## Context

During PR review of Wave I-b (PR #15), two components mishandled date-only strings (ISO 8601 `YYYY-MM-DD` format) in ways that produce incorrect results for users in non-UTC
timezones.

**Instance 1 — Validation off-by-one (`r3027589778`)**

`cycle-count-plan-form-page.component.ts`:

```typescript
const selectedDate = new Date(dateInput); // YYYY-MM-DD → UTC midnight
selectedDate.setHours(0, 0, 0, 0); // normalises to LOCAL midnight
```

`new Date("2026-04-02")` produces `2026-04-02T00:00:00Z`. In a UTC-5 timezone that is `2026-04-01T19:00:00` local. Calling `.setHours(0,0,0,0)` then resets that to
`2026-04-01T00:00:00` local — one full calendar day back. A user in UTC-5 selecting "today" gets a date that compares as _yesterday_, causing `canSubmit()` to incorrectly
return `false`.

**Instance 2 — Display day-shift in `DatePipe` (`r3027589900`)**

`cycle-count-plan-list-page.component.html`:

```html
{{ plan.scheduledDate | date: 'mediumDate' }}
```

Angular's `DatePipe` internally calls `new Date(value)`. The same UTC-midnight parse occurs. A plan scheduled for April 2 is displayed as April 1 for all users in UTC-N
timezones, affecting every row of a data table.

### Root Cause

Per ECMAScript specification, `new Date("YYYY-MM-DD")` (ISO 8601 date-only format) is parsed as **UTC midnight**, not local midnight. This is a well-known gotcha and differs
from other date string formats. The correct way to represent "a date in the user's local timezone" is to use `new Date(year, monthIndex, day)`, which always constructs local
midnight.

No existing ADR addressed this requirement, leading to both instances being introduced simultaneously.

---

## Decision

### 1. Prohibition on `new Date(YYYY-MM-DD)` for local-date semantics

**Decision:** ✅ **Resolved** — `new Date(dateString)` where `dateString` is an ISO date-only value (`YYYY-MM-DD`) is **prohibited** in any context where local-timezone
calendar date semantics are required (validation, comparison, display).

### 2. Required: local-date constructor pattern

**Decision:** ✅ **Resolved** — When constructing a JavaScript `Date` from a `YYYY-MM-DD` string, always split the parts and use the three-argument local-date constructor:

```typescript
// CORRECT — local-date semantics
function parseDateLocal(yyyyMmDd: string): Date {
  const [y, m, d] = yyyyMmDd.split("-").map(Number);
  return new Date(y, m - 1, d); // local midnight
}
```

Never use:

```typescript
// FORBIDDEN — UTC semantics (wrong for local calendar comparisons)
const date = new Date(dateInput);
```

### 3. Required: DatePipe input for date-only strings

**Decision:** ✅ **Resolved** — Angular's `DatePipe` applied directly to a raw `YYYY-MM-DD` string will shift the displayed date in UTC-N timezones. Use one of these
approaches:

**Option A — Append local-time suffix (simplest in-template fix):**

```html
{{ (plan.scheduledDate + 'T00:00:00') | date: 'mediumDate' }}
```

Appending `T00:00:00` (no timezone designator) causes `new Date(...)` to parse as local time (behavior per ECMAScript spec: strings with ISO datetime but no `Z` or offset are
local).

**Option B — Pre-convert in component (preferred for complex cases):**

```typescript
readonly displayDate = computed(() => {
  const [y, m, d] = this.plan.scheduledDate.split('-').map(Number);
  return new Date(y, m - 1, d);
});
```

```html
{{ displayDate() | date: 'mediumDate' }}
```

**Option C — Render raw string (valid when locale-specific formatting is not required):**

```html
{{ plan.scheduledDate }}
```

Appropriate for admin/internal interfaces or where the raw ISO format is acceptable.

### 4. Date validation pattern for `<input type="date">` values

**Decision:** ✅ **Resolved** — When validating a date string from `<input type="date">` (which always produces `YYYY-MM-DD`), use local-date construction for boundary
comparisons:

```typescript
private isDateOnOrAfterToday(dateInput: string): boolean {
  if (!dateInput) return false;
  const parts = dateInput.split('-').map(Number);
  if (parts.length !== 3 || parts.some(Number.isNaN)) return false;

  const selectedDate = new Date(parts[0], parts[1] - 1, parts[2]);
  const today = new Date();
  today.setHours(0, 0, 0, 0);  // normalise today to local midnight

  return selectedDate >= today;
}
```

### 5. Exception: UTC/server contexts

When constructing dates **for server submission** (API request bodies, `requestedAt` fields), UTC semantics may be appropriate and `new Date(dateInput)` or `Date.UTC(...)` is
permitted. This ADR governs **display and local validation** only.

---

## Alternatives Considered

1. **Use `Date.UTC(y, m-1, d)` for comparison** — Rejected; produces a UTC timestamp. Comparing UTC timestamps against `new Date()` (local time) requires additional
   UTC-normalization and is error-prone.
2. **Use `toISOString().slice(0, 10)` for today's date** — Rejected; `toISOString()` returns UTC date, which has the same off-by-one problem in UTC-N timezones.
   `getFullYear()/getMonth()+1/getDate()` must be used instead.
3. **Adopt a date library (date-fns, Luxon)** — Deferred; would solve this cleanly but adds a dependency. This ADR establishes the minimal correct pattern using only the
   standard JS API. A future ADR may adopt a library if date handling complexity grows.
4. **Use Angular's `formatDate(value, format, locale)` directly** — Partially helpful but still calls `new Date()` internally for string inputs — same UTC-parse problem
   applies.

---

## Consequences

### Positive ✅

- ✅ **Correct calendar-date behavior for all timezones** — Users in UTC-N and UTC+N see and validate the correct local calendar date.
- ✅ **"Today" boundary validation works correctly** — Scheduling forms correctly accept today as a valid future date.
- ✅ **Data tables display the correct date** — No more off-by-one day in date columns for non-UTC users.
- ✅ **Citable standard** — Reviewers can reference this ADR when flagging `new Date(dateStr)` misuse.

### Negative ⚠️

- ⚠️ **Slightly more verbose** — `new Date(y, m-1, d)` is three lines instead of one. Mitigated by the low frequency of date parsing in components.
- ⚠️ **Template `+ 'T00:00:00'` is visually awkward** — Preferred approach (Option B, pre-convert in component) is better for readability but requires adding a computed
  signal. Either is acceptable.

---

## Compliance

### PR Checklist Addition

Add to `durion-positivity-frontend/AGENTS.md` PR checklist under Dates:

- [ ] No `new Date(YYYY-MM-DD)` for local-date semantics — use `new Date(y, m-1, d)` split
- [ ] Angular `DatePipe` is NOT applied directly to raw YYYY-MM-DD strings — append `T00:00:00` or pre-convert
- [ ] "Today" boundary tests use local-time date construction (`getFullYear()/getMonth()+1/getDate()`), NOT `toISOString()`

### Common Violations

| Violation                                                     | Correct Pattern                                                                   |
| ------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `new Date(dateInput)` where dateInput is YYYY-MM-DD           | `const [y,m,d] = dateInput.split('-').map(Number); new Date(y, m-1, d)`           |
| `{{ item.scheduledDate \| date: 'mediumDate' }}`              | `{{ (item.scheduledDate + 'T00:00:00') \| date: 'mediumDate' }}`                  |
| `const today = new Date().toISOString().slice(0,10)` in tests | `const d = new Date(); todayStr = \`${d.getFullYear()}-...\`` using local getters |
