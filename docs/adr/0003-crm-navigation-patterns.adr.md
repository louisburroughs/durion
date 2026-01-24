# ADR-CRM-001: CRM Navigation Patterns

**Status:** ACCEPTED - 2026-01-24 
**Date:** 2026-01-23  
**Deciders:** Frontend Lead, UX Designer, Product Manager  
**Affected Issues:** #176, #173, #172, #169

---

## Context

The CRM frontend requires consistent, predictable navigation patterns for entity views and post-creation flows. Currently, there are no canonical routes or screen layouts defined for:

- Party/Account detail views
- Contact management screens
- Vehicle management screens
- Post-create redirects
- Breadcrumb and back-button behavior

Without clear patterns, developers will create ad-hoc solutions, leading to inconsistency and poor user experience.

---

## Decision

Establish **canonical REST-style URL patterns** for all CRM entity views and define **screen layouts** with clear ownership and navigation rules.

### 1. Route Pattern

**Proposed:**
```
GET /crm/parties/{partyId}                    → Party detail view
GET /crm/parties/{partyId}/contacts           → Contacts tab (within PartyView)
GET /crm/parties/{partyId}/vehicles           → Vehicles tab (within PartyView)
GET /crm/vehicles/{vehicleId}                 → Vehicle detail (optional deep-link)
GET /crm/parties/{partyId}/communication      → Communication preferences (tab or modal)
```

**Alternative (legacy-friendly):**
```
GET /crm/accounts/{accountId}                 → (if "account" term preferred)
```

**Decision:** ✅ **Resolved** - Use `/crm/parties/{partyId}` format (proposed). Update legacy services to redirect from `/crm/accounts/` for backward compatibility.

### 2. Screen Layout: PartyView

**PartyView** is the canonical single-page view for a party. Layout:

```text
┌─────────────────────────────────────────────────┐
│ [Back] Party: Acme Corp                         │
├─────────────────────────────────────────────────┤
│ [Tabs] Details | Contacts | Vehicles | Comms    │
├─────────────────────────────────────────────────┤
│ Tab Contents:                                   │
│  • Details: Legal name, tax ID, type, status   │
│  • Contacts: Table of contacts + roles         │
│  • Vehicles: Table of vehicles + VINs          │
│  • Comms: Communication preferences            │
└─────────────────────────────────────────────────┘
```

**Contacts within PartyView:**

- Display as **table with inline edit** (click row → modal)
- Columns: Name, Email, Phone, Roles, Primary flags
- Action buttons per row: Edit, Delete
- Bulk actions: Assign role to multiple

**Decision:** ✅ **Resolved** - Display contacts as **table with inline edit** (click row → modal). Provides familiar spreadsheet UX for bulk operations.

### 3. Post-Create Navigation

**Proposed Flow:**

1. User completes "Create Party" form
2. Submits: `crm.createCommercialAccount(legalName, taxId, ...)`
3. Backend returns: `201 Created { partyId: "party-123", ... }`
4. Frontend redirects: `→ /crm/parties/party-123`
5. User sees: PartyView with success banner: "Account created successfully"
6. Banner includes CTA: "Add contacts" → scroll to Contacts tab

**Alternative (wizard-style):**
1. Show "Success" screen with summary
2. Offer: "Continue to full view" or "Add another party"
3. Requires backend to return more metadata

**Decision:** ✅ **Resolved** - Use proposed redirect flow: user completes form → backend returns `partyId` → frontend redirects to `/crm/parties/{partyId}` with success banner and CTA to add contacts.

### 4. Breadcrumb Strategy

**Proposed:**
```
Home > CRM > Parties (list) > Acme Corp (detail)
Home > CRM > Parties > Acme Corp > Contacts
Home > CRM > Vehicles > Tesla Model 3
```

**Rules:**
- Always clickable and return to parent
- Show entity name (not just ID)
- Don't exceed 4 levels

**Decision:** ✅ **Resolved** - Use proposed breadcrumb hierarchy with clickable links and entity names. Max 4 levels to avoid URL bloat.

### 5. Back Button Behavior

**Proposed:**
- "Back" button on PartyView → browser history `-1`
- "Back" on embedded modals (e.g., contact edit) → close modal, stay on PartyView
- "Back" on standalone pages (e.g., search results) → previous search state or parties list

**Alternative:**
- Always return to parent entity, ignore browser history

**Decision:** ✅ **Resolved** - Use browser history (`-1`) for PartyView back button. For embedded modals, close modal and stay on PartyView. For standalone pages, return to previous search state or list.

### 6. Unsaved Changes Protection

**Proposed:**
- Show warning: "You have unsaved changes. Leave anyway?" before navigation
- Only for forms/modals, not for read-only views
- Persist form state to session storage for recovery

**Decision:** ✅ **Resolved** - Show unsaved changes warning before navigation. Persist form state to session storage for recovery. Only apply to forms/modals, not read-only views.

---

## Alternatives Considered

### Alternative A: Flat Route Structure (No Nesting)
```
GET /crm/parties/{partyId}
GET /crm/contacts/{contactId}
GET /crm/vehicles/{vehicleId}
```
**Pros:** Simpler, parallel entities independent  
**Cons:** Loses hierarchical context (which party owns this contact?); poor UX for viewing related data

### Alternative B: Deep Nesting (Multiple Levels)  --> MY PREFERENCE
```
GET /crm/parties/{partyId}/contacts/{contactId}/communication-preferences
```
**Pros:** Explicit hierarchy  
**Cons:** URL bloat; harder to share links; contact shouldn't depend on party in URL structure

### Alternative C: Tabbed Modal (No Separate Screens)
```
/crm/parties → List view
→ Click party → Modal with all tabs (details, contacts, vehicles)
→ No separate URLs for tabs
```
**Pros:** Simpler routing  
**Cons:** Modal fatigue; can't bookmark/share contact view; poor mobile UX

### Alternative D: Legacy Account-Centric (Backward Compat)
```
/crm/accounts/{accountId} → Party view (keep "account" terminology)
/crm/customers/{customerId} → Legacy customer
```
**Pros:** Familiar to users trained on legacy system  
**Cons:** Dual terminology confusing; technical debt

---

## Consequences

### Positive
- ✅ Consistent, predictable navigation improves user confidence
- ✅ Deep-linkable URLs enable sharing and bookmarking
- ✅ Hierarchical structure makes relationship clear (contacts owned by party)
- ✅ Aligns with RESTful conventions and modern web UX
- ✅ Mobile-friendly tab-based layout scales well

### Negative
- ❌ Requires changes to router configuration in durion-moqui-frontend
- ❌ All existing integrations/links must be updated
- ❌ Legacy users may be confused by terminology shift (party vs. account)
- ❌ Breadcrumbs require loading entity names (extra API calls if not cached)

### Risks
- ⚠️ Deep nesting (contacts within party) could make URLs too long for edge cases
- ⚠️ If contact is shared across multiple parties (future feature), URL hierarchy breaks
- ⚠️ Post-create redirect fails silently if partyId not returned from backend

---

## Compliance

- **Durion Navigation Standards:** Follows RESTful routing conventions
- **Moqui Framework:** Compatible with Moqui's router, can use screen URLs or SPA routing
- **Vue 3 Router:** Fully supported via vue-router
- **Accessibility (WCAG 2.1):** Breadcrumbs and back button aid keyboard navigation

---

## Implementation Notes

### Moqui Artifacts to Create
- `runtime/component/durion-crm/screen/PartyDetail.xml` – Main party view with tabs
- `runtime/component/durion-crm/screen/ContactsTab.xml` – Contacts list/edit
- `runtime/component/durion-crm/screen/VehiclesTab.xml` – Vehicles list/edit

### Vue Components to Create
- `PartyDetail.vue` – Wrapper component with tabs
- `PartyDetailsTab.vue` – Details content
- `ContactsTab.vue` – Contacts table and edit modal
- `VehiclesTab.vue` – Vehicles table and edit modal
- `BreadcrumbNav.vue` – Shared breadcrumb component

### Router Configuration
- Update `durion-crm/router/index.ts` with new routes
- Add guards for unsaved changes protection
- Handle 404 (entity not found) gracefully

---

## Open Questions for Deciders

1. **Route naming:** Use `/crm/parties/{id}` or `/crm/accounts/{id}`? What term resonates with business users?
2. **Contact scope:** Can contacts be created without a party? Or always owned by a party?
3. **Vehicle ownership:** Can a vehicle be reassigned to a different party after creation?
4. **Deep-link party details:** Can users bookmark `/crm/parties/party-123/contacts` directly, or must they go through the main PartyView?
5. **Session persistence:** Should tab selection persist across back/forward navigation?

---

## Approval Checklist

- [X] Frontend Lead signs off on route pattern
- [X] UX Designer confirms screen layout and interaction model
- [X] Product Manager aligns with user expectations
- [X] Backend team confirms post-create response format (`partyId` included)
- [-] Accessibility audit for breadcrumbs and keyboard navigation -> Will accomplish later after prototype

---

## Status Transition

**Current:** PENDING DECISION (awaiting design/PM review)  
**Next:** ACCEPTED (upon approval) → IMPLEMENTED (router + components) → ARCHIVED (if superseded)

---

## Related Decisions

- 0004-crm-duplicate-detection.adr.md: Duplicate Detection UX Strategy
- 0005-crm-optimistic-locking.adr.md: Optimistic Locking Conflict Resolution
- [Backend Contract Guide](BACKEND_CONTRACT_GUIDE.md)

---
