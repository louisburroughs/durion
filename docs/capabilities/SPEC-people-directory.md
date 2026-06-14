# SPEC: People Directory Page

## 1. Summary

A searchable, filterable directory of all persons in the system. Accessible
from the People landing page as a direct-link card in a new "Directory" section.
Replaces the current UX pattern of launching profiles by typing a UUID.

---

## 2. Route & Navigation

| Item | Value |
|---|---|
| Route | `/app/people/directory` |
| Route param | none (filters are query params, shareable) |
| Parent | `/app/people` (PeopleComponent shell) |
| Entry point | People landing page — new "Directory" section card |

Query params (all optional, reflected in URL for shareability):
- `q` — free-text search string
- `type` — person type filter value (see §5)
- `page` — 0-based page index (default 0)

---

## 3. API Contract

### Primary data source

```
GET /v1/people
→ Array<Person>
```

`Person` shape (from `@durion-sdk/people`):
```typescript
interface Person {
  id?: string;
  firstName: string;
  lastName: string;
  primaryEmail?: string;
  secondaryEmail?: string;
  phoneNumbers?: Array<string>;
  username?: string;
}
```

**Note — no server-side filter params exist on `GET /v1/people` (v1).** All
filtering is client-side after a single load. If the person count grows beyond
~500 rows, a backend extension should add `?type=` and `?q=` query params.

### Open question 1 — Person type metadata

`Person` has no `type` field in the current SDK. The filter chips (§5) require
one of:
- (a) Backend adds `type` or `personType` field to `GET /v1/people` response
  (preferred — allows server-side filtering later).
- (b) Cross-reference with `GET /v1/people/employees/{id}` to determine
  EMPLOYEE vs non-employee status. Expensive at scale.
- (c) Treat `EmployeeProfileDtoStatusEnum` values as the filter set, fetching
  employee status per person lazily on filter activation.

**Resolution needed before implementation.** Until resolved, implement the page
without the type filter (show all, with search only), leaving a `filterType`
signal wired but no-op.

---

## 4. Component Structure

```
people/
  pages/
    directory/
      people-directory-page.component.ts
      people-directory-page.component.html
      people-directory-page.component.css
      people-directory-page.component.spec.ts
```

State: Angular signals, `ChangeDetectionStrategy.OnPush`.

Signals:
```typescript
readonly allPeople   = signal<Person[]>([]);
readonly state       = signal<'idle' | 'loading' | 'empty' | 'ready' | 'error'>('idle');
readonly searchQuery = signal('');
readonly activeType  = signal<PersonTypeFilter>('ALL');
readonly pageIndex   = signal(0);
readonly PAGE_SIZE   = 25; // constant

readonly filtered = computed<Person[]>(() => { /* see §6 */ });
readonly paged    = computed<Person[]>(() => { /* slice filtered */ });
readonly totalPages = computed<number>(() => Math.ceil(this.filtered().length / PAGE_SIZE));
```

---

## 5. Person Type Filter

Filter chips displayed above the table. Values:

| Chip label | Filter key | Logic |
|---|---|---|
| All | `ALL` | No filter — show everyone |
| Employees | `EMPLOYEE` | Persons with a known employee record (requires §3 OQ1) |
| Active | `ACTIVE` | Employees with status ACTIVE |
| Inactive | `INACTIVE` | Employees with status ON_LEAVE, SUSPENDED, TERMINATED, or DISABLED |

Until OQ1 is resolved, render chips but disable/hide all except **All**.

---

## 6. Client-Side Search & Filter

```
filtered = allPeople
  .filter(type filter — §5)
  .filter(q: case-insensitive match on firstName, lastName, primaryEmail, username)
```

Debounce the search input 300 ms before updating `searchQuery` signal.
Reset `pageIndex` to 0 on any filter/search change.

---

## 7. Table Layout

Columns (in order):

| Column | Field | Notes |
|---|---|---|
| Name | `${firstName} ${lastName}` | Link → `/app/people/employees/${id}` |
| Username | `username` | Monospace, em-dash if absent |
| Email | `primaryEmail` | Mailto link; em-dash if absent |
| Phone | `phoneNumbers[0]` | First number; em-dash if absent |
| Actions | — | "View Profile" button → employee profile route |

Sort: client-side, default lastName ASC. Clickable column headers toggle
ASC/DESC, aria-sort set on active header.

Empty state (filtered result = 0): "No people match your search." with a
"Clear search" link that resets `q` and `type`.

---

## 8. Pagination

Simple prev/next with page indicator: "Page X of Y". Show page size selector
(25 / 50 / 100) as a `<select>`.

---

## 9. Landing Page Integration

Add a new section to `LANDING_SECTIONS` in `people-landing-page.component.ts`:

```typescript
{
  titleKey: 'PEOPLE.LANDING.SECTION.DIRECTORY.TITLE',
  descriptionKey: 'PEOPLE.LANDING.SECTION.DIRECTORY.DESCRIPTION',
  cards: [
    {
      kind: 'direct',
      titleKey: 'PEOPLE.LANDING.CARD.DIRECTORY.TITLE',
      descriptionKey: 'PEOPLE.LANDING.CARD.DIRECTORY.DESCRIPTION',
      route: '/app/people/directory',
      actionKey: 'PEOPLE.LANDING.ACTION.OPEN_PAGE',
    },
  ],
},
```

---

## 10. Route Registration

Add to `PEOPLE_ROUTES` in `people.routes.ts` before the `**` wildcard:

```typescript
{
  path: 'directory',
  loadComponent: () =>
    import('./pages/directory/people-directory-page.component')
      .then(m => m.PeopleDirectoryPageComponent),
},
```

---

## 11. i18n Keys Required

Namespace: `PEOPLE.DIRECTORY.*`

| Key | en-US |
|---|---|
| `PEOPLE.DIRECTORY.TITLE` | People Directory |
| `PEOPLE.DIRECTORY.SEARCH_PLACEHOLDER` | Search by name, email, or username… |
| `PEOPLE.DIRECTORY.FILTER.ALL` | All |
| `PEOPLE.DIRECTORY.FILTER.EMPLOYEE` | Employees |
| `PEOPLE.DIRECTORY.FILTER.ACTIVE` | Active |
| `PEOPLE.DIRECTORY.FILTER.INACTIVE` | Inactive |
| `PEOPLE.DIRECTORY.COL.NAME` | Name |
| `PEOPLE.DIRECTORY.COL.USERNAME` | Username |
| `PEOPLE.DIRECTORY.COL.EMAIL` | Email |
| `PEOPLE.DIRECTORY.COL.PHONE` | Phone |
| `PEOPLE.DIRECTORY.COL.ACTIONS` | Actions |
| `PEOPLE.DIRECTORY.ACTION.VIEW_PROFILE` | View Profile |
| `PEOPLE.DIRECTORY.LOADING` | Loading people… |
| `PEOPLE.DIRECTORY.EMPTY` | No people match your search. |
| `PEOPLE.DIRECTORY.CLEAR_SEARCH` | Clear search |
| `PEOPLE.DIRECTORY.PAGINATION.PAGE_OF` | Page {{current}} of {{total}} |
| `PEOPLE.DIRECTORY.ERROR.LOAD` | Failed to load people. |
| `PEOPLE.LANDING.SECTION.DIRECTORY.TITLE` | Directory |
| `PEOPLE.LANDING.SECTION.DIRECTORY.DESCRIPTION` | Search and browse all people in the system. |
| `PEOPLE.LANDING.CARD.DIRECTORY.TITLE` | People Directory |
| `PEOPLE.LANDING.CARD.DIRECTORY.DESCRIPTION` | View and filter all persons by type and status. |

---

## 12. Error Handling

| Condition | Behaviour |
|---|---|
| `GET /v1/people` 4xx/5xx | Show error banner with `PEOPLE.DIRECTORY.ERROR.LOAD`; offer Retry button that re-calls the API |
| Empty result after load (no people) | Show empty state with `PEOPLE.DIRECTORY.EMPTY` |
| Empty filtered result | Show empty state with Clear search link |

---

## 13. Open Questions

| # | Question | Owner |
|---|---|---|
| OQ1 | Does backend expose `personType` on `GET /v1/people` or plan to? If not, what is the agreed cross-reference strategy? | Backend |
| OQ2 | Should "View Profile" link to the employee profile route only, or also support non-employee person detail? | Product |
| OQ3 | Is server-side pagination required from day 1, or is client-side acceptable for the current person count? | Backend |
