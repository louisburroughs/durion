# ADR-0037: Frontend SPA Navigation Policy

**Status:** ACCEPTED
**Date:** 2026-04-02
**Deciders:** Frontend Architecture Team
**Affected Issues:** PR #15 review findings — threads r3027589814, r3027589844, r3027589859, r3027589883 (4 instances of bare `href` for in-app navigation); CAP-216/218/219/220/221/315

---

## Context

During PR review of Wave I-b (PR #15), four newly added components used bare HTML `href` attributes to navigate between Angular routes:

- `cycle-count-plan-form-page.component.html` — Cancel button `href="/app/inventory/counts/plans"`
- `return-to-stock-page.component.html` — Cancel button `href="/app/inventory/fulfillment"`
- `shortage-resolution-page.component.html` — Cancel button `href="/app/inventory/fulfillment"`
- `pick-list-page.component.html` — "Retry" `<a href="/app/inventory/fulfillment">`

Using bare `href` for in-app navigation in an Angular SPA causes:

- **Full browser reload** — Angular's bootstrap process, module lazy-loading, and application state are destroyed and reconstructed from scratch.
- **History stack pollution** — The Angular router's internal history and back-button state are bypassed, breaking navigation UX expectations.
- **Unsaved form state wiped** — Any signal state (form fields, selections, scroll position) is lost without warning.
- **Performance degradation** — Network round-trips and hydration are incurred unnecessarily on every such navigation event.
- **Accessibility regression** — Screen readers may re-announce the full page on a hard reload, disrupting the expected in-app flow.

No existing ADR codified this requirement, which led to the pattern being introduced by four separate components in a single PR.

---

## Decision

### 1. Prohibition on bare `href` for in-app routes

**Decision:** ✅ **Resolved** — Bare `href` attributes pointing to in-app Angular routes are **prohibited**. Every in-app navigation link must use Angular's router.

### 2. Required navigation patterns

**Decision:** ✅ **Resolved** — Use one of the following patterns for every in-app navigation:

**Pattern A — Declarative link (preferred for `<a>` elements):**
```html
<a class="btn-secondary" routerLink="/app/inventory/fulfillment">
  {{ 'COMMON.CANCEL' | translate }}
</a>
```
Requires `RouterLink` in the component's `imports` array.

**Pattern B — Programmatic navigation (required for `<button>` or conditional routing):**
```typescript
private readonly router = inject(Router);

cancel(): void {
  this.router.navigate(['/app/inventory/fulfillment']);
}
```
```html
<button type="button" (click)="cancel()">{{ 'COMMON.CANCEL' | translate }}</button>
```

### 3. Action semantics: `<button>` vs `<a routerLink>`

**Decision:** ✅ **Resolved** — Apply correct semantic element regardless of navigation method:

| Intent | Element |
|--------|---------|
| Navigate to a distinct URL (Cancel, Back, View) | `<a routerLink="...">` |
| Trigger an action (Retry/reload, Submit, Toggle) | `<button type="button">` |

An "Retry" or "Reload" control that calls a method is a **button** — it MUST NOT be an `<a>` element even with a `(click)` handler, because `<a>` without `href` is not keyboard-accessible.

### 4. External links exception

Bare `href` is permitted **only** for external URLs (links to external sites, file downloads, `mailto:`, `tel:`, etc.). External links should include `target="_blank" rel="noopener noreferrer"`.

---

## Alternatives Considered

1. **`[routerLink]="null"` to suppress navigation while keeping `<a>` semantics** — Rejected; works for disabling but not for correct action semantics. Still requires angular router binding.
2. **`location.href` assignment in TypeScript** — Rejected; has the same full-reload problem as bare `href`. Bypasses Angular router history.
3. **Allowing `href` with an `(click)="$event.preventDefault()"` and manual router navigation** — Rejected; verbose, error-prone, and violates the single-responsibility principle. Use `router.navigate()` directly.

---

## Consequences

### Positive ✅

- ✅ **No unexpected full reloads** — SPA state is preserved across in-app navigation.
- ✅ **Correct back-button behavior** — Angular router history is always current.
- ✅ **Accessibility compliance** — Keyboard navigation and screen-reader announcements reflect in-app route changes, not hard page replacements.
- ✅ **Enforced at review time** — Reviewers have a clear, citable standard to reference.

### Negative ⚠️

- ⚠️ **`RouterLink` must be in every component's `imports`** — Forgetting this import causes a silent non-navigation (no error, `href` is not applied). Mitigated by adding the check to the PR checklist.

### Neutral

- Angular's `RouterLink` directive behaves identically to `href` for external links when used with an absolute URL starting with `http`/`https`, but should not be used for external links — use bare `href` with `target="_blank"` for those.

---

## Compliance

### PR Checklist Addition

Add to `durion-positivity-frontend/AGENTS.md` PR checklist under Navigation:

- [ ] Every in-app navigation link uses `routerLink` (NOT `href`)
- [ ] Every in-app retry/reload/action control uses `<button>`, not `<a>`
- [ ] `RouterLink` is present in the component's `imports` array when `routerLink` is used

### Common Violations

| Violation | Correct Pattern |
|-----------|----------------|
| `<a href="/app/inventory/fulfillment">Cancel</a>` | `<a routerLink="/app/inventory/fulfillment">Cancel</a>` + `RouterLink` in imports |
| `<a href="/app/inventory/fulfillment" (click)="...">Retry</a>` | `<button type="button" (click)="reload()">Retry</button>` |
| `window.location.href = '/app/...'` | `this.router.navigate(['/app/...'])` |
