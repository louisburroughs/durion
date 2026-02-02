# CAP-089 Frontend Implementation Guide

**Capability:** CAP:089 - Party Management (Commercial Accounts & Individuals)  
**Domain:** CRM  
**Impacted Component Repository:** `louisburroughs/durion-crm`  
**Feature Branch:** `cap/CAP089`  
**Contract Status:** stable-for-ui (Story #95), draft (Stories #96, #97, #98)

---

## Overview

This document outlines the frontend implementation for CAP-089 Party Management, covering four user stories:

1. **Story #95:** Create Commercial Account (Frontend Issue #176) - **PRIORITY: Implement First**
2. **Story #96:** Create Individual Person Record (Frontend Issue #175)
3. **Story #97:** Associate Individuals to Commercial Account (Frontend Issue #174)
4. **Story #98:** Search and Merge Duplicate Parties (Frontend Issue #173)

All implementations will be created in the `durion-crm` component repository at:
`/home/louisb/Projects/durion-moqui-frontend/runtime/component/durion-crm`

---

## Backend Endpoints Summary

### Implemented Endpoints (from Backend Contract Guide)

| Method | Endpoint | Operation ID | Permission | Status |
|--------|----------|--------------|------------|--------|
| POST | `/v1/crm/accounts/parties` | `createCommercialAccount` | `crm:party:create` | UNIMPLEMENTED (501) |
| GET | `/v1/crm/accounts/parties/{partyId}` | `getParty` | `crm:party:view` | UNIMPLEMENTED (501) |
| POST | `/v1/crm/accounts/parties/search` | `searchParties` | `crm:party:search` | UNIMPLEMENTED (501) |
| POST | `/v1/crm/accounts/parties/{partyId}/merge` | `mergeParties` | `crm:party:merge` | UNIMPLEMENTED (501) |
| GET | `/v1/crm/accounts/parties/{partyId}/contacts` | `getContactsWithRoles` | `crm:party:view` | UNIMPLEMENTED (501) |
| PUT | `/v1/crm/accounts/parties/{partyId}/contacts/{contactId}/roles` | `updateContactRoles` | `crm:party:edit` | UNIMPLEMENTED (501) |

**Note:** All endpoints return 501 (Not Implemented). Frontend must implement graceful degradation and error handling.

---

## Implementation Checklist

### Phase 1: Story #95 - Create Commercial Account (Issue #176) ✅ PRIORITY

**Status:** Contract stable-for-ui

**Files to Create:**
- `screen/durion/crm/party/CommercialAccountCreate.xml` - Main screen
- `service/crm/PartyServices.xml` - Moqui service wrappers
- `entity/PartyEntities.xml` - Entity definitions (if needed)

**Implementation Steps:**
1. ✅ Create feature branch `cap/CAP089` in durion-crm
2. ⬜ Create Moqui screen `CommercialAccountCreate.xml` with:
   - Form fields: legalName, dbaName, taxId, defaultBillingTermsId
   - Client-side validation (required fields only)
   - Duplicate check flow
   - Override confirmation with justification
   - Success state with partyId display
3. ⬜ Create Moqui service wrapper `crm.Party.createCommercialAccount`:
   - Calls backend `/v1/crm/accounts/parties` endpoint
   - Handles 201/200 success responses
   - Maps 400 (validation), 403 (forbidden), 409 (duplicate) errors
   - Preserves correlation ID for error tracking
4. ⬜ Implement duplicate detection UX:
   - Show candidates panel when backend returns duplicates
   - Require explicit checkbox confirmation + justification (min 5 chars)
   - "Create anyway" button gated by both
5. ⬜ Add error handling:
   - Inline field errors for 400 responses
   - Access denied state for 403
   - Duplicate panel for 409
   - Generic error for 5xx with correlation ID display
6. ⬜ Test scenarios:
   - Create with required fields only
   - Missing required field validation
   - Duplicate detection and override
   - Backend validation errors
   - Unauthorized access (403)

**TypeScript/DTO Types (from contract):**
```typescript
// Request DTO
interface CreateCommercialAccountRequest {
  legalName: string;          // required
  dbaName?: string;           // optional
  taxId?: string;             // optional
  defaultBillingTermsId: string; // required
  externalIdentifiers?: Map<string, string>; // optional
  duplicateOverride?: boolean; // default false
  duplicateOverrideJustification?: string; // required when override=true
  duplicateCandidatePartyIds?: string[]; // optional for audit
}

// Response DTO
interface CreateCommercialAccountResponse {
  partyId: string;            // UUID - canonical identifier
  legalName: string;
  status: 'ACTIVE' | 'PENDING' | 'SUSPENDED';
  createdAt: string;          // ISO 8601
  duplicateCandidates?: DuplicateCandidate[]; // present if duplicates found
}

interface DuplicateCandidate {
  partyId: string;
  legalName: string;
  matchReason?: string;
}

// Error Response
interface ErrorResponse {
  errorCode: string;          // e.g., 'VALIDATION_ERROR', 'DUPLICATE_PARTY'
  message: string;
  correlationId?: string;
  fieldErrors?: Record<string, string>;
  context?: any;              // Additional context like duplicate candidates
}
```

**Wireframe Reference:** `/durion/domains/crm/.ui/frontend-story-party-create-commercial-account-176.wf.md`

---

### Phase 2: Story #96 - Create Individual Person Record (Issue #175)

**Status:** Contract draft

**Files to Create:**
- `screen/durion/crm/party/PersonCreate.xml`
- Service methods in `service/crm/PartyServices.xml`

**Implementation Steps:**
1. ⬜ Create Moqui screen `PersonCreate.xml` with:
   - Person fields: firstName, lastName, preferredContactMethod
   - Repeatable contact points (email/phone with primary flags)
   - Client-side validation (required, email format, phone normalization)
   - Primary enforcement (max one per kind)
2. ⬜ Implement phone normalization:
   - Accept flexible input (spaces, dashes, parentheses)
   - Normalize to digits-only (10-15 digits)
   - Separate extension field
3. ⬜ Implement primary constraints:
   - Auto-demote previous primary when new one selected
   - Maximum one primary email, one primary phone
4. ⬜ Create service wrapper for person creation
5. ⬜ Add error handling per contract
6. ⬜ Test scenarios per acceptance criteria

**TypeScript/DTO Types:**
```typescript
interface CreatePersonRequest {
  firstName: string;          // required
  lastName: string;           // required
  preferredContactMethod: 'EMAIL' | 'PHONE_CALL' | 'SMS' | 'NONE'; // required
  contactPoints: ContactPoint[];
}

interface ContactPoint {
  kind: 'EMAIL' | 'PHONE';    // required, immutable
  emailAddress?: string;      // required if kind=EMAIL
  phoneNumber?: string;       // required if kind=PHONE (display value)
  normalizedDigits?: string;  // derived from phoneNumber (10-15 digits)
  extension?: string;         // optional, digits-only
  isPrimary: boolean;         // default false; max one per kind
}

interface CreatePersonResponse {
  partyId: string;            // UUID canonical identifier
  firstName: string;
  lastName: string;
  createdAt?: string;
  createdBy?: string;
}
```

**Wireframe Reference:** `/durion/domains/crm/.ui/frontend-story-party-create-individual-person-reco-175.wf.md`

---

### Phase 3: Story #97 - Associate Individuals to Commercial Account (Issue #174)

**Status:** Contract draft

**Files to Create:**
- `screen/durion/crm/party/CommercialAccountContacts.xml`
- Service methods in `service/crm/PartyServices.xml`

**Implementation Steps:**
1. ⬜ Create screen to view/manage party relationships
2. ⬜ List display with:
   - Person display name + partyId
   - roleType (APPROVER, BILLING)
   - effectiveStartDate, effectiveEndDate
   - isPrimary indicator (BILLING only)
3. ⬜ Implement relationship creation:
   - Person selection/search
   - Role selection
   - Effective date picker
   - "Set as primary billing contact" option
4. ⬜ Implement primary billing contact management:
   - "Set Primary Billing Contact" action
   - Backend atomic auto-demotion of previous primary
   - List refresh after change
5. ⬜ Implement deactivation:
   - Set effectiveEndDate
   - Validation: endDate >= startDate
6. ⬜ Add filtering: ACTIVE (default), INACTIVE, ALL
7. ⬜ Test scenarios per acceptance criteria

**TypeScript/DTO Types:**
```typescript
interface PartyRelationship {
  partyRelationshipId: string; // UUID
  accountPartyId: string;      // commercial account partyId
  personPartyId: string;       // individual person partyId
  personDisplayName: string;
  roleType: 'APPROVER' | 'BILLING'; // system-defined
  isPrimary: boolean;          // meaningful only for BILLING role
  effectiveStartDate: string;  // ISO date
  effectiveEndDate?: string;   // ISO date; null if active
  status?: 'ACTIVE' | 'INACTIVE'; // derived or backend-provided
  createdAt?: string;
  createdByUserId?: string;
  updatedAt?: string;
  updatedByUserId?: string;
}

interface CreateRelationshipRequest {
  accountPartyId: string;
  personPartyId: string;
  roleType: 'APPROVER' | 'BILLING';
  effectiveStartDate: string;
  setPrimaryBilling?: boolean; // only for BILLING role
}

interface SetPrimaryBillingRequest {
  accountPartyId: string;
  partyRelationshipId: string;
}

interface DeactivateRelationshipRequest {
  partyRelationshipId: string;
  effectiveEndDate: string;
}
```

**Wireframe Reference:** `/durion/domains/crm/.ui/frontend-story-party-associate-individuals-to-comm-174.wf.md`

---

### Phase 4: Story #98 - Search and Merge Duplicate Parties (Issue #173)

**Status:** Contract draft

**Files to Create:**
- `screen/durion/crm/party/PartyMergeSearch.xml`
- `screen/durion/crm/party/PartyMergeConfirm.xml`
- `screen/durion/crm/party/PartyMergeResult.xml`
- Service methods in `service/crm/PartyServices.xml`

**Implementation Steps:**
1. ⬜ Create search screen with:
   - Search form: name, email, phone (at least one required)
   - No browse-all (DECISION-INVENTORY-014)
   - Results list with multi-select (exactly two)
   - "Continue to Merge" button (enabled when 2 selected)
2. ⬜ Create confirmation screen with:
   - Display both selected parties
   - Survivor/source radio selection
   - Required justification (5-500 chars)
   - Acknowledgement checkbox
   - "Merge Parties" button (gated)
3. ⬜ Create result screen with:
   - Success message
   - survivorPartyId, sourcePartyId
   - mergeAuditId
   - Link to survivor party detail
   - Optional "Try opening source party" link (demonstrates 409 PARTY_MERGED)
4. ⬜ Implement merged party handling:
   - Party detail screens must handle 409 PARTY_MERGED
   - Display mergedToPartyId
   - Offer redirect to survivor
5. ⬜ Add validation:
   - At least one search criterion required
   - Exactly two selected parties
   - Survivor ≠ source
   - Justification length
6. ⬜ Test scenarios per acceptance criteria

**TypeScript/DTO Types:**
```typescript
interface SearchPartiesRequest {
  name?: string;
  email?: string;
  phone?: string;
  taxId?: string;
  partyType?: 'ORGANIZATION' | 'INDIVIDUAL';
  status?: 'ACTIVE' | 'PENDING' | 'SUSPENDED' | 'INACTIVE';
  includeMerged?: boolean;    // admin-only
  pageNumber?: number;        // default 1
  pageSize?: number;          // default 20, max 100
  sortField?: 'legalName' | 'createdAt' | 'modifiedAt';
  sortOrder?: 'ASC' | 'DESC';
}

interface SearchPartiesResponse {
  results: PartySummary[];
  totalCount: number;
  pageNumber: number;
  pageSize: number;
}

interface PartySummary {
  partyId: string;
  legalName?: string;
  displayName?: string;
  partyType: 'ORGANIZATION' | 'INDIVIDUAL';
  status: string;
  createdAt: string;
}

interface MergePartiesRequest {
  survivorPartyId: string;    // path param or survivor selection
  losingPartyId: string;      // source/losing party
  justification: string;      // required, 5-500 chars
}

interface MergePartiesResponse {
  mergeAuditId: string;
  survivorPartyId: string;
  losingPartyId: string;
  mergedPartyAlias?: string;  // for backwards compatibility
  status: 'COMPLETED' | 'PENDING';
  completedAt: string;
}

// 409 Response for merged party reads
interface PartyMergedError {
  errorCode: 'PARTY_MERGED';
  message: string;
  mergedToPartyId: string;    // redirect target
  correlationId?: string;
}
```

**Wireframe Reference:** `/durion/domains/crm/.ui/frontend-story-party-search-and-merge-duplicate-pa-173.wf.md`

---

## Cross-Cutting Concerns

### API Integration via durion-positivity Bridge

All backend API calls MUST go through the existing `runtime/component/durion-positivity/` bridge. Do not create ad-hoc HTTP clients.

**Pattern:**
```groovy
// In Moqui service definition
<service verb="create" noun="CommercialAccount">
    <in-parameters>
        <parameter name="legalName" required="true"/>
        <parameter name="dbaName"/>
        <parameter name="taxId"/>
        <parameter name="defaultBillingTermsId" required="true"/>
    </in-parameters>
    <out-parameters>
        <parameter name="partyId"/>
    </out-parameters>
    <actions>
        <script><![CDATA[
            // Call backend via durion-positivity bridge
            def response = ec.service.sync()
                .name('durion.positivity.RestClient.post')
                .parameter('endpoint', '/v1/crm/accounts/parties')
                .parameter('body', [
                    legalName: legalName,
                    dbaName: dbaName,
                    taxId: taxId,
                    defaultBillingTermsId: defaultBillingTermsId
                ])
                .call()
            
            // Handle response
            if (response.statusCode == 201 || response.statusCode == 200) {
                partyId = response.body.partyId
            } else if (response.statusCode == 409) {
                // Handle duplicate candidates
                ec.message.addError("Duplicate parties found")
            } else if (response.statusCode == 403) {
                ec.message.addError("Access denied")
            }
        ]]></script>
    </actions>
</service>
```

### Error Handling Standards

All screens must implement consistent error handling:

1. **400 Validation Errors:**
   - Map `fieldErrors` to inline field messages
   - Show form-level summary banner
   - Preserve user inputs

2. **403 Forbidden:**
   - Show access denied state
   - Disable mutation controls
   - Do not show partial data

3. **404 Not Found:**
   - Show "Record not found" message
   - Offer refresh action

4. **409 Conflict:**
   - For duplicates: show candidates panel
   - For `PARTY_MERGED`: show redirect to `mergedToPartyId`
   - For others: show conflict message + refresh prompt

5. **5xx Server Errors:**
   - Show generic error (no PII)
   - Display correlation ID if present
   - Preserve form inputs for retry

### Canonical Identifier Usage (DECISION-INVENTORY-001)

**MANDATORY:** Always use `partyId` as the canonical identifier. Never introduce `customerId` as a primary key in:
- URL routes (use `/party/{partyId}`)
- Service parameters
- Entity fields (except foreign keys)
- UI state/display

### Security & Permissions

All screens must handle permission-based access control:

**Required Permissions (from backend):**
- `crm:party:create` - Create parties/commercial accounts
- `crm:party:view` - View party details
- `crm:party:search` - Search parties
- `crm:party:edit` - Edit party relationships
- `crm:party:merge` - Merge duplicate parties (Admin-only)

**Implementation Pattern:**
- Backend enforces via `@PreAuthorize` annotations
- Frontend handles 403 responses gracefully
- Hide entry points when permission known missing
- Always fail closed (show access denied vs. partial data)

### Observability & Correlation

All service calls must:
- Propagate `X-Correlation-Id` header
- Display correlation ID in error states
- NOT log PII (emails, phones, tax IDs) client-side
- Log only UUIDs (partyId, relationshipId) for tracing

---

## Testing Requirements

### Unit Tests (Jest)

Create tests for each screen's key user flows:

**Story #95 Tests:**
- ✅ Create with required fields succeeds
- ✅ Missing required field blocks submission
- ✅ Duplicate warning shown when matches exist
- ✅ Override requires confirmation + justification
- ✅ Backend validation errors displayed inline
- ✅ Unauthorized user sees access denied

**Story #96 Tests:**
- ✅ Minimal person create succeeds
- ✅ Multiple emails/phones with primary enforcement
- ✅ Missing required field blocked
- ✅ Invalid email rejected
- ✅ Invalid phone (not 10-15 digits) blocked
- ✅ Unauthorized access denied

**Story #97 Tests:**
- ✅ View active relationships
- ✅ Create APPROVER relationship
- ✅ Create BILLING with primary flag
- ✅ Change primary billing contact (atomic demotion)
- ✅ Deactivate relationship
- ✅ Prevent overlapping relationships (409)
- ✅ Unauthorized access denied

**Story #98 Tests:**
- ✅ Search requires criteria
- ✅ Select exactly two parties enforced
- ✅ Survivor selection + justification required
- ✅ Merge succeeds with audit
- ✅ Self-merge prevented
- ✅ Cross-subtype merge rejected
- ✅ Merged party read shows redirect (409)
- ✅ Unauthorized access denied

---

## Deployment & PR Creation

### Git Workflow

```bash
# All work in feature branch
cd /home/louisb/Projects/durion-moqui-frontend/runtime/component/durion-crm
git checkout cap/CAP089

# After each story completion
git add screen/ service/ entity/
git commit -m "feat(crm): CAP089 Story #95 - Create Commercial Account UI"

# After all stories complete
git push -u origin cap/CAP089

# Create PR
gh pr create --base main --head cap/CAP089 \
  --title "feat(crm): CAP089 Party Management frontend UI" \
  --body "Implements frontend UI for CAP:089 Party Management capability.

## Summary
- ✅ Story #95: Create Commercial Account (Issue #176)
- ✅ Story #96: Create Individual Person Record (Issue #175)
- ✅ Story #97: Associate Individuals to Commercial Account (Issue #174)
- ✅ Story #98: Search and Merge Duplicate Parties (Issue #173)

## Testing
All acceptance criteria tests passing. See test files in `tests/`.

## Backend Dependencies
Backend endpoints return 501 (Not Implemented). Frontend implements graceful degradation and error handling.

## Related Issues
Closes louisburroughs/durion-moqui-frontend#176
Closes louisburroughs/durion-moqui-frontend#175
Closes louisburroughs/durion-moqui-frontend#174
Closes louisburroughs/durion-moqui-frontend#173
"
```

---

## Open Questions & Blockers

### Story #95 (Stable for UI - Can Proceed)
- ✅ Contract status: stable-for-ui
- ⚠️ Backend returns 501 - implement graceful degradation

### Story #96 (Draft - Needs Clarification)
- ❓ Phone normalization: Does backend accept `normalizedDigits` + `extension` separately?
- ❓ Contact point validation: Will backend return indexed field keys (e.g., `contactPoints[1].emailAddress`)?

### Story #97 (Draft - Needs Clarification)
- ❓ Moqui service names: What are the exact service/endpoint names for relationship management?
- ❓ Primary billing auto-demotion: Confirmed as atomic backend operation?

### Story #98 (Draft - Needs Clarification)
- ❓ Backend contract: Are search and merge services implemented?
- ❓ Justification field: Min/max length requirements?
- ❓ Search matching: Exact vs. prefix vs. contains?
- ❓ Merged exclusion: Is `includeMerged` toggle supported?

---

## References

- **Backend Contract Guide:** `/durion/domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- **Capability Manifest:** `/durion/docs/capabilities/CAP-089/CAPABILITY_MANIFEST.yaml`
- **Wireframes:**
  - Story #95: `/durion/domains/crm/.ui/frontend-story-party-create-commercial-account-176.wf.md`
  - Story #96: `/durion/domains/crm/.ui/frontend-story-party-create-individual-person-reco-175.wf.md`
  - Story #97: `/durion/domains/crm/.ui/frontend-story-party-associate-individuals-to-comm-174.wf.md`
  - Story #98: `/durion/domains/crm/.ui/frontend-story-party-search-and-merge-duplicate-pa-173.wf.md`
- **Frontend AGENTS.md:** `/durion-moqui-frontend/AGENTS.md`
- **Architecture Docs:** `/durion/docs/architecture/`
- **Backend Issue Comments:** Review comments in issues #176, #175, #174, #173 for clarifications

---

## Summary

This frontend implementation covers all four stories for CAP-089 Party Management. Story #95 (Create Commercial Account) has stable-for-UI contract status and can proceed immediately. Stories #96-98 are in draft status and may require contract clarification before full implementation.

All implementations follow Moqui/Vue 3/Quasar patterns, use the durion-positivity API bridge, enforce canonical `partyId` identifier usage, and implement comprehensive error handling and permission checks.

**Current Status:**
- ✅ Feature branch created: `cap/CAP089`
- ⬜ Story #95 implementation (PRIORITY)
- ⬜ Story #96 implementation
- ⬜ Story #97 implementation
- ⬜ Story #98 implementation
- ⬜ Tests written
- ⬜ PR created

**Estimated Effort:** 3-5 days (assuming backend endpoints return 501 and require graceful degradation)
