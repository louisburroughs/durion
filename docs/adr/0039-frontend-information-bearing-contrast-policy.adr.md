# ADR-0039: Frontend Information-Bearing Contrast Policy

**Status:** ACCEPTED  
**Date:** 2026-04-11  
**Deciders:** Frontend Architecture Team, UX Lead, Accessibility Lead, QA Lead  
**Affected Issues:** Frontend user experience accessibility consistency, text readability, ADA/WCAG conformance evidence

---

## Context

Frontend delivery needs an explicit, testable contrast policy for information-bearing UI elements to avoid inconsistent interpretations of accessibility expectations during implementation and review.

ADR-0029 established the frontend accessibility baseline at WCAG 2.2 AA, but contrast thresholds must be stated concretely in frontend implementation policy to remove ambiguity for design, engineering, and QA.

---

## Decision

### 1. Information-Bearing Contrast Thresholds

**Decision:** ✅ **Resolved** - All information-bearing text must meet WCAG 2.2 AA contrast thresholds in every supported theme/state.

Rules:

- Small text (less than `18pt`, or less than `14pt` when bold): minimum contrast ratio `4.5:1`.
- Large text (`18pt` and above, or `14pt` and above when bold): minimum contrast ratio `3:1`.
- Thresholds apply to normal, hover, focus, active, disabled, and validation/error states when those states still convey required information.

### 2. Scope of Information-Bearing Elements

**Decision:** ✅ **Resolved** - The policy applies to any text that conveys meaning, status, instructions, value, or action.

In scope:

- Body text, labels, helper text, validation and error text, table/grid text, badges, chips, button text, links, and navigation labels.
- Placeholder text if it conveys required information for task completion.
- Text inside charts/cards/widgets when it carries operational meaning.

Out of scope:

- Purely decorative text treatment that does not carry user-facing meaning.

### 3. UX and Delivery Enforcement

**Decision:** ✅ **Resolved** - Contrast compliance is required at design handoff, implementation, and PR review.

Requirements:

- Design specs must include foreground/background token pairs for information-bearing text.
- Frontend implementation must use approved tokens rather than ad-hoc color values for meaningful text.
- PR review and QA must include contrast verification evidence for newly added or changed information-bearing text.
- A story cannot be marked done if required information-bearing text violates the threshold for its size/weight class.

### 4. ADA/WCAG Alignment

**Decision:** ✅ **Resolved** - This policy is the frontend operationalization of ADA-accessibility obligations via WCAG criteria.

Authoritative criteria:

- WCAG 2.2 Success Criterion 1.4.3 (Contrast Minimum)
- WCAG 2.2 Success Criterion 1.4.6 (Contrast Enhanced) for optional stricter internal targets
- Related legal and policy references: ADA Title II/III interpretations, Section 508, EN 301 549

When standards evolve, this ADR remains binding until explicitly superseded.

---

## Consequences

### Positive ✅

- Removes ambiguity for contrast decisions in UX and engineering workflows.
- Improves readability and usability for low-vision users across core frontend flows.
- Provides clearer compliance evidence during review and audit.

### Negative ⚠️

- May require token updates or visual design adjustments in existing components.
- Adds explicit verification effort in design QA and PR review.

### Neutral

- Teams must keep contrast checks as a routine quality gate for changed UI.

---

## Implementation Notes

- Treat this ADR as additive to ADR-0029; it does not reduce any baseline accessibility requirement.
- Prefer centralized design tokens and reusable component-level fixes over one-off local overrides.
- Document contrast exceptions only when a standard exception process is defined and approved.

---

## References

- WCAG 2.2: <https://www.w3.org/TR/WCAG22/>
- WCAG 2.2 SC 1.4.3 Contrast (Minimum): <https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html>
- WCAG 2.2 SC 1.4.6 Contrast (Enhanced): <https://www.w3.org/WAI/WCAG22/Understanding/contrast-enhanced.html>
- ADA.gov Accessibility Guidance: <https://www.ada.gov/resources/web-guidance/>
- Section 508: <https://www.section508.gov/>
- EN 301 549: <https://www.etsi.org/deliver/etsi_en/301500_301599/301549/>
