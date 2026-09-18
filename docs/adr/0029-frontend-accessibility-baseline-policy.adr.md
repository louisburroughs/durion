---
type: ADR
title: 'ADR-0029: Frontend Accessibility Baseline Policy'
description: The Angular frontend has multiple implemented and in-flight feature domains with varying UI maturity.
status: stable
adr_status: accepted
created: '2026-03-28'
tags: [adr, accessibility, frontend]
---
# ADR-0029: Frontend Accessibility Baseline Policy

**Status:** ACCEPTED **Date:** 2026-03-28 **Deciders:** Frontend Architecture Team, UX Lead, QA Lead **Affected Issues:** Angular frontend accessibility consistency,
keyboard/screen reader parity, compliance readiness

---

## Context

The Angular frontend has multiple implemented and in-flight feature domains with varying UI maturity. Without a single accessibility baseline, teams can ship inconsistent
interaction patterns and regressions in keyboard/screen-reader behavior.

Accessibility requirements must be explicit and testable so that feature delivery remains inclusive while scaling execution across waves.

---

## Decision

### 1. Accessibility Compliance Target

**Decision:** ✅ **Resolved** - All user-facing Angular features must meet WCAG 2.2 Level AA as the frontend baseline.

Scope:

- New pages/components
- Modified pages/components (must not regress)
- Shared shell/navigation/components

### 2. Semantic HTML and ARIA Policy

**Decision:** ✅ **Resolved** - Use semantic HTML first; apply ARIA only when native semantics are insufficient.

Rules:

- Use native controls (`button`, `input`, `select`, `a`) for interactive behavior.
- Do not reimplement native controls with non-semantic elements.
- Follow WAI-ARIA Authoring Practices for advanced widgets (tabs, dialogs, comboboxes, grids).
- Keep accessible names deterministic via visible labels, `aria-label`, or `aria-labelledby`.

### 3. Keyboard and Focus Behavior

**Decision:** ✅ **Resolved** - Every interactive flow must be fully operable by keyboard alone.

Requirements:

- Logical tab order and visible focus indicator at all times.
- No keyboard traps.
- Dialogs/modals must trap focus while open and return focus to invoking control on close.
- Skip-to-content behavior in app shell for efficient keyboard navigation.

### 4. Forms, Validation, and Error Handling

**Decision:** ✅ **Resolved** - Form UX must expose validation and error states to both visual and assistive technology users.

Requirements:

- Programmatic label association for all inputs.
- Error messages bound via `aria-describedby` and announced when validation fails.
- Required/invalid states conveyed via both visual cues and accessibility semantics.
- Do not rely on color alone for error communication.

### 5. Visual, Motion, and Content Requirements

**Decision:** ✅ **Resolved** - Visual presentation must preserve readability and motion safety.

Requirements:

- Minimum contrast ratios per WCAG 2.2 AA.
- Respect `prefers-reduced-motion` and avoid non-essential motion.
- Images/icons that convey meaning must have equivalent text (`alt` or accessible label).
- Headings/landmarks must support predictable navigation structure.

### 6. Accessibility Quality Gates

**Decision:** ✅ **Resolved** - Accessibility checks are required in development and verification workflows.

Minimum gates:

- Automated checks (axe-based scans) on critical routes/components.
- Keyboard-only smoke path for each feature page.
- Screen-reader smoke checks on critical flows (NVDA or VoiceOver).
- Block release on unresolved critical accessibility defects.

### 7. Definition of Done (Accessibility)

**Decision:** ✅ **Resolved** - A story is not done unless accessibility acceptance criteria are satisfied for changed UI.

Required for completion:

- Keyboard flow verified
- Accessible names/roles/states verified
- Error announcements verified for changed forms
- No new critical accessibility violations

### 8. Repository Mechanics (Angular implementation rules)

**Decision:** ✅ **Resolved** - The following concrete rules apply to every Angular implementation in `durion-positivity-frontend`, each motivated by a specific PR review
finding (PRs #275, #282, #288, #289, Sept 2026):

1. Every modal, including one nested inside another modal, uses the repository's native `dialog[appModalDialog]` directive
   (`src/app/shared/modal-dialog.directive.ts`) — its `showModal()` call supplies top-layer promotion, backdrop, focus trap, and Escape-to-close via `(modalCancel)`.
   `aria-modal` on a `div` implements nothing; a nested dialog rendered outside the parent's trap lets Tab escape the parent and lets Escape close both dialogs at once.
2. `.sr-only` is the single global visually-hidden utility, defined once in `src/styles.css`; components do not redefine it locally and never apply the class without
   confirming it resolves against the global rule (labels rendered visibly in PR #288 because the rule was scoped to specific component selectors instead of global).
3. `aria-label` on an element replaces its descendant text in the accessible name computation: never put a visible value or an `sr-only` hint inside an `aria-label`led
   control expecting it to also be announced. Attach hints and caveats via `aria-describedby` pointing at unique ids, placed on the control itself, and — for disabled
   controls that cannot receive focus — on the enclosing group instead.
4. `title` is never the only channel for conveying information; it reaches neither keyboard-only nor touch users.
5. Interactive targets are at least 24×24 CSS px (WCAG 2.2 SC 2.5.8), enforced with `min-inline-size`/`min-block-size`; do not assume the spacing exception applies.
6. Label in Name (WCAG 2.2 SC 2.5.3): every ARIA accessible name for a control that shows visible text must start with that visible text, in every shipped locale and
   across named and unnamed variants alike — assert this against the real locale bundles under `src/assets/i18n/`, not hardcoded English strings.
7. Focus is moved deliberately whenever an action removes, replaces, or disables the focused control: a rename moves focus to the input, a confirm-delete moves focus to
   the confirmation, a card moved to another rail carries focus with it, and a control disabled while an action is pending must have its focus handled explicitly.
   Focus restoration is scheduled after the render that removes the control (driven from the readback, not from the write).
8. Live regions survive rebuilds: dynamic banners keep `aria-live="polite"`, refusals use `role="alert"`, and a pending/typing indicator carries real visually-hidden
   text rather than an `aria-label` on a `div`.
9. `aria-current` (`"true"` for an in-page view, `"page"` only for an actual route) marks the active item in a bar of buttons; `aria-selected`/`role="tab"` is used only
   inside a real tablist with arrow-key handling; no `aria-controls` may point at an element that is conditionally absent from the DOM.
10. A toggle's accessible name and `title` describe the action that will happen now (e.g., "Open" vs "Close"), not the current state.
11. Every interactive control keeps a visible `:focus-visible` indicator; setting `outline: none` requires a token-based replacement focus ring on the same element or a
    wrapping element.
12. Accessible names of controls inside a list must name their target (e.g., row number, mechanic name) — a parent's `aria-label` is not inherited by its descendants.
13. Every drag interaction has a pointer-free equivalent; drag handles are labelled as handles and point at the keyboard route for the same operation.

### 9. Automated Evidence for What Axe Cannot See

**Decision:** ✅ **Resolved** - Because implementing agents cannot run NVDA/VoiceOver, unit specs must carry the automated evidence a screen-reader smoke pass would
otherwise provide.

Required assertions in component/page specs:

- Focus movement (the specific element that receives focus after an action, not just "focus changed").
- `dialog.matches(':modal')` for every `appModalDialog` dialog, proving the native modal state was actually entered.
- Computed accessible names, resolved the way `aria-label`/`aria-describedby` compose them, not just presence of the attribute.
- Live-region presence (`aria-live`, `role="alert"`) on the elements that carry dynamic status.
- Focus-indicator rules (no bare `outline: none` without a replacement ring).

The PR template's manual smoke-test items (keyboard-only pass, screen-reader pass) stay unticked unless a human actually ran them — an agent completing automated
checks does not tick manual items on their behalf.

---

## Consequences

### Positive ✅

- Consistent inclusive UX across domains
- Lower remediation cost by shifting checks left
- Clear quality bar for PR review and QA sign-off

### Negative ⚠️

- Additional implementation and test effort per story
- Some refactors required for legacy/stubbed screens as they become active

### Neutral

- Teams must maintain accessibility evidence in routine delivery artifacts

---

## Implementation Notes

- Add accessibility criteria to frontend story templates and review checklists.
- Standardize reusable accessible patterns in shared shell/components.
- Track accessibility defects with severity and SLA.
- Prefer fixing root reusable components over per-page workarounds.
- The repository mechanics in §8-9 are also listed with concrete file paths in `durion-positivity-frontend/AGENTS.md` and `durion-positivity-frontend/docs/EXEMPLARS.md`;
  those are the canonical place to look up the current exemplar implementation of each rule.

---

## References

- WCAG 2.2: <https://www.w3.org/TR/WCAG22/>
- WAI-ARIA Authoring Practices Guide: <https://www.w3.org/WAI/ARIA/apg/>
- MDN Accessibility: <https://developer.mozilla.org/en-US/docs/Web/Accessibility>
- Section 508: <https://www.section508.gov/>
- EN 301 549: <https://www.etsi.org/deliver/etsi_en/301500_301599/301549/>

---

## Changelog

- **2026-09-18**: Added §8 (Repository Mechanics) and §9 (Automated Evidence for What Axe Cannot See), codifying concrete Angular implementation rules drawn from
  `durion-positivity-frontend` PR review findings (PRs #275, #282, #288, #289, Sept 2026), including native `dialog[appModalDialog]` usage for nested modals, the
  single global `.sr-only` utility, `aria-label`/`aria-describedby` name composition, target size, Label in Name, deliberate focus management, live-region survival,
  `aria-current`/tab semantics, toggle naming, focus-visible indicators, list-item naming, and keyboard-equivalent drag interactions.
