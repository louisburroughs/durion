# Accounting Domain Permission Taxonomy

> **Purpose:** Define explicit permission keys for Accounting domain operations  
> **Domain:** accounting  
> **Owner:** Accounting Domain  
> **Version:** 1.0  
> **Status:** ACCEPTED
> **Date:** 2026-01-24

## Overview

This document defines the complete permission taxonomy required to implement Accounting domain stories. All permissions follow the standardized format: `domain:resource:action` per PERMISSION_REGISTRY.md.

These permissions resolve blocking questions from accounting-questions.txt and follow the precedent established by CRM domain (ADR 0002).

---

## Permission Naming Convention

All Accounting permissions follow the security domain standard:

- **Format:** `accounting:resource:action`
- **All lowercase**
- **Singular nouns for resources (except posting_rules)**
- **Three-part structure**

Examples: `accounting:coa:create`, `accounting:je:post`, `accounting:ap:approve`

---

## Accounting Permission Groups

### 1. Chart of Accounts (CoA) Management (Issue #204)

**Resource:** `coa`  
**System of Record:** Accounting (GL account master)  
**Related Decisions:** Foundation for all GL postings; account structure drives financial reporting

```yaml
domain: accounting
serviceName: pos-accounting-service
version: "1.0"

permissions:
  # Chart of Accounts
  - key: accounting:coa:view
    description: View GL account details
    risk_level: LOW
    use_cases:
      - View account list with derived status (Active/Inactive/NotYetActive)
      - View account details including activation windows
      - Search accounts by code, name, type
    required_for_stories:
      - Issue #204: CoA: Create and Maintain Chart of Accounts
      - Issue #205: GL Mapping (account lookup)
      - Issue #201: View JE line GL accounts

  - key: accounting:coa:create
    description: Create new GL accounts
    risk_level: MEDIUM
    use_cases:
      - Create GL accounts with code, name, type, activation dates
      - Validate account code uniqueness
    required_for_stories:
      - Issue #204: CoA: Create and Maintain Chart of Accounts

  - key: accounting:coa:edit
    description: Edit existing GL accounts
    risk_level: MEDIUM
    use_cases:
      - Update account name, description, activation windows
      - Modify account structure (type, parent)
      - Enforce immutability rules for inactive accounts (if policy)
    required_for_stories:
      - Issue #204: CoA: Create and Maintain Chart of Accounts

  - key: accounting:coa:deactivate
    description: Deactivate GL accounts
    risk_level: HIGH
    use_cases:
      - Mark accounts inactive (effective-date deactivation)
      - Validate no outstanding balance before deactivation
      - Validate no active mappings reference account
    required_for_stories:
      - Issue #204: CoA: Create and Maintain Chart of Accounts
```

---

### 2. GL Mapping Taxonomy (Issue #205)

**Resource:** `mapping`  
**System of Record:** Accounting (posting category, mapping keys, GL mappings)  
**Related Decisions:** Effective-dated mappings; overlap detection required

```yaml
  # GL Mapping Management
  - key: accounting:mapping:view
    description: View posting categories, mapping keys, and GL mappings
    risk_level: LOW
    use_cases:
      - View posting category hierarchy
      - View mapping keys per category
      - View GL mappings with effective dates and dimensions
      - View resolution test results (optional endpoint)
    required_for_stories:
      - Issue #205: Mapping: Configure GL Posting Taxonomy

  - key: accounting:mapping:create
    description: Create posting categories, mapping keys, and GL mappings
    risk_level: MEDIUM
    use_cases:
      - Create new posting categories
      - Create mapping keys within categories
      - Create GL mappings with effective dates and dimensions
      - Validate mapping overlap detection (409 on conflict)
    required_for_stories:
      - Issue #205: Mapping: Configure GL Posting Taxonomy

  - key: accounting:mapping:edit
    description: Edit posting categories and mapping keys
    risk_level: MEDIUM
    use_cases:
      - Update posting category metadata (if mutable per policy)
      - Update mapping key metadata (if mutable per policy)
      - Note: GL mappings are append-only (create new effective-dated row)
    required_for_stories:
      - Issue #205: Mapping: Configure GL Posting Taxonomy

  - key: accounting:mapping:deactivate
    description: Deactivate posting categories and mapping keys
    risk_level: HIGH
    use_cases:
      - End-date posting categories (validate no active mappings)
      - End-date mapping keys (validate no active mappings)
      - Prevent deactivation if referenced by active GL mappings
    required_for_stories:
      - Issue #205: Mapping: Configure GL Posting Taxonomy

  - key: accounting:mapping:test_resolution
    description: Test mapping resolution for validation
    risk_level: LOW
    use_cases:
      - Submit test queries (mappingKey + transactionDate)
      - View resolved postingCategory, glAccount, dimensions
      - Troubleshoot mapping gaps before go-live
    required_for_stories:
      - Issue #205: Optional resolution test endpoint
```

---

### 3. Posting Rule Sets (Issue #202)

**Resource:** `posting_rules`  
**System of Record:** Accounting (event type → posting rules versioning)  
**Related Decisions:** Immutable published versions; state machine (DRAFT → PUBLISHED → ARCHIVED)

```yaml
  # Posting Rule Set Management
  - key: accounting:posting_rules:view
    description: View posting rule sets and versions
    risk_level: LOW
    use_cases:
      - List posting rule sets with latest version summary
      - View specific rule set version details
      - View rules definition JSON (read-only)
      - View state (DRAFT, PUBLISHED, ARCHIVED)
    required_for_stories:
      - Issue #202: Mapping: Configure EventType → Posting Rule Set

  - key: accounting:posting_rules:create
    description: Create posting rule sets and new versions
    risk_level: MEDIUM
    use_cases:
      - Create new posting rule set (creates v1 in DRAFT)
      - Create new version from base version (clone and increment)
      - Edit rules definition JSON in DRAFT versions
      - Validate JSON schema compliance
    required_for_stories:
      - Issue #202: Mapping: Configure EventType → Posting Rule Set

  - key: accounting:posting_rules:publish
    description: Publish posting rule set versions
    risk_level: HIGH
    use_cases:
      - Transition version from DRAFT → PUBLISHED
      - Validate balance (debits == credits per condition)
      - Make version available for JE generation
      - Prevent edits to published versions (immutable)
    required_for_stories:
      - Issue #202: Mapping: Configure EventType → Posting Rule Set

  - key: accounting:posting_rules:archive
    description: Archive posting rule set versions
    risk_level: HIGH
    use_cases:
      - Transition version from PUBLISHED → ARCHIVED
      - Remove version from active use (JE generation)
      - Retain for audit trail
      - Optionally allow as base for new versions (per policy)
    required_for_stories:
      - Issue #202: Mapping: Configure EventType → Posting Rule Set
```

---

### 4. Journal Entries (Issue #201)

**Resource:** `je`  
**System of Record:** Accounting (general ledger transactions)  
**Related Decisions:** Immutable after posting; balanced entries required; dimension tracking

```yaml
  # Journal Entry Management
  - key: accounting:je:view
    description: View journal entries and line details
    risk_level: LOW
    use_cases:
      - View JE list with filters (date range, status, source event)
      - View JE details with all lines
      - View dimension breakdowns per line
      - View traceability (source event → JE → GL accounts)
    required_for_stories:
      - Issue #201: GL: Build Balanced Journal Entry from Event
      - Issue #206: AP: View Vendor Bill with Traceability

  - key: accounting:je:create
    description: Build journal entries from events
    risk_level: MEDIUM
    use_cases:
      - Trigger JE generation from source event ID
      - Apply posting rules to generate balanced JE
      - Create JE in DRAFT/PENDING state
      - Validate balance (sum debits == sum credits)
    required_for_stories:
      - Issue #201: GL: Build Balanced Journal Entry from Event

  - key: accounting:je:post
    description: Post journal entries to GL
    risk_level: HIGH
    use_cases:
      - Mark JE as POSTED (immutable)
      - Update GL account balances
      - Prevent edits after posting
      - Generate audit trail entry
    required_for_stories:
      - Issue #201: GL: Build Balanced Journal Entry from Event

  - key: accounting:je:reverse
    description: Reverse posted journal entries
    risk_level: HIGH
    use_cases:
      - Create reversing JE with justification
      - Link original JE ↔ reversing JE
      - Post reversing JE to GL
      - Audit trail for reversal reasons
    required_for_stories:
      - Issue #201: GL: Build Balanced Journal Entry from Event (optional)

  - key: accounting:je:view_suspense
    description: View journal entries routed to suspense
    risk_level: LOW
    use_cases:
      - View JEs with mapping failures
      - View suspense routing policy
      - Troubleshoot posting rule gaps
    required_for_stories:
      - Issue #201: Optional suspense view
```

---

### 5. Accounts Payable (AP) Operations (Issue #206)

**Resource:** `ap`  
**System of Record:** Accounting (vendor bill lifecycle and payment tracking)  
**Related Decisions:** Status-driven workflow; traceability to origin events and JEs

```yaml
  # AP Management
  - key: accounting:ap:view
    description: View vendor bills and payment details
    risk_level: LOW
    use_cases:
      - View vendor bill list with status filter (PENDING_REVIEW, APPROVED, REJECTED, PAID)
      - View vendor bill details with line items
      - View traceability (origin event → vendor bill → JE → payments)
      - View vendor information (from People domain)
    required_for_stories:
      - Issue #206: AP: View Vendor Bill with Traceability

  - key: accounting:ap:approve
    description: Approve vendor bills for payment
    risk_level: HIGH
    use_cases:
      - Transition bill from PENDING_REVIEW → APPROVED
      - Add approval justification/notes
      - Generate audit trail entry
      - Trigger payment workflow (if integrated)
    required_for_stories:
      - Issue #206: AP: View Vendor Bill with Traceability

  - key: accounting:ap:reject
    description: Reject vendor bills
    risk_level: MEDIUM
    use_cases:
      - Transition bill from PENDING_REVIEW → REJECTED
      - Require rejection reason/justification
      - Generate audit trail entry
      - Prevent payment processing
    required_for_stories:
      - Issue #206: AP: View Vendor Bill with Traceability

  - key: accounting:ap:pay
    description: Mark vendor bills as paid
    risk_level: HIGH
    use_cases:
      - Transition bill from APPROVED → PAID
      - Link to payment transaction ID
      - Update vendor payment history
      - Generate audit trail entry
    required_for_stories:
      - Issue #206: AP: View Vendor Bill with Traceability (optional for MVP)
```

---

### 6. Event Ingestion (Issue #207)

**Resource:** `events`  
**System of Record:** Accounting (canonical event ingestion and acknowledgement)  
**Related Decisions:** Sync endpoint for ops tools; duplicate detection required

```yaml
  # Event Ingestion
  - key: accounting:events:submit
    description: Submit accounting events via sync endpoint
    risk_level: MEDIUM
    use_cases:
      - Submit canonical event (eventId, eventType, transactionDate, payload)
      - Validate payload JSON structure
      - Receive acknowledgement (eventId, status, receivedAt, sequenceNumber)
      - Detect duplicate event IDs (409 on conflict)
    required_for_stories:
      - Issue #207: Events: Receive Events via Queue and/or Service Endpoint

  - key: accounting:events:view
    description: View submitted accounting events
    risk_level: LOW
    use_cases:
      - View event list with filters (eventType, date range, status)
      - View event details including payload
      - View processing status (RECEIVED, PROCESSED, FAILED)
      - View linked JEs (if processed)
    required_for_stories:
      - Issue #207: Events: Receive Events via Queue and/or Service Endpoint

  - key: accounting:events:retry
    description: Retry failed event processing
    risk_level: MEDIUM
    use_cases:
      - Retry JE generation for failed events
      - View retry history and outcomes
      - Escalate to suspense if retries exhausted
    required_for_stories:
      - Issue #207: Optional retry capability
```

---

## Risk Levels & Thresholds

| Risk Level | Characteristics | Approval Flow |
|------------|-----------------|---------------|
| LOW | Read-only operations; no data mutation; no financial impact | Self-service, audit log |
| MEDIUM | Mutation of configuration data; non-posted transactions; reversible | Manager approval (may be implicit for trusted roles) |
| HIGH | Financial mutation; posting to GL; payment approvals; irreversible actions | Manager/Controller approval required |
| CRITICAL | GL period close; audit trail deletion; system-wide configuration (future) | Executive + Finance approval |

**Notes:**

- LOW permissions suitable for analysts, auditors, read-only roles
- MEDIUM permissions suitable for accountants, AP clerks, GL analysts
- HIGH permissions reserved for controllers, finance managers, senior accountants
- CRITICAL permissions (not yet defined) would be restricted to CFO, finance executives

**Financial Impact Thresholds:**

- Posting JE to GL: HIGH (affects financial statements)
- Approving vendor bills: HIGH (commits to payment)
- Creating/editing mappings: MEDIUM (affects future postings but reversible)
- Creating posting rules: MEDIUM (DRAFT state; no financial impact until published)
- Publishing posting rules: HIGH (activates rules for JE generation)

---

## Proposed Role Mappings

### Role: AP Clerk (Accounts Payable Clerk)

**Assigned Permissions:**

```
accounting:ap:view
accounting:ap:reject
accounting:events:view
accounting:je:view
accounting:coa:view
```

**Use Cases:**

- Review vendor bills awaiting approval
- Reject bills with errors (incorrect amounts, missing documentation)
- View traceability (bill → event → JE)
- Cannot approve bills for payment (requires higher authority)

**Permission Count:** 5

---

### Role: AR Clerk (Accounts Receivable Clerk)

**Assigned Permissions:**

```
accounting:ap:view
accounting:events:view
accounting:je:view
accounting:coa:view
```

**Use Cases:**

- View vendor bill status for coordination
- View accounting events and journal entries
- Cannot approve or pay bills (AP-specific)

**Permission Count:** 4

**Note:** AR-specific permissions (customer invoices, collections) will be added in future iterations when AR stories are defined.

---

### Role: GL Analyst (General Ledger Analyst)

**Assigned Permissions:**

```
accounting:coa:view
accounting:coa:create
accounting:coa:edit
accounting:mapping:view
accounting:mapping:create
accounting:mapping:edit
accounting:mapping:test_resolution
accounting:posting_rules:view
accounting:posting_rules:create
accounting:je:view
accounting:je:create
accounting:je:view_suspense
accounting:events:view
accounting:events:submit
accounting:ap:view
```

**Use Cases:**

- Maintain chart of accounts (create/edit, but not deactivate)
- Configure GL mappings and posting categories
- Create and edit posting rule sets (DRAFT only)
- Build JEs from events (but not post to GL)
- Test mapping resolution for validation
- Submit test events for integration testing

**Permission Count:** 15

---

### Role: Accountant (Staff Accountant)

**Assigned Permissions:**

```
accounting:coa:view
accounting:coa:create
accounting:coa:edit
accounting:coa:deactivate
accounting:mapping:view
accounting:mapping:create
accounting:mapping:edit
accounting:mapping:deactivate
accounting:mapping:test_resolution
accounting:posting_rules:view
accounting:posting_rules:create
accounting:je:view
accounting:je:create
accounting:je:post
accounting:je:view_suspense
accounting:events:view
accounting:events:submit
accounting:events:retry
accounting:ap:view
accounting:ap:approve
accounting:ap:reject
```

**Use Cases:**

- Full CoA management including deactivation
- Full GL mapping management
- Create and edit posting rules (but not publish)
- Post JEs to GL (high-impact operation)
- Approve vendor bills for payment
- Retry failed events

**Permission Count:** 20

---

### Role: Controller (Finance Controller)

**Assigned Permissions:**

```
accounting:coa:view
accounting:coa:create
accounting:coa:edit
accounting:coa:deactivate
accounting:mapping:view
accounting:mapping:create
accounting:mapping:edit
accounting:mapping:deactivate
accounting:mapping:test_resolution
accounting:posting_rules:view
accounting:posting_rules:create
accounting:posting_rules:publish
accounting:posting_rules:archive
accounting:je:view
accounting:je:create
accounting:je:post
accounting:je:reverse
accounting:je:view_suspense
accounting:events:view
accounting:events:submit
accounting:events:retry
accounting:ap:view
accounting:ap:approve
accounting:ap:reject
accounting:ap:pay
```

**Use Cases:**

- All Accountant permissions
- Publish posting rule sets (activates rules for production)
- Archive posting rule sets
- Reverse posted JEs (correcting entries)
- Mark vendor bills as paid (payment confirmation)
- Full authority over accounting operations

**Permission Count:** 25

---

### Role: Accounting Administrator (System Admin for Accounting)

**Assigned Permissions:**

```
(All Controller permissions)
```

**Use Cases:**

- All Controller permissions
- System configuration and troubleshooting
- Future: Period close, audit trail access, system-wide settings

**Permission Count:** 25 (same as Controller for now; future enhancements may add admin-specific permissions)

---

## Permission Hierarchy Visualization

```
AP_CLERK (5 perms)
  ↓
AR_CLERK (4 perms)
  ↓
GL_ANALYST (15 perms: adds CoA/Mapping/Rules CRUD)
  ↓
ACCOUNTANT (20 perms: adds deactivate, post, approve)
  ↓
CONTROLLER (25 perms: adds publish, archive, reverse, pay)
  ↓
ADMIN (25 perms: same as Controller + future admin capabilities)
```

**Design Note:** Roles build hierarchically; higher roles include all lower permissions plus additional authority.

---

## Integration with Security Domain

### Registration Process

1. **Create permissions.yaml** in Accounting service resources:

```bash
domains/accounting/permissions.yaml
```

2. **Register during deployment** via Security Domain API:

```bash
POST /v1/permissions/register
Content-Type: application/yaml

domain: accounting
serviceName: pos-accounting-service
version: "1.0"
permissions:
  - accounting:coa:view
  - accounting:coa:create
  # ... (all permissions)
```

3. **Implement RoleAuthorityService mappings:**

```java
// pos-security-service/service/RoleAuthorityService.java
public Set<String> expandRolesToAuthorities(Set<String> roles) {
    Set<String> authorities = new HashSet<>(roles);
    
    // Accounting role expansions
    if (roles.contains("AP_CLERK")) {
        authorities.addAll(Arrays.asList(
            "accounting:ap:view",
            "accounting:ap:reject",
            "accounting:events:view",
            "accounting:je:view",
            "accounting:coa:view"
        ));
    }
    
    if (roles.contains("GL_ANALYST")) {
        authorities.addAll(Arrays.asList(
            "accounting:coa:view", "accounting:coa:create", "accounting:coa:edit",
            "accounting:mapping:view", "accounting:mapping:create", "accounting:mapping:edit",
            "accounting:mapping:test_resolution",
            "accounting:posting_rules:view", "accounting:posting_rules:create",
            "accounting:je:view", "accounting:je:create", "accounting:je:view_suspense",
            "accounting:events:view", "accounting:events:submit",
            "accounting:ap:view"
        ));
    }
    
    if (roles.contains("ACCOUNTANT")) {
        // Include all GL_ANALYST permissions
        authorities.addAll(getGLAnalystPermissions());
        // Add additional permissions
        authorities.addAll(Arrays.asList(
            "accounting:coa:deactivate",
            "accounting:mapping:deactivate",
            "accounting:je:post",
            "accounting:events:retry",
            "accounting:ap:approve",
            "accounting:ap:reject"
        ));
    }
    
    if (roles.contains("CONTROLLER")) {
        // Include all ACCOUNTANT permissions
        authorities.addAll(getAccountantPermissions());
        // Add additional permissions
        authorities.addAll(Arrays.asList(
            "accounting:posting_rules:publish",
            "accounting:posting_rules:archive",
            "accounting:je:reverse",
            "accounting:ap:pay"
        ));
    }
    
    // ... (CRM and other domain expansions)
    
    return authorities;
}
```

4. **Verify JWT tokens carry expanded authorities:**

```bash
# JWT payload should include all expanded permissions
{
  "sub": "user123",
  "roles": ["ACCOUNTANT"],
  "authorities": [
    "ROLE_ACCOUNTANT",
    "accounting:coa:view",
    "accounting:coa:create",
    # ... (all 20 accountant permissions)
  ]
}
```

5. **Implement controller enforcement:**

```java
// pos-accounting/controller/GLAccountController.java
@RestController
@RequestMapping("/v1/accounting/gl-accounts")
public class GLAccountController {
    
    @GetMapping
    @PreAuthorize("hasAuthority('accounting:coa:view')")
    public ResponseEntity<Page<GLAccountDTO>> listGLAccounts(...) {
        // ...
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('accounting:coa:create')")
    public ResponseEntity<GLAccountDTO> createGLAccount(@RequestBody GLAccountDTO dto) {
        // ...
    }
    
    @PutMapping("/{glAccountId}")
    @PreAuthorize("hasAuthority('accounting:coa:edit')")
    public ResponseEntity<GLAccountDTO> updateGLAccount(...) {
        // ...
    }
    
    @DeleteMapping("/{glAccountId}")
    @PreAuthorize("hasAuthority('accounting:coa:deactivate')")
    public ResponseEntity<Void> deactivateGLAccount(@PathVariable String glAccountId) {
        // ...
    }
}
```

---

## Frontend Permission Usage

### Moqui Service Wrappers

All Moqui service wrappers in `durion-accounting` component must annotate required permissions:

```xml
<!-- AccountingRestServices.xml -->
<service verb="list" noun="GLAccounts" type="interface">
    <description>
        List GL accounts with filters and pagination.
        Required permission: accounting:coa:view
    </description>
    <in-parameters>
        <parameter name="status" type="String"/>
        <parameter name="pageSize" type="Integer" default="20"/>
        <parameter name="pageNumber" type="Integer" default="1"/>
    </in-parameters>
    <out-parameters>
        <parameter name="accounts" type="List"/>
        <parameter name="totalCount" type="Long"/>
    </out-parameters>
</service>

<service verb="create" noun="GLAccount" type="interface">
    <description>
        Create new GL account.
        Required permission: accounting:coa:create
    </description>
    <in-parameters>
        <parameter name="accountCode" required="true"/>
        <parameter name="accountName" required="true"/>
        <parameter name="accountType" required="true"/>
        <parameter name="activationDate" type="Date"/>
    </in-parameters>
    <out-parameters>
        <parameter name="glAccountId" type="String"/>
    </out-parameters>
</service>
```

### Screen-Level Permission Checks

Frontend screens should use permission checks for UI rendering:

```xml
<!-- GLAccountList.screen.xml -->
<screen>
    <actions>
        <set field="hasViewPermission" from="ec.user.hasPermission('accounting:coa:view')"/>
        <set field="hasCreatePermission" from="ec.user.hasPermission('accounting:coa:create')"/>
    </actions>
    <widgets>
        <container-box>
            <box-header title="GL Accounts"/>
            <box-toolbar>
                <link url="createGLAccount" text="Create Account" 
                      condition="hasCreatePermission"/>
            </box-toolbar>
            <box-body>
                <!-- Account list display -->
            </box-body>
        </container-box>
    </widgets>
</screen>
```

---

## Audit Requirements

All accounting operations require comprehensive audit trails:

### Audit Dimensions

- **User:** Who performed the action (userId, username, roles)
- **Timestamp:** When the action occurred (ISO 8601 UTC)
- **Operation:** What action was performed (create, edit, post, approve, etc.)
- **Entity:** What entity was affected (GLAccount, JE, VendorBill, etc.)
- **Entity ID:** The specific record ID
- **Old Values:** Previous state (for updates)
- **New Values:** New state (for creates/updates)
- **Justification:** User-provided reason (required for high-risk operations)
- **IP Address:** Client IP for security tracking
- **Trace ID:** W3C trace context for correlation

### High-Risk Operations Requiring Justification

- Posting JE to GL (`accounting:je:post`)
- Reversing posted JE (`accounting:je:reverse`)
- Approving vendor bills (`accounting:ap:approve`)
- Deactivating GL accounts (`accounting:coa:deactivate`)
- Publishing posting rule sets (`accounting:posting_rules:publish`)

### Audit Retention

- **Financial transactions:** 7 years (regulatory compliance)
- **Configuration changes:** 3 years
- **Access logs:** 1 year

---

## Testing Strategy

### Permission Enforcement Tests

1. **Unit Tests:** Verify @PreAuthorize annotations on all controller methods
2. **Integration Tests:** Test role-based access with JWT tokens
3. **Negative Tests:** Confirm 403 responses for unauthorized requests
4. **Role Coverage:** Test all role combinations (AP_CLERK, GL_ANALYST, Accountant, Controller)

### Test Scenarios

```java
@Test
public void testGLAnalystCannotPostJE() {
    // Given: User with GL_ANALYST role
    String token = generateTokenForRole("GL_ANALYST");
    
    // When: Attempt to post JE
    ResponseEntity<?> response = postJE(token, jeId);
    
    // Then: Expect 403 Forbidden
    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    assertTrue(response.getBody().toString().contains("accounting:je:post"));
}

@Test
public void testAccountantCanPostJE() {
    // Given: User with ACCOUNTANT role
    String token = generateTokenForRole("ACCOUNTANT");
    
    // When: Post JE
    ResponseEntity<?> response = postJE(token, jeId);
    
    // Then: Expect 200 OK
    assertEquals(HttpStatus.OK, response.getStatusCode());
}
```

---

---

### 7. Posting Categories and Mapping Keys (Issue #203)

**Resource:** `posting_config`  
**System of Record:** Accounting (posting category taxonomy and mapping key registry)  
**Related Decisions:** GL mappings effective-dated; overlap detection; dimension schema enforcement

```yaml
  # Posting Configuration Management
  - key: accounting:posting_config:view
    description: View posting categories, mapping keys, and GL mappings
    risk_level: LOW
    use_cases:
      - View posting category hierarchy and metadata
      - View mapping keys per category with 1:1 link
      - View GL mappings with effective dates and dimension values
      - Search by category code, mapping key, GL account
    required_for_stories:
      - Issue #203: Categories: Define Posting Categories and Mapping Keys

  - key: accounting:posting_config:manage
    description: Create and update posting configuration (categories, keys, mappings)
    risk_level: MEDIUM
    use_cases:
      - Create posting categories with unique codes
      - Create mapping keys with 1:1 link to categories
      - Create GL mappings with effective dates and dimensions
      - Validate overlap detection (409 on conflict)
      - End-date categories and keys (deactivate)
    required_for_stories:
      - Issue #203: Categories: Define Posting Categories and Mapping Keys

  - key: accounting:posting_config:test_resolution
    description: Test mapping resolution for validation
    risk_level: LOW
    use_cases:
      - Submit test queries (mappingKey + transactionDate)
      - View resolved postingCategory, glAccount, dimensions
      - Troubleshoot mapping gaps before go-live
    required_for_stories:
      - Issue #203: Optional resolution test endpoint
```

---

### 8. Vendor Bill Management (Issue #194)

**Resource:** `vendor_bill`  
**System of Record:** Accounting (AP lifecycle from event to payment)  
**Related Decisions:** Status-driven workflow; traceability to origin events and posting references

```yaml
  # Vendor Bill Operations
  - key: accounting:vendor_bill:view
    description: View vendor bills and traceability
    risk_level: LOW
    use_cases:
      - View vendor bill list with status filters
      - View vendor bill details with line items
      - View traceability (origin event → vendor bill → JE → payments)
      - View vendor information (People domain integration)
    required_for_stories:
      - Issue #194: AP: Create Vendor Bill from Event

  - key: accounting:vendor_bill:create
    description: Create vendor bills from events
    risk_level: MEDIUM
    use_cases:
      - Create vendor bill from origin event (WorkCompleted, PurchaseOrder, etc.)
      - Set bill amount, due date, vendor reference
      - Validate vendor exists (People domain)
      - Create in PENDING_REVIEW status
    required_for_stories:
      - Issue #194: AP: Create Vendor Bill from Event

  - key: accounting:vendor_bill:edit
    description: Edit vendor bills in PENDING_REVIEW status
    risk_level: MEDIUM
    use_cases:
      - Update bill amount, due date, notes (before approval)
      - Cannot edit after approval (immutable)
      - Validate status transition rules
    required_for_stories:
      - Issue #194: AP: Create Vendor Bill from Event

  - key: accounting:vendor_bill:approve
    description: Approve vendor bills for payment
    risk_level: HIGH
    use_cases:
      - Transition bill from PENDING_REVIEW → APPROVED
      - Require approval justification/notes
      - Generate audit trail entry
      - Trigger payment workflow (if integrated)
    required_for_stories:
      - Issue #194: AP: Create Vendor Bill from Event

  - key: accounting:vendor_bill:reject
    description: Reject vendor bills
    risk_level: MEDIUM
    use_cases:
      - Transition bill from PENDING_REVIEW → REJECTED
      - Require rejection reason/justification
      - Generate audit trail entry
      - Prevent payment processing
    required_for_stories:
      - Issue #194: AP: Create Vendor Bill from Event
```

---

### 9. AP Approval and Payment Scheduling (Issue #193)

**Resource:** `ap_payment`  
**System of Record:** Accounting (payment workflow and scheduling)  
**Related Decisions:** Approval threshold policies; payment method constraints; audit trail requirements

```yaml
  # AP Payment Workflow
  - key: accounting:ap_payment:view
    description: View payment schedules and approval queues
    risk_level: LOW
    use_cases:
      - View bills awaiting approval with amount thresholds
      - View scheduled payments by date range
      - View payment history for vendor
      - View approval audit trail
    required_for_stories:
      - Issue #193: AP: Approve and Schedule Payments

  - key: accounting:ap_payment:approve_under_threshold
    description: Approve payments under threshold (e.g., $10,000)
    risk_level: MEDIUM
    use_cases:
      - Approve vendor bills under organization threshold
      - Add approval notes and justification
      - Single-approver sufficient
      - Generate audit trail entry
    required_for_stories:
      - Issue #193: AP: Approve and Schedule Payments

  - key: accounting:ap_payment:approve_over_threshold
    description: Approve payments over threshold (requires higher authority)
    risk_level: HIGH
    use_cases:
      - Approve vendor bills over organization threshold
      - May require dual approval (if policy)
      - Add approval notes and justification
      - Generate audit trail entry with approver details
    required_for_stories:
      - Issue #193: AP: Approve and Schedule Payments

  - key: accounting:ap_payment:schedule
    description: Schedule approved payments
    risk_level: HIGH
    use_cases:
      - Set payment date and method (ACH, Check, Wire)
      - Link to bank account for payment processing
      - Generate payment batch for export
      - Cannot schedule unapproved bills
    required_for_stories:
      - Issue #193: AP: Approve and Schedule Payments

  - key: accounting:ap_payment:cancel
    description: Cancel scheduled payments
    risk_level: HIGH
    use_cases:
      - Cancel payment before execution
      - Require cancellation reason/justification
      - Revert bill to APPROVED status
      - Generate audit trail entry
    required_for_stories:
      - Issue #193: AP: Approve and Schedule Payments
```

---

### 10. Manual Journal Entry (Issue #190)

**Resource:** `manual_je`  
**System of Record:** Accounting (manual GL adjustments with controls)  
**Related Decisions:** Balance validation; reason codes required; draft vs post workflow

```yaml
  # Manual Journal Entry Operations
  - key: accounting:manual_je:view
    description: View manual journal entries
    risk_level: LOW
    use_cases:
      - View manual JE list with filters (date range, status, reason)
      - View JE details with all lines
      - View audit trail (who created, who posted)
      - Distinguish manual from event-driven JEs
    required_for_stories:
      - Issue #190: GL: Manual Journal Entry with Controls

  - key: accounting:manual_je:create_draft
    description: Create manual journal entries in DRAFT state
    risk_level: MEDIUM
    use_cases:
      - Create JE with description and reason code
      - Add JE lines (account, debit, credit, dimensions)
      - Validate balance (sum debits == sum credits)
      - Save in DRAFT state (editable)
    required_for_stories:
      - Issue #190: GL: Manual Journal Entry with Controls

  - key: accounting:manual_je:edit_draft
    description: Edit manual journal entries in DRAFT state
    risk_level: MEDIUM
    use_cases:
      - Modify JE lines, amounts, dimensions
      - Update reason code and description
      - Revalidate balance after changes
      - Cannot edit after posting (immutable)
    required_for_stories:
      - Issue #190: GL: Manual Journal Entry with Controls

  - key: accounting:manual_je:post
    description: Post manual journal entries to GL
    risk_level: HIGH
    use_cases:
      - Validate balance (sum debits == sum credits)
      - Transition from DRAFT → POSTED (immutable)
      - Update GL account balances
      - Generate audit trail entry with justification
    required_for_stories:
      - Issue #190: GL: Manual Journal Entry with Controls

  - key: accounting:manual_je:reverse
    description: Reverse posted manual journal entries
    risk_level: HIGH
    use_cases:
      - Create reversing JE with justification
      - Link original JE ↔ reversing JE
      - Post reversing JE to GL
      - Audit trail for reversal reasons
    required_for_stories:
      - Issue #190: GL: Manual Journal Entry with Controls

  - key: accounting:manual_je:delete_draft
    description: Delete manual journal entries in DRAFT state
    risk_level: MEDIUM
    use_cases:
      - Delete draft JE before posting
      - Require deletion reason/justification
      - Cannot delete posted JEs (use reverse instead)
      - Generate audit trail entry
    required_for_stories:
      - Issue #190: GL: Manual Journal Entry with Controls
```

---

### 11. Bank and Cash Reconciliation (Issue #187)

**Resource:** `reconciliation`  
**System of Record:** Accounting (bank/cash statement matching and adjustments)  
**Related Decisions:** Import format standards; matching cardinality; adjustment posting

```yaml
  # Reconciliation Operations
  - key: accounting:reconciliation:view
    description: View reconciliation sessions and status
    risk_level: LOW
    use_cases:
      - View reconciliation list by account and period
      - View reconciliation details (matched, unmatched, adjustments)
      - View statement import history
      - View reconciliation audit trail
    required_for_stories:
      - Issue #187: Reconciliation: Bank and Cash Reconciliation

  - key: accounting:reconciliation:import_statement
    description: Import bank/cash statements
    risk_level: MEDIUM
    use_cases:
      - Upload statement files (CSV, OFX, QBO formats)
      - Parse statement lines with date, amount, description
      - Validate import format and data quality
      - Create reconciliation session
    required_for_stories:
      - Issue #187: Reconciliation: Bank and Cash Reconciliation

  - key: accounting:reconciliation:match
    description: Match transactions to GL entries
    risk_level: MEDIUM
    use_cases:
      - Auto-match by amount, date, reference
      - Manual match statement lines to GL transactions
      - Many-to-many matching support (if policy)
      - Mark matches as confirmed
    required_for_stories:
      - Issue #187: Reconciliation: Bank and Cash Reconciliation

  - key: accounting:reconciliation:create_adjustment
    description: Create reconciliation adjustments
    risk_level: HIGH
    use_cases:
      - Create adjustment JE for unmatched items
      - Select adjustment type (bank fee, interest, error correction)
      - Link adjustment to reconciliation session
      - Post adjustment to GL
    required_for_stories:
      - Issue #187: Reconciliation: Bank and Cash Reconciliation

  - key: accounting:reconciliation:finalize
    description: Finalize reconciliation session
    risk_level: HIGH
    use_cases:
      - Validate all items matched or adjusted
      - Lock reconciliation (immutable)
      - Update account balances
      - Generate reconciliation report
    required_for_stories:
      - Issue #187: Reconciliation: Bank and Cash Reconciliation

  - key: accounting:reconciliation:reopen
    description: Reopen finalized reconciliation
    risk_level: HIGH
    use_cases:
      - Reopen locked reconciliation for corrections
      - Require justification and authorization
      - Reverse finalization effects
      - Generate audit trail entry
    required_for_stories:
      - Issue #187: Reconciliation: Bank and Cash Reconciliation (optional)
```

---

### 12. WorkCompleted Event Ingestion (Issue #183)

**Resource:** `workcompleted_events`  
**System of Record:** Accounting (WorkExec domain event ingestion and processing)  
**Related Decisions:** Retry policies; status model; operator reason capture; payload visibility

```yaml
  # WorkCompleted Event Operations
  - key: accounting:workcompleted_events:view
    description: View WorkCompleted event ingestion status
    risk_level: LOW
    use_cases:
      - View event list with filters (status, date range, workorderId)
      - View event details including payload (if authorized per AD-009)
      - View processing status (RECEIVED, PROCESSED, FAILED, SUSPENDED)
      - View linked JEs and vendor bills
    required_for_stories:
      - Issue #183: Events: WorkCompleted Event Ingestion

  - key: accounting:workcompleted_events:submit
    description: Submit WorkCompleted events
    risk_level: MEDIUM
    use_cases:
      - Submit canonical WorkCompleted event
      - Validate payload structure per event schema
      - Receive acknowledgement (eventId, status, sequenceNumber)
      - Detect duplicate event IDs (409 on conflict)
    required_for_stories:
      - Issue #183: Events: WorkCompleted Event Ingestion

  - key: accounting:workcompleted_events:retry
    description: Retry failed WorkCompleted event processing
    risk_level: MEDIUM
    use_cases:
      - Retry JE generation for failed events
      - View retry history and outcomes (attempt count, last error)
      - Escalate to suspense if retries exhausted
      - Require operator reason for manual retry
    required_for_stories:
      - Issue #183: Events: WorkCompleted Event Ingestion

  - key: accounting:workcompleted_events:suspend
    description: Manually suspend WorkCompleted events
    risk_level: MEDIUM
    use_cases:
      - Suspend event processing for investigation
      - Require suspension reason/justification
      - Prevent auto-retry during suspension
      - Generate audit trail entry
    required_for_stories:
      - Issue #183: Events: WorkCompleted Event Ingestion

  - key: accounting:workcompleted_events:view_payload
    description: View raw WorkCompleted event payload
    risk_level: MEDIUM
    use_cases:
      - Access raw JSON payload for troubleshooting
      - Permission-gated per AD-009 (Payload Visibility)
      - View sensitive financial data (labor costs, part costs)
      - Audit access to payload
    required_for_stories:
      - Issue #183: Events: WorkCompleted Event Ingestion
```

---

## Future Enhancements

### Phase 2 Permissions (Deferred)

- **AR Operations:** `accounting:ar:*` (customer invoices, collections, aging reports)
- **Period Close:** `accounting:period:close`, `accounting:period:reopen` (month-end/year-end)
- **Budget Management:** `accounting:budget:*` (create budgets, track variance)
- **Consolidation:** `accounting:consolidation:*` (multi-entity consolidation)
- **Audit Trail Access:** `accounting:audit:view_all` (compliance/SOX requirements)

### Advanced Features (Future)

- **Dimension Administration:** `accounting:dimension:manage` (create business units, cost centers)
- **Currency Management:** `accounting:currency:*` (FX rates, revaluation)
- **Tax Accounting:** `accounting:tax:*` (tax jurisdictions, calculations)

---

## Change Log

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-01-24 | 1.0 | Backend Team | Initial permission taxonomy created following CRM pattern |

---

## Appendix: Complete Permission List

### Chart of Accounts (5 permissions)
- `accounting:coa:view`
- `accounting:coa:create`
- `accounting:coa:edit`
- `accounting:coa:deactivate`

### GL Mapping (5 permissions)
- `accounting:mapping:view`
- `accounting:mapping:create`
- `accounting:mapping:edit`
- `accounting:mapping:deactivate`
- `accounting:mapping:test_resolution`

### Posting Rules (4 permissions)
- `accounting:posting_rules:view`
- `accounting:posting_rules:create`
- `accounting:posting_rules:publish`
- `accounting:posting_rules:archive`

### Journal Entries (5 permissions)
- `accounting:je:view`
- `accounting:je:create`
- `accounting:je:post`
- `accounting:je:reverse`
- `accounting:je:view_suspense`

### Accounts Payable (4 permissions)
- `accounting:ap:view`
- `accounting:ap:approve`
- `accounting:ap:reject`
- `accounting:ap:pay`

### Event Ingestion (3 permissions)
- `accounting:events:submit`
- `accounting:events:view`
- `accounting:events:retry`

### Posting Configuration (3 permissions - Issue #203)
- `accounting:posting_config:view`
- `accounting:posting_config:manage`
- `accounting:posting_config:test_resolution`

### Vendor Bill Management (5 permissions - Issue #194)
- `accounting:vendor_bill:view`
- `accounting:vendor_bill:create`
- `accounting:vendor_bill:edit`
- `accounting:vendor_bill:approve`
- `accounting:vendor_bill:reject`

### AP Payment Workflow (5 permissions - Issue #193)
- `accounting:ap_payment:view`
- `accounting:ap_payment:approve_under_threshold`
- `accounting:ap_payment:approve_over_threshold`
- `accounting:ap_payment:schedule`
- `accounting:ap_payment:cancel`

### Manual Journal Entry (6 permissions - Issue #190)
- `accounting:manual_je:view`
- `accounting:manual_je:create_draft`
- `accounting:manual_je:edit_draft`
- `accounting:manual_je:post`
- `accounting:manual_je:reverse`
- `accounting:manual_je:delete_draft`

### Bank/Cash Reconciliation (6 permissions - Issue #187)
- `accounting:reconciliation:view`
- `accounting:reconciliation:import_statement`
- `accounting:reconciliation:match`
- `accounting:reconciliation:create_adjustment`
- `accounting:reconciliation:finalize`
- `accounting:reconciliation:reopen`

### WorkCompleted Event Ingestion (5 permissions - Issue #183)
- `accounting:workcompleted_events:view`
- `accounting:workcompleted_events:submit`
- `accounting:workcompleted_events:retry`
- `accounting:workcompleted_events:suspend`
- `accounting:workcompleted_events:view_payload`

**Total Accounting Permissions:** 64
