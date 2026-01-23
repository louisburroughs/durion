# CRM Domain Permission Taxonomy

> **Purpose:** Define explicit permission keys for CRM domain operations  
> **Domain:** crm  
> **Owner:** CRM Domain  
> **Version:** 1.0  
> **Status:** PROPOSED (ADR 0002 submitted for architectural review)  
> **Date:** 2026-01-23

## Overview

This document defines the complete permission taxonomy required to implement CRM domain stories. All permissions follow the standardized format: `domain:resource:action` per PERMISSION_REGISTRY.md.

These permissions resolve DECISION-INVENTORY-007 (Permissions + fail-closed enforcement) from crm-questions.txt Priority 1.

---

## Permission Naming Convention

All CRM permissions follow the security domain standard:

- **Format:** `crm:resource:action`
- **All lowercase**
- **Singular nouns for resources**
- **Three-part structure**

Examples: `crm:party:create`, `crm:vehicle:view`, `crm:contact_preference:edit`

---

## CRM Permission Groups

### 1. Party Management (Issues #176, #173)

**Resource:** `party`  
**System of Record:** CRM (canonical customer identifier)  
**Related Decision:** DECISION-INVENTORY-001 (partyId as canonical identifier)

```yaml
domain: crm
serviceName: pos-crm-service
version: "1.0"

permissions:
  # Party CRUD
  party:
    - name: crm:party:view
      description: View party (person/organization) records and details
      riskLevel: LOW
      
    - name: crm:party:search
      description: Search parties by name, email, phone, tax ID
      riskLevel: LOW
      
    - name: crm:party:create
      description: Create new commercial account (party)
      riskLevel: MEDIUM
      applicableStories:
        - issue: "#176"
          title: "Create Commercial Account"
      
    - name: crm:party:edit
      description: Edit party master data (name, tax ID, identifiers)
      riskLevel: MEDIUM
      
    - name: crm:party:deactivate
      description: Deactivate a party record (soft delete)
      riskLevel: HIGH
      
    - name: crm:party:merge
      description: Merge two party records into one survivor
      riskLevel: CRITICAL
      applicableStories:
        - issue: "#173"
          title: "Search and Merge Duplicate Parties"
      notes: |
        - Requires `crm:party:view` and `crm:party:search` to locate candidates
        - Merge operation is atomic and audit-logged (MergeAuditId)
        - Non-reversible; must enforce justification requirement
        - Post-merge, merged party is aliased to survivor for referential integrity
```

### 2. Contact and Role Management (Issues #172, #171)

**Resource:** `contact` / `contact_role` / `contact_preference`  
**System of Record:** CRM (contact point roles and preferences)  
**Related Decisions:** DECISION-INVENTORY-002 (roles ownership), DECISION-INVENTORY-003 (contact point validation)

```yaml
  # Contact Point Management
  contact:
    - name: crm:contact:view
      description: View contact points (email, phone) for a party
      riskLevel: LOW
      notes: "Redaction of sensitive PII per DECISION-INVENTORY-006 applies server-side"
      
    - name: crm:contact:create
      description: Add new contact point (email/phone) to a party
      riskLevel: MEDIUM
      
    - name: crm:contact:edit
      description: Edit contact point details (phone normalization, primary flag)
      riskLevel: MEDIUM
      notes: "Contact kind is immutable per DECISION-INVENTORY-003; only details and primary flag editable"
      
    - name: crm:contact:delete
      description: Remove contact point from a party
      riskLevel: MEDIUM
      
  # Contact Roles (BCC/Approver/Driver relationship)
  contact_role:
    - name: crm:contact_role:view
      description: View assigned roles for contacts on an account
      riskLevel: LOW
      applicableStories:
        - issue: "#172"
          title: "Maintain Contact Roles and Primary Flags"
      
    - name: crm:contact_role:assign
      description: Assign roles (BILLING, APPROVER, DRIVER) to contacts
      riskLevel: MEDIUM
      applicableStories:
        - issue: "#172"
          title: "Maintain Contact Roles and Primary Flags"
      notes: |
        - Atomic auto-demotion: setting new primary demotes prior primary
        - Validation: if invoiceDeliveryMethod=EMAIL, require billing contact with primary email
        - Per DECISION-INVENTORY-002
      
    - name: crm:contact_role:revoke
      description: Revoke a role assignment from a contact
      riskLevel: MEDIUM
      
  # Contact Communication Preferences
  contact_preference:
    - name: crm:contact_preference:view
      description: View communication preferences and consent flags for a party
      riskLevel: MEDIUM
      applicableStories:
        - issue: "#171"
          title: "Manage Communication Preferences & Consent Flags"
      notes: "Consent flags are sensitive; fine-grained permission separation from edit is intentional"
      
    - name: crm:contact_preference:edit
      description: Update communication channel preferences and consent for a party
      riskLevel: HIGH
      applicableStories:
        - issue: "#171"
          title: "Manage Communication Preferences & Consent Flags"
      notes: |
        - Optimistic locking required (version/ETag conflict on 409)
        - updateSource and audit trail per DECISION-INVENTORY-007
        - Consent model: stored on same DTO; no separate ConsentRecord entity (safe-to-defer)
```

### 3. Vehicle Management (Issue #169)

**Resource:** `vehicle`  
**System of Record:** CRM (vehicle master and party associations)  
**Related Decision:** DECISION-INVENTORY-001 (partyId association)

```yaml
  # Vehicle CRUD
  vehicle:
    - name: crm:vehicle:view
      description: View vehicle records (VIN, unit #, description, plate)
      riskLevel: LOW
      notes: "VIN/plate redaction applies per DECISION-INVENTORY-006 in certain contexts"
      
    - name: crm:vehicle:search
      description: Search vehicles by VIN, unit #, or plate
      riskLevel: LOW
      
    - name: crm:vehicle:create
      description: Create new vehicle record with VIN and optional party association
      riskLevel: MEDIUM
      applicableStories:
        - issue: "#169"
          title: "Create Vehicle Record (VIN, Unit #, Description, Optional Plate)"
      notes: |
        - VIN uniqueness scope: global (no scope per party)
        - duplicate VIN returns 409 with stable errorCode (e.g., DUPLICATE_VIN)
        - partyId association is optional at create; can be added later via relationship
      
    - name: crm:vehicle:edit
      description: Edit vehicle details (unit #, description, license plate, status)
      riskLevel: MEDIUM
      notes: "VIN is immutable after creation"
      
    - name: crm:vehicle:deactivate
      description: Deactivate vehicle record (soft delete)
      riskLevel: HIGH
      
  # Vehicle-Party Association
  vehicle_party_association:
    - name: crm:vehicle_party_association:create
      description: Associate party (owner/driver/lessee) to vehicle with effective dating
      riskLevel: MEDIUM
      notes: "Effective-dated relationship; multiple parties can associate at different times"
      
    - name: crm:vehicle_party_association:view
      description: View party associations for a vehicle (current and historical)
      riskLevel: LOW
      
    - name: crm:vehicle_party_association:edit
      description: Adjust effective dates for party-vehicle associations
      riskLevel: MEDIUM
      
  # Vehicle Care Preferences
  vehicle_preference:
    - name: crm:vehicle_preference:view
      description: View vehicle care preferences (rotation intervals, service types, notes)
      riskLevel: LOW
      
    - name: crm:vehicle_preference:edit
      description: Update vehicle care preferences with audit history and optimistic locking
      riskLevel: MEDIUM
      notes: "Fixed schema per DECISION-INVENTORY-012; versioning required for concurrency"
```

### 4. Integration Monitoring (CRM Operational Screens)

**Resource:** `integration` / `processing_log` / `suspense`  
**System of Record:** CRM (operational read models for event ingestion outcomes)  
**Related Decision:** DECISION-INVENTORY-005 (ProcessingLog/Suspense contracts)

```yaml
  # Integration Monitoring (Diagnostic/Troubleshooting)
  processing_log:
    - name: crm:processing_log:view
      description: View ingestion event processing outcomes (success/failure/retry state)
      riskLevel: MEDIUM
      notes: |
        - Read-only operational screen
        - Supports filtering: eventType, eventId, correlationIdPrefix, status, date range
        - Returned payload is sanitized per DECISION-INVENTORY-006
      
  suspense:
    - name: crm:suspense:view
      description: View quarantined/unprocessable events requiring triage
      riskLevel: MEDIUM
      notes: |
        - Read-only operational screen
        - Raw payload visibility default-deny; use payloadSummary and redactedPayload
        - Identified by suspenseId (not eventId as primary)
      
  integration:
    - name: crm:integration:audit
      description: View audit/attempt history for ingestion records and retry outcomes
      riskLevel: LOW
      notes: "Audit trail visible to authorized operators; retry metadata included if available"
```

---

## Risk Levels & Thresholds

| Risk Level | Characteristics | Approval Flow |
|------------|-----------------|---------------|
| LOW | Read-only, non-sensitive data | Self-service, audit log |
| MEDIUM | Mutation of business data, non-financial | Manager approval (may be implicit) |
| HIGH | Financial impact, regulatory, sensitive | Manager/Finance approval required |
| CRITICAL | Merger/acquisition data, audit trails, merge operations | Executive + Security approval |

**Notes:**

- LOW permissions suitable for field technician / CSR roles
- MEDIUM permissions suitable for manager / supervisor roles  
- HIGH permissions reserved for finance / ops management
- CRITICAL permissions restricted to executives and security admins

---

## Proposed Role Mappings

### Role: Customer Service Representative (CSR)

**Assigned Permissions:**

```
crm:party:view
crm:party:search
crm:contact:view
crm:contact:create
crm:contact:edit
crm:contact_role:view
crm:contact_role:assign
crm:contact_role:revoke
crm:contact_preference:view
crm:vehicle:view
crm:vehicle:search
crm:vehicle_party_association:view
crm:vehicle_preference:view
crm:processing_log:view
crm:suspense:view
crm:integration:audit
```

**Use Cases:**

- Manage customer contact information and role assignments
- View vehicle records and associate parties
- Monitor integration health for troubleshooting

---

### Role: Fleet Account Manager

**Assigned Permissions:**

```
crm:party:view
crm:party:search
crm:party:create
crm:party:edit
crm:contact:view
crm:contact:create
crm:contact:edit
crm:contact_role:view
crm:contact_role:assign
crm:contact_role:revoke
crm:contact_preference:view
crm:contact_preference:edit
crm:vehicle:view
crm:vehicle:search
crm:vehicle:create
crm:vehicle:edit
crm:vehicle_party_association:create
crm:vehicle_party_association:view
crm:vehicle_party_association:edit
crm:vehicle_preference:view
crm:vehicle_preference:edit
crm:processing_log:view
crm:suspense:view
crm:integration:audit
```

**Use Cases:**

- Create and maintain commercial accounts (parties)
- Create vehicle records and manage fleet associations
- Set vehicle care preferences
- Manage billing/approver/driver contact roles

---

### Role: CRM Administrator (Data Steward)

**Assigned Permissions:**

```
crm:party:view
crm:party:search
crm:party:create
crm:party:edit
crm:party:deactivate
crm:party:merge
crm:contact:view
crm:contact:create
crm:contact:edit
crm:contact:delete
crm:contact_role:view
crm:contact_role:assign
crm:contact_role:revoke
crm:contact_preference:view
crm:contact_preference:edit
crm:vehicle:view
crm:vehicle:search
crm:vehicle:create
crm:vehicle:edit
crm:vehicle:deactivate
crm:vehicle_party_association:create
crm:vehicle_party_association:view
crm:vehicle_party_association:edit
crm:vehicle_preference:view
crm:vehicle_preference:edit
crm:processing_log:view
crm:suspense:view
crm:integration:audit
```

**Use Cases:**

- Merge duplicate party records
- Deactivate obsolete vehicle records
- Full CRUD access to all CRM entities
- Data quality maintenance and troubleshooting

---

## Integration with Security Domain

### Registration Process

1. **Create permissions.yaml** in CRM service resources:

```bash
domains/crm/permissions.yaml
```

1. **Register during deployment** via Security Domain API:

```bash
POST /v1/permissions/register
Content-Type: application/yaml
[permissions manifest]
```

1. **Map permissions to roles** in Security domain role management:
   - Security Domain owns role definitions and permission mappings
   - CRM Domain documents suggested role→permission assignments (above)
   - Security Domain makes final authorization decisions

### Backend Enforcement

- **All CRM endpoints** must validate permissions via `@PreAuthorize` or equivalent
- **Fail-closed:** Missing permission returns `403 Forbidden` with `errorCode: FORBIDDEN`
- **Audit trail:** Every permission check logs to security audit trail

### Frontend Handling

- **Lazy evaluation:** UI loads user's permissions at login (via security domain endpoint)
- **Access denied:** Missing permission → show "Access Denied" screen (no partial data)
- **Entry point hiding:** Non-privileged users don't see buttons/links for inaccessible actions

---

## Mapping to Open Questions

| Open Question | Resolved By | Permission(s) |
|---|---|---|
| Issue #176 Q1: Permissions for "Create Commercial Account" | This taxonomy | `crm:party:create` |
| Issue #173 Q2: Permission key for party merge | This taxonomy | `crm:party:merge` |
| Issue #172 Q2: Permissions for viewing/updating role assignments | This taxonomy | `crm:contact_role:view`, `crm:contact_role:assign`, `crm:contact_role:revoke` |
| Issue #171 Q5: Permissions for viewing/editing communication preferences | This taxonomy | `crm:contact_preference:view`, `crm:contact_preference:edit` |
| Issue #169 Q4: Permission keys for vehicle creation/view | This taxonomy | `crm:vehicle:create`, `crm:vehicle:view`, `crm:vehicle:search` |

---

## Next Steps

1. **Security Domain Review:** Submit this taxonomy to security domain for architectural review and integration guidelines
2. **Backend Implementation:** Implement `@PreAuthorize` checks in pos-crm-service (when created) and Moqui service layer
3. **Frontend Integration:** Update Story Authoring Agent with exact permission keys for story specifications
4. **Role Configuration:** Create initial role definitions in security admin UI using proposed role mappings
5. **Documentation Update:** Link this taxonomy to crm-questions.txt as Priority 1 resolution

---

## Appendix: Environment-Specific Restrictions

**DECISION-INVENTORY-007** includes environment-based restrictions per crm-questions.txt Priority 1 context.

### Production Environment

- `crm:party:merge` requires executive + security approval (not auto-delegated)
- `crm:contact_preference:edit` limited to authorized CSRs (explicit allowlist)
- `crm:vehicle:deactivate` requires manager signature + audit

### Non-Production (Staging/UAT)

- All CRM permissions available to QA users for testing
- No executive approval required for `crm:party:merge`
- Audit trail still enforced for traceability

### Development Environment

- All CRM permissions available to developers
- Break-glass access available (DECISION-INVENTORY-007)
- Detailed error messages and stack traces in permission denial

---

## References

- **Baseline Permissions:** `/durion/domains/security/docs/BASELINE_PERMISSIONS.md`
- **Permission Registry:** `/durion/domains/security/docs/PERMISSION_REGISTRY.md`
- **CRM Questions:** `/durion/domains/crm/crm-questions.txt` (Priority 1)
- **CRM Domain Guide:** `/durion/domains/crm/.business-rules/AGENT_GUIDE.md` (DECISION-INVENTORY-007)
- **Backend Service:** `durion-positivity-backend/pos-security-service`
