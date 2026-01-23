# ADR-CRM-003: Optimistic Locking Conflict Resolution

**Status:** PENDING DECISION  
**Date:** 2026-01-23  
**Deciders:** Frontend Lead, Backend Lead, UX Designer, Database Administrator  
**Affected Issues:** #171 (Communication Preferences)

---

## Context

The CRM system allows concurrent edits of shared entities (e.g., communication preferences for a party/contact). Currently, there is no collision detection or conflict resolution strategy:

- **Current Risk:** If two users edit communication preferences simultaneously, the second save overwrites the first (lost update problem)
- **Example Scenario:**
  - User A: Changes `emailPreference` from `OPT_OUT` → `OPT_IN`
  - User B: Changes `smsPreference` from `OPT_IN` → `OPT_OUT`
  - User A saves first (succeeds)
  - User B saves second (loses A's email change)
- **Problem:** No feedback to User B; email preference reverts to `OPT_OUT` silently

This ADR defines an **optimistic locking strategy** to detect and resolve such conflicts at the UI level.

---

## Decision

Implement **optimistic locking with a version field** and provide a **conflict resolution modal** that lets users view, merge, or retry changes.

### 1. Version Field Strategy

**Proposed:**

- **Field Name:** `version` (incremental integer)
- **Initial Value:** `1` when entity created
- **Increment Rule:** Backend increments on every update (1 → 2 → 3)
- **Included in Requests:** Frontend sends `version` with every PUT/PATCH/upsert request
- **Validation Rule:** Backend rejects if `submittedVersion ≠ currentVersion`
- **Scope:** Applied to **communication preferences** initially; consider extending to other mutable entities

**Backend Behavior:**

```java
// Pseudocode: Update communication preferences
@PutMapping("/v1/crm/parties/{partyId}/communication-preferences")
public ResponseEntity<?> updateComms(
    @PathVariable String partyId,
    @RequestBody CommsPreferencesRequest request  // includes version
) {
    CommsPreferences current = repo.findByPartyId(partyId);
    
    if (request.version != current.version) {
        // Version mismatch: optimistic lock conflict
        return ResponseEntity.status(409)
            .body(ConflictResponse.builder()
                .errorCode("OPTIMISTIC_LOCK_CONFLICT")
                .message("Record updated by another user")
                .currentVersion(current.version)
                .submittedVersion(request.version)
                .currentData(current)  // User can see what changed
                .build()
            );
    }
    
    // Update and increment version
    current.setEmailPreference(request.emailPreference);
    current.setVersion(current.getVersion() + 1);
    repo.save(current);
    
    return ResponseEntity.ok(current);
}
```

**Decision on version field name:** (`version` / `lastUpdatedStamp` / `eTag`)  
**Decision:** ______________________

### 2. Frontend Version Tracking

**Proposed:**

1. **Load communication preferences:**
   ```vue
   const comms = await service.getComms(partyId);
   // comms = {
   //   emailPreference: 'OPT_IN',
   //   smsPreference: 'OPT_IN',
   //   version: 2,
   // }
   ```

2. **On form edit:**
   - Keep `version` in component state (don't show to user)
   - Store original `version` to detect if data was updated elsewhere

3. **On form submit:**
   - Include `version` in request payload:
   ```json
   {
     "emailPreference": "OPT_OUT",
     "smsPreference": "OPT_IN",
     "version": 2
   }
   ```

4. **On 409 Conflict response:**
   - Show **ConflictResolutionModal** with current data vs. user's changes

**Decision:** ______________________

### 3. Conflict Resolution Modal

**Trigger:** When backend responds with `409 OPTIMISTIC_LOCK_CONFLICT`

**Modal Design:**

```
┌──────────────────────────────────────────────────────┐
│ ⓧ Save Conflict                                      │
├──────────────────────────────────────────────────────┤
│ This record was updated by another user since you    │
│ started editing. Your changes cannot be saved yet.   │
│                                                      │
│ Updated by: Jane Smith (2026-01-23 15:42:30 UTC)    │
│                                                      │
│ ┌─ Changes ─────────────────────────────────────────┐
│ │ Field                Current      Your Change     │
│ │ ─────────────────────────────────────────────────  │
│ │ emailPreference      OPT_IN   →   OPT_OUT         │
│ │ smsPreference        OPT_OUT  →   OPT_IN          │
│ │ pushNotification     (no change)  (no change)     │
│ └───────────────────────────────────────────────────┘
│                                                      │
│ ○ Reload & Retry    ○ Discard    ○ Override & Save  │
│ [                 Cancel                          ] │
└──────────────────────────────────────────────────────┘
```

**Modal Content:**

| Section | Content | Purpose |
|---------|---------|---------|
| Title | "Save Conflict" | Clear, non-alarming |
| Message | Explanation + timestamp/user | Context for why conflict happened |
| Field Comparison | Side-by-side: current vs. submitted | Show exactly what conflicts |
| Action Buttons | 3–4 options | Let user choose resolution strategy |

**Decision on modal design:** ______________________

### 4. Resolution Options

#### Option 1: "Reload & Retry" (Recommended Default)

**Flow:**
1. User clicks "Reload & Retry"
2. Frontend fetches latest data: `GET /crm/parties/{partyId}/communication-preferences`
3. Modal closes
4. Form pre-filled with latest server data
5. User's edits are **preserved where possible** (smart merge)
6. User can review, edit again, and retry save
7. Success: Form clears with banner "Changes saved"

**Smart Merge Logic (Optional):**
```
// If user edited emailPreference and server updated smsPreference
Original:      { emailPreference: OPT_IN, smsPreference: OPT_IN, version: 1 }
Server:        { emailPreference: OPT_IN, smsPreference: OPT_OUT, version: 2 }
User Changed:  { emailPreference: OPT_OUT }
Result Merged: { emailPreference: OPT_OUT, smsPreference: OPT_OUT, version: 2 }

Banner: "Merged with server changes. Verify and save again."
```

**Decision:** Include smart merge? (YES / NO)  
**Decision:** ______________________

#### Option 2: "Discard My Changes"

**Flow:**
1. User clicks "Discard"
2. Confirmation: "Are you sure? Your edits will be lost."
3. On confirm: Form resets to latest server data
4. Message: "Changes discarded. Reloaded from server."

**Use Case:** User is unsure of their edits, prefers server version

**Decision:** ______________________

#### Option 3: "Override & Save" (Privileged Only)

**Flow:**
1. User clicks "Override & Save"
2. Confirmation warning: "This will overwrite the version saved by another user. Proceed?"
3. Extra confirmation checkbox: "I understand the consequences"
4. On confirm: Force save (no version check on retry)
5. Banner: "Override save succeeded. Version is now {newVersion}"

**Security Model:**
- Only available to users with `EDIT_PRIVILEGED` or `ADMIN` permission
- Log override in audit trail: `action: OVERRIDE_SAVE, by: {userId}, partyId: {id}, oldVersion: {v}, newVersion: {v+1}`
- May be disabled entirely for MVP (not recommended)

**Decision:** Include override option? (YES / NO / ADMIN_ONLY)  
**Decision:** ______________________

#### Option 4: "Cancel"

**Flow:**
1. User clicks "Cancel"
2. Modal closes
3. Form retains user's edits (nothing is lost)
4. User can edit and retry, or abandon

**Decision:** ______________________

### 5. Special Conflict Scenarios

#### Scenario A: Optimistic Lock on Delete

**Context:** User tries to update entity that was deleted by another user.

**Backend Response:**
```json
{
  "errorCode": "ENTITY_NOT_FOUND",
  "message": "This record no longer exists",
  "reason": "DELETED_BY_ANOTHER_USER"
}
```

**UX:**
- Show modal: "This record no longer exists. It may have been deleted by another user."
- Offer: "Go Back to {PartyView}" or "Cancel"
- Do NOT offer "Create New"

**Decision:** ______________________

#### Scenario B: Version Gap (Highly Concurrent)

**Context:** User submits version 2, but server is at version 5 (gap indicates high concurrency or sync issues).

**Backend Response:**
```json
{
  "errorCode": "OPTIMISTIC_LOCK_CONFLICT",
  "message": "Unable to merge due to version mismatch",
  "submittedVersion": 2,
  "currentVersion": 5,
  "gapWarning": true
}
```

**UX:**
- Show warning: "The record has been updated multiple times. Viewing latest..."
- Offer: "Reload & Retry" (smart merge may not work)
- Suggest: "If conflicts persist, contact an administrator"

**Decision:** ______________________

#### Scenario C: Simultaneous Delete + Update

**Context:** User A updates record, User B deletes it simultaneously.

**Handling:**
- User A's update fails with "ENTITY_NOT_FOUND" (delete wins)
- User B's delete succeeds
- Show User A: "This record was deleted. Changes could not be saved."

**Decision:** ______________________

### 6. Retry Limit & Backoff

**Proposed:**

- **Max Retries:** 3 attempts per session
- **Backoff Strategy:** Exponential (0s, 1s, 2s) to avoid thundering herd
- **After Limit:** Show message: "Could not save after 3 attempts. Please contact support or try again later."

**Decision:** ______________________

---

## Alternatives Considered

### Alternative A: Last-Write-Wins (No Locking)
**Concept:** No version field; latest save always succeeds  
**Pros:** Simple, no conflicts  
**Cons:** Silent data loss; compliance/audit risk

### Alternative B: Pessimistic Locking (Row-Level Locks)
**Concept:** Backend acquires exclusive lock on read; released on update/abandon  
**Pros:** Guaranteed conflict-free  
**Cons:** Scalability bottleneck; long lock times = poor UX; requires timeout handling

### Alternative C: Event Sourcing / Conflict-Free Replicated Data Types (CRDTs)
**Concept:** Store all edits as events; merge algorithmically  
**Pros:** No conflicts; full audit trail  
**Cons:** Complex, overkill for MVP; slow queries

### Alternative D: Server-Side Conflict Auto-Merge
**Concept:** Backend intelligently merges non-overlapping changes  
**Pros:** Seamless UX; fewer conflicts visible to user  
**Cons:** Black-box merging (user doesn't understand what changed); wrong merges possible

### Alternative E: Modal Notification Only (No Resolution)
**Concept:** Show conflict, but don't offer resolution options  
**Pros:** Simple  
**Cons:** Leaves user stranded; frustrating UX

---

## Consequences

### Positive
- ✅ Prevents silent data loss from concurrent edits
- ✅ User sees exactly what conflicted
- ✅ Multiple resolution strategies (reload, discard, override)
- ✅ Audit trail of overrides for compliance
- ✅ Smart merge reduces manual re-entry for non-overlapping edits
- ✅ Scales well (no server-side locks)

### Negative
- ❌ Adds version field to all mutable entities (schema change)
- ❌ Conflict resolution requires backend changes (409 response, version comparison)
- ❌ Modal adds UX friction; users may not understand options
- ❌ Smart merge logic is complex; edge cases possible
- ❌ Override option (if enabled) reintroduces data loss risk

### Risks
- ⚠️ If version field not incremented on backend, conflicts not detected
- ⚠️ If 409 response omits `currentData`, user can't compare changes
- ⚠️ Smart merge may incorrectly merge semantically related fields
- ⚠️ User confusion: "Reload & Retry" vs "Discard" (both reload data)

---

## Compliance

- **Data Integrity:** Optimistic locking ensures read-modify-write consistency
- **Audit Trail:** Override saves are logged with user/timestamp
- **GDPR Right to Erasure:** Version history may complicate deletion (consider retention policy)
- **WCAG 2.1:** Modal is keyboard-navigable; comparison table is accessible

---

## Implementation Notes

### Backend Changes
- Add `version` field to communication preferences entity
- Update repository to check version on save
- Return 409 with conflict details on mismatch
- Log all override saves to audit table

### Frontend Changes
- Store `version` in component state (hidden from user)
- Include `version` in all upsert requests
- Implement `ConflictResolutionModal.vue`
- Implement smart merge logic (optional)
- Add retry logic with backoff

### Database Migration
```sql
ALTER TABLE communication_preferences 
ADD COLUMN version INT NOT NULL DEFAULT 1;

CREATE INDEX idx_comms_version ON communication_preferences(party_id, version);
```

### Metrics to Track
- **Conflict Rate:** % of saves that hit 409
- **Conflict Frequency:** Avg conflicts per entity per day
- **Resolution Choice:** % "Reload" vs "Discard" vs "Override"
- **Retry Success:** % of retries that succeed after reload
- **User Satisfaction:** Survey after conflict resolution

---

## Open Questions for Deciders

1. **Scope:** Should optimistic locking apply only to communication preferences, or all CRM entities?
2. **Smart Merge:** Implement auto-merge, or require user to manually re-enter changes?
3. **Override Permission:** Who should be allowed to use "Override & Save"? Admins only? Power users?
4. **Retry Limit:** 3 attempts sufficient, or too low/high?
5. **Timestamp vs. Version:** Use simple `version` integer or `lastUpdatedStamp` timestamp?

---

## Approval Checklist

- [ ] Backend Lead signs off on version field strategy and 409 response format
- [ ] Frontend Lead confirms conflict modal design and resolution logic
- [ ] UX Designer reviews "Reload & Retry" vs "Discard" terminology
- [ ] Database Administrator approves schema changes and indexes
- [ ] Audit/Compliance confirms override logging and retention policy
- [ ] QA signs off on test scenarios (delete, version gap, retry limit)

---

## Status Transition

**Current:** PENDING DECISION (awaiting backend/UX review)  
**Next:** ACCEPTED (upon approval) → IMPLEMENTED (schema + backend + modal) → ARCHIVED

---

## Related Decisions

- [ADR-CRM-001: Navigation Patterns](ADR-CRM-001-Navigation-Patterns.md)
- [ADR-CRM-002: Duplicate Detection](ADR-CRM-002-Duplicate-Detection.md)
- [Backend Contract Guide](../domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md)

---
