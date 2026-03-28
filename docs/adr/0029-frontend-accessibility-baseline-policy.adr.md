# ADR-0029: Frontend Accessibility Baseline Policy

**Status:** ACCEPTED  
**Date:** 2026-03-28  
**Deciders:** Frontend Architecture Team, UX Lead, QA Lead  
**Affected Issues:** Angular frontend accessibility consistency, keyboard/screen reader parity, compliance readiness

---

## Context

The Angular frontend has multiple implemented and in-flight feature domains with varying UI maturity. Without a single accessibility baseline, teams can ship inconsistent interaction patterns and regressions in keyboard/screen-reader behavior.

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

---

## References

- WCAG 2.2: <https://www.w3.org/TR/WCAG22/>
- WAI-ARIA Authoring Practices Guide: <https://www.w3.org/WAI/ARIA/apg/>
- MDN Accessibility: <https://developer.mozilla.org/en-US/docs/Web/Accessibility>
- Section 508: <https://www.section508.gov/>
- EN 301 549: <https://www.etsi.org/deliver/etsi_en/301500_301599/301549/>

