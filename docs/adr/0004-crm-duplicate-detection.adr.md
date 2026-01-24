# ADR-CRM-002: Duplicate Detection UX Strategy

**Status:** ACCEPTED  
**Date:** 2026-01-23  
**Deciders:** Frontend Lead, UX Designer, Product Manager, Backend Lead  
**Affected Issues:** #176 (Create Commercial Account)

---

## Context

When users create a party (commercial account), the backend performs duplicate detection to prevent duplicate records. Currently, there is no clear UX for handling this scenario:

- **Current State:** Backend contract specifies `409 DUPLICATE_CANDIDATES` response, but frontend behavior is undefined
- **Problem:** Users need a clear way to decide whether to:
  1. Merge with an existing party (if truly a duplicate)
  2. Create anyway (if a legitimate new entity)
  3. Cancel and revise search
- **Risk:** Without clear UX, users may create unintended duplicates or abandon the create flow

---

## Decision

Implement a **modal-driven duplicate detection UX** that surfaces candidate matches, explains similarity reasons, and enables merge or proceed decisions **before** party creation is finalized.

### 1. Detection Trigger

**Proposed:**
1. User completes "Create Party" form (legal name, tax ID, type, etc.)
2. Clicks "Create" button
3. Frontend calls `crm.createCommercialAccount` with form data
4. Backend responds with `409 Conflict`:
   ```json
   {
     "errorCode": "DUPLICATE_CANDIDATES",
     "message": "Possible duplicate parties found",
     "candidates": [
       {
         "partyId": "party-001",
         "legalName": "Acme Corporation",
         "taxId": "12-3456789",
         "matchReasons": ["exact_name_match", "tax_id_match"],
         "status": "ACTIVE",
         "createdAt": "2025-06-15T10:30:00Z"
       },
       {
         "partyId": "party-002",
         "legalName": "Acme Corp",
         "taxId": null,
         "matchReasons": ["name_similarity"],
         "status": "ACTIVE",
         "createdAt": "2025-04-20T14:22:00Z"
       }
     ]
   }
   ```
5. Frontend shows **DuplicateCandidatesModal** with candidates

**Decision:** ✅ **Resolved** - Accept proposed detection trigger: user completes form → clicks "Create" → backend responds with `409 DUPLICATE_CANDIDATES` if matches found → frontend shows DuplicateCandidatesModal with candidates.

### 2. Modal Content & Design

**Modal Title:**
```
"Possible Duplicate Found"
```

**Message:**
```
"We found existing parties that may match the one you're creating. 
Please review the options below and choose how to proceed."
```

**Candidate Display:**

| Column | Content | Purpose |
|--------|---------|---------|
| Radio Button | Select candidate | Choose winning party for merge |
| Legal Name | "Acme Corporation" | Primary identifier |
| Tax ID | "12-3456789" | Verify uniqueness |
| Status | "ACTIVE" / "INACTIVE" | Show entity health |
| Match Reason | "Exact name + tax ID" | Explain similarity |
| Created | "2025-06-15" | Show age of record |

**Visual Example:**
```txt
┌───────────────────────────────────────────────────────┐
│ ⓧ Possible Duplicate Found                            │
├───────────────────────────────────────────────────────┤
│ We found existing parties that may match. Please      │
│ review and choose how to proceed.                     │
│                                                       │
│ ○ Acme Corporation      [12-3456789] ACTIVE           │
│   ✓ Exact name + tax ID match                         │
│   Created 2025-06-15                                  │
│                                                       │
│ ○ Acme Corp             [none]       ACTIVE           │
│   ⓘ Name similarity (95%)                             │
│   Created 2025-04-20                                  │
│                                                       │
│ ○ ACME LLC              [98-7654321] INACTIVE         │
│   ⓘ Possible name variation                           │
│   Created 2024-02-01                                  │
│                                                       │
├───────────────────────────────────────────────────────┤
│ [Merge with Selected] [Create Anyway] [Cancel]        │
└───────────────────────────────────────────────────────┘
```

**Decision:** ✅ **Resolved** - Accept modal design as-is: candidate display with radio buttons, legal name, tax ID, status, match reason, and created date. Clear action buttons with appropriate hierarchy.

### 3. Action Buttons & Behavior

#### Button 1: "Merge with Selected"

**Precondition:** User selects at least one candidate

**Flow:**
1. User clicks "Merge with Selected"
2. Frontend navigates to **Merge Confirmation Screen**
3. Show:
   - Winning party (selected from candidates): Full details
   - Losing party (user's form data): Fields to be merged
   - Comparison: "This will consolidate contacts, vehicles, and identifiers"
   - Optional field: "Why are these duplicates?" (Select Option)
     - Additional data available
     - User error
     - Information has changed
     - Other
   - Checkbox: "I agree to overwrite exiting data with my changes"
4. User clicks "Confirm Merge"
5. Frontend calls `crm.mergeParties(winningPartyId, losingPartyData, justification)`
6. On success: Redirect to merged party view with banner: "Parties merged. Conflicts resolved."

**Decision:** ✅ **Resolved** - Accept proposed flow: user selects candidate → clicks "Merge with Selected" → shows merge confirmation screen with full details, comparison, merge reason, and confirmation checkbox → on confirm, call `crm.mergeParties()` → redirect to merged party view.

#### Button 2: "Create Anyway"

**Flow:**
1. User clicks "Create Anyway"
2. Modal shows warning banner:
   ```
   ⚠️ Warning: We found possible duplicates. Creating a new party may result in 
      multiple records for the same entity. You can merge them later.
   ```
3. Offer checkbox: "I confirm this is a new, unique party"
4. On confirmation, proceed with original `crm.createCommercialAccount` call
5. On success: Redirect to new party view with warning banner

**Rationale:** Allow users to proceed if they're confident (e.g., branch office of same company, new fiscal entity)

**Decision:** ✅ **Resolved** - Accept proposed flow: user clicks "Create Anyway" → modal shows warning banner with explanation and confirmation checkbox → on confirmation, proceed with original `crm.createCommercialAccount` call → redirect to new party view with warning banner.

#### Button 3: "Cancel"

**Flow:**
1. User clicks "Cancel"
2. Modal closes
3. Form is preserved (user can edit and try again)
4. Show info banner: "Duplicate check cancelled. You can refine your search and try again."

**Decision:** ✅ **Resolved** - Accept proposed flow: user clicks "Cancel" → modal closes → form is preserved → show info banner for user to refine and retry.

### 4. Error Handling

#### Scenario A: Backend Duplicate Check Timeout (>5s)

**Proposed:**
```json
{
  "errorCode": "DUPLICATE_CHECK_TIMEOUT",
  "message": "Duplicate check took too long. You can create anyway or try again.",
  "suggestion": "CREATE_ANYWAY"
}
```

**UX:**
- Show modal with message: "We couldn't complete the duplicate check in time."
- Offer buttons: "Create Anyway" (default) or "Try Again"
- Log warning to telemetry for backend optimization

**Decision:** ✅ **Resolved** - Accept proposed handling: show modal with timeout message → offer "Create Anyway" (default) and "Try Again" buttons → log warning to telemetry.

#### Scenario B: Backend Duplicate Check Unavailable (503)

**Proposed:**
```json
{
  "errorCode": "DUPLICATE_CHECK_UNAVAILABLE",
  "message": "Duplicate check service is temporarily unavailable. You can create anyway or try again later.",
  "suggestion": "RETRY"
}
```

**UX:**
- Show error banner: "Duplicate check unavailable. Please try again in a moment or create anyway (not recommended)."
- REJECTED: Disable "Create" button for 5 seconds, then re-enable (removes user agency; confusing UX)
- ACCEPTED: Offer "Create Anyway" with warning (empowers user to proceed if needed)

**Decision:** ✅ **Resolved** - Accept proposed handling with modifications: show error banner explaining unavailability → offer "Create Anyway" with warning and "Try Again" → log to telemetry; do not auto-disable button.

#### Scenario C: Unexpected Duplicate Check Response

**Proposed:**
- Log error to telemetry
- Show user-friendly message: "An unexpected error occurred during duplicate check."
- Offer: "Create Anyway" or "Cancel and Try Later"

**Decision:** ✅ **Resolved** - Accept proposed handling: log unexpected error to telemetry → show user-friendly message explaining error occurred → offer "Create Anyway" or "Cancel and Try Later" buttons; do not expose technical error details to user.

### 5. Validation & Safety Guardrails

**Rule 1: Merge Confirmation Required**
- Justification is optional via predefined choice list (user selects reason or skips):
  - "Data consolidation"
  - "Duplicate account"
  - "Organizational change"
  - "Other (optional free text)"
  - "(No reason provided)"
- Cannot merge without confirming "I understand this is permanent"

**Rule 2: Visual Conflict Preview**
- Show side-by-side: winning party data vs. losing party data
- Highlight conflicting fields (e.g., different tax IDs)
- Let user review before final merge

**Rule 3: Audit Trail**
- All merges logged with `partyId`, `selectedJustification`, `mergedBy`, `timestamp`
- Revert/undo not supported; user must contact support

**Decision:** ✅ **Resolved** - Accept all three rules with modification to Rule 1: justification is optional via predefined choice list (users select reason or skip) rather than required free text. This improves data consistency for audit logging while reducing user friction. Rule 2 and Rule 3 accepted as proposed; audit trail includes selectedJustification (may be null if user skips).

---

## Alternatives Considered

### Alternative A: No Duplicate Modal (Silent Auto-Merge)
**Concept:** Backend auto-merges without user input  
**Pros:** Simple, fast  
**Cons:** Loss of data autonomy; user doesn't know merge happened; legal/audit liability

### Alternative B: Separate Duplicate Search Screen
**Concept:** User must manually search for duplicates before create  
**Pros:** Explicit, familiar (like traditional CRM workflows)  
**Cons:** Extra step; friction in create flow; users skip it

### Alternative C: Lightweight Banner (No Modal)
**Concept:** Show inline banner on form: "Similar parties exist" + link to search  
**Pros:** Non-blocking, less intrusive  
**Cons:** Easy to dismiss/ignore; doesn't prevent creation of duplicates

### Alternative D: Post-Create Merge Assistant
**Concept:** Create party first, then show "Similar parties detected" after success  
**Pros:** Faster create flow  
**Cons:** Already created duplicate (data inconsistency); requires async post-processing

---

## Consequences

### Positive
- ✅ Prevents unintended duplicate creation with clear decision point
- ✅ Enables merge flow without forcing users to manually search
- ✅ Transparent matching reasons build user confidence
- ✅ Audit trail (justification + merge log) satisfies compliance
- ✅ Graceful error handling for backend failures

### Negative
- ❌ Modal adds latency to create flow (5s duplicate check)
- ❌ Requires backend to implement duplicate detection algorithm
- ❌ Merge flow is complex (confirmation screen, justification field, audit logging)
- ❌ Users may choose "Create Anyway" and create unintended duplicates anyway
- ❌ False positives (high match score for truly different parties) create friction

### Risks
- ⚠️ Timeout/unavailability decisions ("Create Anyway") may lead to duplicates
- ⚠️ Match algorithm accuracy depends on backend implementation (not in scope here)
- ⚠️ User education needed: why merge is permanent, what data gets consolidated

---

## Compliance

- **Durion Data Governance:** Duplicate detection aligns with data quality standards
- **Audit Requirements:** Merge justification and audit trail satisfy compliance
- **GDPR:** Merge consolidates personal data appropriately (per retention policies)
- **WCAG 2.1:** Modal is keyboard-navigable, radio buttons and buttons are accessible

---

## Implementation Notes

### Frontend Components
- `DuplicateCandidatesModal.vue` – Display candidates, handle button actions
- `MergeConfirmationScreen.vue` – Show merge preview, capture justification
- `ErrorModal.vue` – Timeout/unavailability scenarios

### Backend Assumptions
- `crm.createCommercialAccount` responds with `409 DUPLICATE_CANDIDATES` on match
- `crm.mergeParties(winningPartyId, losingPartyData, justification)` service exists
- Duplicate check is synchronous and completes within 5s SLA

### Metrics to Track
- **Duplicate Detection Rate:** % of creates that trigger modal
- **Merge Rate:** % of detected duplicates actually merged
- **False Positive Rate:** % of merges that user later reverts
- **Timeout Rate:** % of duplicate checks that timeout
- **"Create Anyway" Rate:** % of users who proceed despite duplicates

---

## Open Questions for Deciders

1. **Duplicate threshold:** What similarity score (0–100%) triggers the modal? 80%? 90%?
2. **Candidate limit:** Show all candidates or top 3 by score?
3. **Justification length:** 50–500 chars sufficient, or need different range?
4. **Timeout behavior:** Allow "Create Anyway" on timeout, or force retry?
5. **Visual merging:** Show which fields will be consolidated in merge preview?

---

## Approval Checklist

- [X] Frontend Lead signs off on modal design and flow
- [X] UX Designer confirms candidate display and button hierarchy
- [X] Product Manager aligns with data governance policy
- [X] Backend Lead confirms duplicate detection algorithm and 409 response format
- [X] Security/Compliance confirms audit trail adequacy
- [-] Accessibility audit for modal and form interactions - TBD later

---

## Status Transition

**Current:** PENDING DECISION (awaiting UX/backend review)  
**Next:** ACCEPTED (upon approval) → IMPLEMENTED (modal + merge flow) → ARCHIVED

---

## Related Decisions

- 0003-crm-navigation-patterns.adr.md
- 0005-crm-optimistic-locking.adr.md
- [Backend Contract Guide](../domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md)

---
