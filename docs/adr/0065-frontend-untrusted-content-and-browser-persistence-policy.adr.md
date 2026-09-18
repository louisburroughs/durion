---
type: ADR
title: 'ADR-0065: Frontend Untrusted Content and Browser Persistence Policy'
description: PR review on the assistant chat surface found unsafe rendering of model-generated content and tenant-unsafe browser storage; this ADR fixes the required patterns.
status: stable
adr_status: accepted
created: '2026-09-18'
related: [ADR-0037, ADR-0062]
tags: [adr, frontend, security]
---
# ADR-0065: Frontend Untrusted Content and Browser Persistence Policy

**Status:** ACCEPTED **Date:** 2026-09-18 **Deciders:** Frontend Architecture Team, Security Domain **Affected Issues:** PR review findings on
[PR #288](https://github.com/louisburroughs/durion-positivity-frontend/pull/288)

---

## Context

PR #288 added an assistant chat surface that renders model/MCP-server-generated content (markdown, links, images, downloadable tables) and persists conversation
history in the browser. Review found six defects, all from treating model output as trusted and browser storage as already tenant-scoped:

1. **CSV formula injection** — a table built from assistant output was RFC 4180-quoted, but quoting alone does not stop Excel/Sheets from executing a cell that begins
   with `=`, `+`, `-`, `@`, a tab, or a carriage return as a formula (e.g. `=HYPERLINK(...)`).
2. **Protocol-relative URLs classified as internal** — a scheme allowlist (`http`, `https`, `mailto`) let `//evil.example/...` through because it carries no scheme, so
   the renderer treated it as an in-app, same-window navigation target — off-site, from text the model produced.
3. **Control-character smuggling** — `java\tscript:alert(1)` passed a scheme check run on the raw string; browsers strip control characters/whitespace before
   resolving a URL, so the browser saw plain `javascript:`.
4. **Structured blocks bypassing the URL check** — `chat-response.mapper.ts` bound any non-empty `url` on an `image`/`file` block straight to `[src]`/`[href]`, so only
   markdown links went through the scheme/origin check; a structured block from the same untrusted payload did not.
5. **Cross-tenant history leak** — chat history in `localStorage` was keyed by `sub` alone. A subject switching tenants could read the previous tenant's stored
   conversations (customer names, invoice totals). The in-memory reset guard had the same gap, tracking only `sub`.
6. **Corrupt storage as a hard failure** — an entry with `messages: [null]`, an unparseable date, or a non-string preview threw on read instead of reading as absent
   history; there was no write-side size budget, so an oversized snapshot could fail a quota write silently.

No existing ADR set a policy for rendering untrusted content or scoping browser-persisted data to a tenant+subject. ADR-0037 covers routing/navigation and ADR-0062
fixes `tid` as the sole tenant-identity source for API calls; neither addresses client storage keys or content rendering.

---

## Decision

### 1. Untrusted content never becomes an HTML string

**Decision:** ✅ **Resolved** — Text from a model, an MCP server, or another user is parsed into a typed token tree (`parseMarkdown`/`parseInline` in
`markdown.util.ts`; block coercion `coerceBlocks`/`coerceBlock` in `chat-response.mapper.ts`) and rendered by walking that tree with ordinary Angular templates.
`[innerHTML]` and `DomSanitizer.bypassSecurityTrust*` are **prohibited** for this content — no HTML string is ever constructed, so there is no sanitiser
configuration to keep correct.

### 2. URL normalise-then-validate, one function for every URL-bearing block

**Decision:** ✅ **Resolved** — Every URL reaching an anchor, `[src]`, or `[href]` — markdown link/image (`buildLink`/`buildImage`) or structured
`image`/`file`/`chart` block (`safeBlockUrl`) — goes through the same pair in `markdown.util.ts`:

- `normaliseHref(href)` strips ASCII control characters and whitespace before any scheme check, matching what the browser does before resolving the URL; checking
  the raw string lets a control character hide a scheme (`java\tscript:`).
- `isSafeHref(href)` runs on the normalised value: rejects network-path references (`//`, `\\`), then allowlists `http:`, `https:`, `mailto:`, and any scheme-free
  value (an app-relative path).

The rendered value is always `normaliseHref`'s output, never the raw source. A target that fails the check keeps its label/alt text and drops the anchor — it
degrades, it never throws. In-app targets use `routerLink` (ADR-0037); external targets get `target="_blank" rel="noopener noreferrer"`. A new structured block gains
a URL field only by calling this same pair (`safeBlockUrl`'s shape: normalise, then validate, else `null`) — never a bespoke check.

### 3. Untrusted values that become downloads must be neutralised, not just escaped

**Decision:** ✅ **Resolved** — A CSV cell built from assistant output (`csvCell` in `chat-message.component.ts`) that begins with `=`, `+`, `-`, `@`, a tab, or a
carriage return is prefixed with an apostrophe **before** RFC 4180 quoting — quoting alone stops delimiter injection, not formula execution. This generalises: any
client-generated file whose content is not first-party must have this class of lead character neutralised at assembly time. An object URL created for a triggered
download is revoked with a deferred `setTimeout(() => URL.revokeObjectURL(url))`, never synchronously after `.click()` (Firefox/Safari have not yet read the blob).

### 4. Browser persistence of tenant data is keyed by `tid` AND `sub`

**Decision:** ✅ **Resolved** — Any localStorage/sessionStorage/IndexedDB key holding tenant data derives from both the `tid` and `sub` claims on the current token,
never `sub` alone (ADR-0062). Reference shape, `LocalChatHistoryStore.key()` in `chat-history.store.ts`:

```typescript
private key(): string | null {
  const tenant = this.auth.currentUserClaims()?.tid?.trim();
  const subject = this.auth.currentUserClaims()?.sub?.trim();
  if (!tenant || !subject) return null;
  return `${STORAGE_PREFIX}:${tenant}:${subject}`;
}
```

No tenant, no key, no read or write — never a shared fallback bucket. A non-tenant preference (theme, rail collapsed) may skip tenant keying; the test is whether the
value could mix another tenant's business data.

### 5. In-memory identity guards track `tid|sub` together, never `sub` alone

**Decision:** ✅ **Resolved** — A service caching tenant data in memory holds a tracked identity `identityOf(claims) = \`${tid}|${sub}\`` (`chat-state.service.ts`)
and resets that cache in an `effect()` when it changes. Tracking `sub` alone misses a tenant switch by the same subject; `tid` alone misses an account switch inside
one tenant. In-flight async work (a queued write, a pending selection load) is tagged with the identity that issued it and discarded, not applied, if the current
identity has since changed (`ChatTurnTarget.identity`).

### 6. Reads from browser storage never throw; writes enforce a size budget

**Decision:** ✅ **Resolved** — Every stored value is validated field by field on read (`isPersistedConversation`, `isPersistedMessage`, `isDateString`); an entry
failing validation is filtered out, never thrown on, and a `JSON.parse` failure or absent key resolves to empty. Writes cap conversation count and messages-per-
conversation (`MAX_CONVERSATIONS`, `MAX_MESSAGES_PER_CONVERSATION`) before serializing; a `setItem` quota failure is caught and treated as "did not persist" —
surfaced where the caller has a place to put it, never silently swallowed.

### 7. Required test coverage

**Decision:** ✅ **Resolved** — A component/service that renders untrusted content or persists tenant data ships: one spec per rejected URL class (`javascript:`,
`data:`, `//host`/`\\host`, control-character-smuggled scheme); one CSV formula-prefix spec covering each lead character; one tenant-switch isolation spec (write
under one `tid|sub`, switch identity, assert the previous tenant's data is unreachable); one corrupt-storage spec per malformed shape, asserting empty, not thrown.

---

## Alternatives Considered

1. **DOMPurify + `[innerHTML]`** — Rejected; the renderer still terminates on an HTML string, leaving sanitiser-configuration drift as an ongoing injection risk. A
   token tree has no HTML construction step to keep safe.
2. **Trust the backend/MCP response as first-party** — Rejected; the model's answer is influenced by ingested documents, so it is attacker-influenceable regardless
   of transport.
3. **Key browser storage by `sub` only** — Rejected; this is the exact cross-tenant leak this ADR fixes.
4. **Reject a whole stored conversation on any malformed field** — Rejected in favor of per-field validation; dropping the entry for one bad field loses otherwise
   good history unnecessarily.

---

## Consequences

### Positive ✅

- ✅ **No HTML injection surface for model output** — the token-tree renderer has nothing for an attacker string to break out of.
- ✅ **One audited choke point for every URL** — `normaliseHref`/`isSafeHref` is the single place a reviewer checks; a new block type is safe by construction.
- ✅ **No cross-tenant leak from browser storage** — `tid|sub` keying and reset guards make the leak class structurally unreachable, not patched once.
- ✅ **Storage failures degrade instead of crashing** — a corrupt or over-quota browser is a worse experience, never a thrown exception.

### Negative ⚠️

- ⚠️ **More validation code per block type** — each structured kind needs its own `coerceBlock` case and, if URL-bearing, a `safeBlockUrl` call; easy to forget on a
  new kind. Mitigated by the checklist below and review flagging any unrouted `[src]`/`[href]` binding.
- ⚠️ **Storage key change orphans existing local history** — moving from `sub`-only to `tid|sub` drops history under the old key. Accepted: pre-production policy
  bars migration shims, and the data is a local convenience cache, not source of truth.

---

## Compliance

### PR Checklist Addition

Add to `durion-positivity-frontend/AGENTS.md` PR checklist under Security:

- [ ] Model/server/user-generated text renders through a parsed token tree + Angular templates — no `[innerHTML]`, no `bypassSecurityTrust*`
- [ ] Every URL bound to an anchor/`[src]`/`[href]` — markdown or structured block — is normalised (`normaliseHref`) then checked (`isSafeHref`); rendered value is
      the normalised value
- [ ] A client-generated CSV/TSV cell from untrusted content is formula-lead-prefixed (`=+-@`, tab, CR) before RFC 4180 quoting
- [ ] An object URL from a triggered download is revoked with a deferred call (`setTimeout`), not synchronously after `.click()`
- [ ] A browser storage key for tenant data derives from both `tid` and `sub`, never `sub` alone (ADR-0062)
- [ ] An in-memory identity/reset guard compares `` `${tid}|${sub}` ``, never `sub` alone
- [ ] A stored value is validated field by field on read; a malformed entry is filtered out, never thrown
- [ ] A browser-storage write enforces a bounded size/count budget and a quota failure is caught, not silently lost

### Common Violations

| Violation | Correct Pattern |
| --------- | ---------------- |
| `[innerHTML]="renderMarkdown(text)"` on model output | Parse to a token tree (`parseMarkdown`) and render via Angular templates |
| `isSafeHref(rawHref)` checked before stripping control characters | `isSafeHref(normaliseHref(rawHref))` — normalise first, render the normalised value |
| Scheme allowlist with no `//`/`\\` rejection | Reject network-path references before the scheme allowlist |
| `[src]="block.url"` on a structured image/file block with no check | Route through the same normalise/validate pair as markdown links (`safeBlockUrl`) |
| `csvCell(value) => \`"${value.replace(/"/g,'""')}"\`` | Prefix a formula-lead cell with `'` before quoting (`FORMULA_LEAD_RE` check) |
| `URL.revokeObjectURL(url)` immediately after `anchor.click()` | `setTimeout(() => URL.revokeObjectURL(url))` |
| `localStorage` key derived from `claims.sub` only | Key derived from tenant AND subject together |
| Reset guard: `if (claims.sub !== trackedSub)` | `identityOf(claims) = \`${tid}\|${sub}\`` compared against the tracked identity |
| `JSON.parse(raw).map(toConversation)` with no per-item validation | Filter with a type guard (`isPersistedConversation`) before mapping |
| Unbounded `localStorage.setItem(key, JSON.stringify(everything))` | Cap conversation/message counts before serializing; catch and surface a quota failure |

---

## Implementation Notes

Reference implementation: `durion-positivity-frontend` branch `claude/adoring-mccarthy-mriyc5` — `markdown.util.ts` (`normaliseHref`, `isSafeHref`, `buildLink`,
`buildImage`), `chat-response.mapper.ts` (`safeBlockUrl`, `coerceBlock`), `chat-message.component.ts` (`csvCell`, `downloadCsv`), `chat-history.store.ts`
(`LocalChatHistoryStore.key`, `isPersistedConversation`), `chat-state.service.ts` (`identityOf`, the reset `effect()`), all under
`src/app/features/shell/`. Do not re-derive an equivalent check locally — import or mirror these.

---

## References

- PR review findings: [PR #288](https://github.com/louisburroughs/durion-positivity-frontend/pull/288)
- ADR-0037 — frontend routing/navigation conventions; ADR-0062 — tenant identity sourced from `tid` only
- `durion-positivity-frontend` branch `claude/adoring-mccarthy-mriyc5` (unmerged exemplar implementation cited throughout)
