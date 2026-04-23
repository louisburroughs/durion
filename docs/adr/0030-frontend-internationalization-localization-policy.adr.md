# ADR-0030: Frontend Internationalization and Localization Policy

**Status:** ACCEPTED **Date:** 2026-03-28 **Deciders:** Frontend Architecture Team, Product Lead, UX Content Lead **Affected Issues:** Multi-locale readiness, translation
consistency, locale-safe formatting

---

## Context

The Angular frontend currently has a baseline language configuration, but long-term product execution requires predictable internationalization (i18n) and localization (l10n)
behavior across features.

Without a policy, teams risk hard-coded strings, incorrect pluralization, locale-unsafe formatting, and inconsistent fallback behavior.

---

## Decision

### 1. String Externalization and Key Ownership

**Decision:** ✅ **Resolved** - All user-facing text must be externalized to translation resources; no hard-coded UI copy in feature components.

Rules:

- Use stable namespaced keys by feature/page/context.
- Keep source language entries complete before merge.
- Treat translation keys as contract artifacts (breaking changes require coordination).

### 2. Locale Identification and Negotiation

**Decision:** ✅ **Resolved** - Locale identifiers must follow BCP 47 (for example `en-US`, `es-419`), with deterministic fallback.

Fallback order:

1. User-selected locale (if set)
2. Browser/app preferred locale
3. Default locale (`en-US`)

### 3. Message Formatting Standard

**Decision:** ✅ **Resolved** - Messages must support ICU-style plural/select patterns for grammar-safe localization.

Requirements:

- No string concatenation for sentences.
- Use message parameters/placeholders for dynamic values.
- Use plural rules through ICU/messageformat-compatible translation patterns.

### 4. Date, Time, Number, and Currency Formatting

**Decision:** ✅ **Resolved** - All locale-sensitive formatting must use Intl/CLDR-backed formatting, never manual formatting.

Requirements:

- Dates/times displayed in user locale and applicable timezone context.
- Numbers/currency use locale-aware separators and currency display.
- Stored/transmitted timestamps remain canonical (UTC/ISO contract standards).

### 5. BiDi and Script Support

**Decision:** ✅ **Resolved** - UI must support left-to-right and right-to-left rendering where locale requires it.

Requirements:

- Direction (`dir`) must be locale-driven.
- Layout/components must avoid hard-coded directional assumptions.
- Icons and affordances that imply direction must support mirroring where appropriate.

### 6. Translation Quality and Release Gates

**Decision:** ✅ **Resolved** - Localization quality checks are mandatory for shipping localized experiences.

Minimum gates:

- Missing-key detection (no raw key leakage in production views)
- Pseudo-localization checks for overflow/truncation/layout resilience
- Locale regression checks on critical routes
- Reviewer sign-off for translation quality on user-critical flows

### 7. Definition of Done (i18n/l10n)

**Decision:** ✅ **Resolved** - A story touching user-visible text or formatting is not done until i18n/l10n criteria are met.

Required for completion:

- New/changed copy externalized
- Key namespace and fallback behavior validated
- Locale-sensitive formats verified
- No missing translations for target release locales

---

## Consequences

### Positive ✅

- Predictable multilingual behavior across features
- Reduced rewrite cost for expansion into additional locales
- Better UX quality for non-default locale users

### Negative ⚠️

- Added process overhead for key management and translation QA
- Additional testing matrix across locales and directions

### Neutral

- Translation and content operations become a first-class delivery dependency

---

## Implementation Notes

- Align Angular translation usage with the existing translation module setup in app configuration.
- Define translation file structure and key naming conventions at repository level.
- Add pseudolocale and missing-key checks to CI or pre-release validation.
- Track locale support status by feature in release notes/capability documentation.

---

## References

- W3C Internationalization (i18n): <https://www.w3.org/International/>
- BCP 47 language tags: <https://www.rfc-editor.org/rfc/bcp/bcp47.txt>
- Unicode CLDR: <https://cldr.unicode.org/>
- ICU Message Format: <https://unicode-org.github.io/icu/userguide/format_parse/messages/>
- ECMAScript Intl API: <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl>
- ngx-translate: <https://github.com/ngx-translate/core>
