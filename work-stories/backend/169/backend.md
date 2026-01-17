Title: [BACKEND] [STORY] Estimate: Present Estimate Summary for Review
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/169
Labels: type:story, domain:workexec, status:needs-review

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:needs-review

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As a Service Advisor, I need to generate a clean, customer-facing summary of a Draft Estimate so that I can review the proposed scope of work, pricing, and terms with the customer before they provide their approval.

## Actors & Stakeholders
- **Primary Actor:** `Service Advisor` - The user who prepares estimates and interacts with the customer.
- **Secondary Actors:**
    - `System` - The POS system responsible for generating the summary and enforcing business rules.
- **Stakeholders:**
    - `Customer` - The recipient of the estimate summary who uses it to make an approval decision.
    - `Compliance Officer` - Concerned that all required legal terms, disclaimers, and expiration dates are presented accurately.
    - `Finance Team` - Concerned that the financial figures presented to the customer are consistent with the internal estimate record.

## Preconditions
1. An `Estimate` exists with the `status` of `Draft`.
2. The `Estimate` contains at least one `EstimateItem` with defined quantities and pricing.
3. The requesting user (Service Advisor) is authenticated and has the necessary permissions to access the specified `Estimate`.
4. The system has a defined visibility policy for the `Service Advisor` role.

## Functional Behavior

### Trigger
The Service Advisor initiates an action to generate a customer-facing summary for a specific `Draft` Estimate.

### Process
1.  The System receives a request to generate a summary for a given `estimateId`.
2.  The System validates that the `Estimate` exists, is in `Draft` status, and the user is authorized.
3.  The System retrieves the complete `Estimate` record, including all associated line items, calculated totals (subtotal, taxes, fees, grand total), and metadata (e.g., `expirationDate`).
4.  The System fetches the role-based visibility policy applicable to the Service Advisor.
5.  The System constructs a data payload for the summary, explicitly excluding any fields marked as internal-only by the visibility policy (e.g., `itemCost`, `profitMargin`).
6.  The System retrieves the currently active, configured legal terms and conditions and any required disclaimers.
7.  The System renders a customer-facing document (e.g., PDF or HTML view) using the filtered data payload and the retrieved legal text.

### Outcome
- **Success:** The System presents the generated summary document to the Service Advisor for review or sharing with the customer.
- **Failure:** The System returns a structured error message indicating the reason for failure (e.g., invalid status, missing configuration).

## Alternate / Error Flows
- **Estimate Not Found:** If the `estimateId` does not correspond to an existing record, the system returns a `404 Not Found` error.
- **Invalid Estimate Status:** If the `Estimate` is not in `Draft` status (e.g., it is `Approved` or `Declined`), the system returns a `409 Conflict` error with a message "Summary can only be generated for Draft estimates."
- **Authorization Failure:** If the user lacks permission to view the estimate, the system returns a `403 Forbidden` error.
- **Missing Configuration:** If required legal terms or disclaimers are not configured in the system, the system returns a `500 Internal Server Error` (or a `409 Conflict`) with a clear error code like `CONFIGURATION_ERROR_TERMS_MISSING`.

## Business Rules
- The summary is a read-only representation of the `Estimate` at the time of generation. It does not modify the `Estimate`'s state.
- All monetary values displayed on the summary (line totals, subtotal, taxes, grand total) MUST be identical to the values stored on the `Estimate` record.
- The visibility of internal financial data (e.g., part cost, labor cost, margin) is strictly controlled by a non-negotiable, role-based security policy.
- The generation of a summary MUST be blocked if mandatory legal text (terms, disclaimers) is not configured, to prevent the creation of non-compliant documents.
- If the `Estimate` has an `expirationDate`, it MUST be clearly displayed on the summary.
**BR-SNAPSHOT-1: Immutable Snapshot Requirement**
- When an estimate summary is generated for review, the system SHALL create an immutable snapshot of:
  - All estimate line items and pricing
  - Customer information
  - Vehicle information
  - Legal terms and disclaimers (if configured)
  - Configuration state and policy settings
  - Generation timestamp and generating user
- The snapshot SHALL NOT be modified after creation
- The snapshot SHALL be used as the authoritative source for the presented estimate

**BR-LEGAL-1: Legal Terms Policy Configuration**
- The system SHALL support a configurable policy for handling missing legal terms
- Available policy options:
  - `FAIL`: Prevent estimate summary generation if legal terms are not configured (safe default)
  - `USE_DEFAULTS`: Generate estimate summary with system-configured default legal terms
- The policy SHALL be configurable per shop location or globally
- The active policy SHALL be captured in the estimate snapshot for audit purposes

**BR-LEGAL-2: Missing Legal Terms Handling**
- When policy is `FAIL` and legal terms are not configured:
  - System SHALL return error: "Cannot generate estimate summary: Legal terms and conditions not configured"
  - System SHALL log the configuration error
  - System SHALL NOT create an estimate snapshot
- When policy is `USE_DEFAULTS` and legal terms are not configured:
  - System SHALL use the system default legal terms
  - System SHALL log a warning indicating default terms were used
  - System SHALL include policy decision in snapshot metadata
- When legal terms are configured:
  - System SHALL use the configured legal terms regardless of policy
  - System SHALL include the source and version of legal terms in the snapshot

## Data Requirements
- **`Estimate` Entity:**
    - `estimateId` (PK)
    - `status` (String, Enum: 'Draft', 'Approved', etc.)
    - `subtotal` (Money)
    - `taxTotal` (Money)
    - `grandTotal` (Money)
    - `expirationDate` (Date, Nullable)
- **`EstimateItem` Entity:**
    - `description` (String)
    - `quantity` (Decimal)
    - `unitPrice` (Money)
    - `lineTotal` (Money)
    - `itemCost` (Money, Internal-Only)
- **`SystemConfiguration` Entity/Service:**
    - `customerFacingTerms` (Text)
    - `estimateDisclaimer` (Text)
- **`RoleVisibilityPolicy` Entity/Service:**
    - Defines field-level visibility rules per user role (e.g., 'Service Advisor' cannot view 'itemCost').
**EstimateSummarySnapshot**
- `snapshotId` (UUID, PK, not null) - Unique identifier for the snapshot
- `estimateId` (UUID, FK to Estimate, not null) - Reference to source estimate
- `snapshotTimestamp` (TIMESTAMP, not null) - When snapshot was created
- `snapshotData` (JSONB, not null) - Complete estimate data including line items, pricing, customer, vehicle
- `legalTermsSource` (VARCHAR(50), nullable) - Source of legal terms: 'CONFIGURED', 'DEFAULT', or null if missing
- `legalTermsVersion` (VARCHAR(50), nullable) - Version identifier of legal terms used
- `legalTermsText` (TEXT, nullable) - Full text of legal terms included in summary
- `policyMode` (VARCHAR(20), not null) - Active policy: 'FAIL' or 'USE_DEFAULTS'
- `createdBy` (UUID, FK to User, not null) - User who generated the summary
- `auditMetadata` (JSONB, not null) - Additional audit data (IP, user agent, shop location, etc.)

**LegalTermsConfiguration**
- `configId` (UUID, PK, not null) - Unique identifier
- `shopLocationId` (UUID, FK to Location, nullable) - Specific shop location (null = global)
- `termsText` (TEXT, not null) - Legal terms and disclaimers content
- `termsVersion` (VARCHAR(50), not null) - Version identifier
- `effectiveDate` (DATE, not null) - When these terms become effective
- `expirationDate` (DATE, nullable) - When these terms expire (null = no expiration)
- `isDefault` (BOOLEAN, not null, default false) - Whether this is the system default
- `createdAt` (TIMESTAMP, not null) - Creation timestamp
- `createdBy` (UUID, FK to User, not null) - User who created the configuration

**MissingLegalTermsPolicy**
- `policyId` (UUID, PK, not null) - Unique identifier
- `shopLocationId` (UUID, FK to Location, nullable) - Specific shop location (null = global)
- `policyMode` (VARCHAR(20), not null) - 'FAIL' or 'USE_DEFAULTS'
- `effectiveDate` (DATE, not null) - When this policy becomes effective
- `updatedAt` (TIMESTAMP, not null) - Last update timestamp
- `updatedBy` (UUID, FK to User, not null) - User who last updated the policy

## Acceptance Criteria
**Scenario: Successful generation of an estimate summary**
  Given an Estimate exists with the status "Draft"
  And the Estimate has a grand total of "$550.00"
  And the system is configured with standard "Terms and Conditions"
  When the Service Advisor requests a customer summary for that Estimate
  Then the system should generate a summary document
  And the document's grand total must be "$550.00"
  And the document must contain the standard "Terms and Conditions"

**Scenario: Internal cost fields are excluded from the summary**
  Given an Estimate exists with an item that has an internal "itemCost" of "$100.00"
  And the Service Advisor role is configured to hide the "itemCost" field
  When the Service Advisor requests a customer summary for that Estimate
  Then the system should generate a summary document
  And the document's content must not contain the "itemCost" field or its value

**Scenario: Attempting to generate a summary for an approved estimate**
  Given an Estimate exists with the status "Approved"
  When the Service Advisor requests a customer summary for that Estimate
  Then the system must return a "409 Conflict" error
  And provide a message indicating the action is invalid for the current status

**Scenario: Summary generation fails when legal terms are not configured**
  Given an Estimate exists with the status "Draft"
  And the system's standard "Terms and Conditions" are not configured
  When the Service Advisor requests a customer summary for that Estimate
  Then the system must return an error
  And the error must indicate that a system configuration is missing

**AC-SNAPSHOT-1: Immutable Snapshot Created**
- Given an estimate is ready for customer review
- When the service advisor generates an estimate summary
- Then the system SHALL create an immutable snapshot containing all estimate data
- And the snapshot SHALL include a unique snapshotId and timestamp
- And the snapshot SHALL be stored before presenting to the customer

**AC-LEGAL-1: Legal Terms Included When Configured**
- Given legal terms are configured for the shop location
- When an estimate summary is generated
- Then the system SHALL include the configured legal terms in the snapshot
- And the snapshot SHALL record legalTermsSource as 'CONFIGURED'
- And the snapshot SHALL include the termsVersion identifier

**AC-LEGAL-2: Policy Mode - FAIL (Safe Default)**
- Given the missing legal terms policy is set to 'FAIL'
- And legal terms are NOT configured for the shop location
- When an estimate summary generation is attempted
- Then the system SHALL prevent the generation
- And return error message: "Cannot generate estimate summary: Legal terms and conditions not configured"
- And log a configuration error event
- And NOT create an estimate snapshot

**AC-LEGAL-3: Policy Mode - USE_DEFAULTS**
- Given the missing legal terms policy is set to 'USE_DEFAULTS'
- And legal terms are NOT configured for the shop location
- When an estimate summary is generated
- Then the system SHALL use the system default legal terms
- And the snapshot SHALL record legalTermsSource as 'DEFAULT'
- And log a warning: "Estimate summary generated with default legal terms"
- And include the policy decision in snapshot metadata

**AC-LEGAL-4: Policy Configuration**
- Given an administrator is configuring missing legal terms policy
- When they set the policy mode for a shop location
- Then the system SHALL validate the mode is either 'FAIL' or 'USE_DEFAULTS'
- And store the policy with effectiveDate and updatedBy
- And the new policy SHALL be used for all subsequent estimate summaries at that location

**AC-LEGAL-5: Audit Trail Captured**
- Given any estimate summary is generated
- When the snapshot is created
- Then the system SHALL capture in auditMetadata:
  - Active policy mode
  - Legal terms source and version
  - Generating user, timestamp, and shop location
  - IP address and user agent (if available)
- And this metadata SHALL be immutable after snapshot creation

## Audit & Observability
- **Audit Log:** An immutable audit event MUST be created upon every successful generation of an estimate summary. The event must record:
    - `eventType`: `ESTIMATE_SUMMARY_GENERATED`
    - `timestamp`
    - `userId` (of the requesting Service Advisor)
    - `estimateId`
    - `traceId`
- **Monitoring:**
    - Track the latency of the summary generation endpoint (`estimate.summary.generation.latency`).
    - Increment a counter for successful generations (`estimate.summary.generation.success`).
    - Increment a counter for failed generations, tagged by error type (`estimate.summary.generation.failure{reason="invalid_status"}`).
- **Logging:** Log detailed error information for failed generation attempts, including the `estimateId` and the specific validation or configuration rule that failed.

## Answered Questions
### Question 1: Missing Terms & Conditions Policy
**Question:** If required legal terms and disclaimers are not configured in the system, should the summary generation fail with an error (as assumed in this story), or should it proceed with a warning/default text?

**Decision:** Use immutable snapshots; if legal terms are missing, handle gracefully per policy (fail or use defaults) with clear documentation.

**Interpretation:**
- The system SHALL use immutable snapshots of estimate data when generating summaries
- Legal terms and disclaimers SHALL be captured as part of the snapshot
- The handling of missing legal terms SHALL be configurable per business policy
- Policy options include:
  - **Fail** (safe default): Prevent summary generation if legal terms are not configured
  - **Use defaults**: Generate summary with system-configured default legal terms
- The chosen policy MUST be clearly documented and configurable
- All snapshots MUST include timestamp and configuration state for audit purposes

---
## Original Story (Unmodified – For Traceability)
# Issue #169 — [BACKEND] [STORY] Estimate: Present Estimate Summary for Review

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Estimate: Present Estimate Summary for Review

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor

## Trigger
A Draft estimate is ready to be reviewed with the customer before approval submission.

## Main Flow
1. User requests a customer-facing summary view/printout.
2. System generates a summary that includes scope, quantities, pricing, taxes, fees, and totals.
3. System excludes restricted internal fields (cost, margin) based on role/policy.
4. System includes configured terms, disclaimers, and expiration date.
5. User shares summary with customer and optionally proceeds to submit for approval.

## Alternate / Error Flows
- Terms/disclaimers not configured → use defaults or block submission depending on compliance settings.

## Business Rules
- Customer summary must be consistent with the estimate snapshot.
- Visibility rules must be enforced for internal-only fields.
- Expiration must be clearly shown if configured.

## Data Requirements
- Entities: Estimate, EstimateItem, DocumentTemplate, VisibilityPolicy
- Fields: displayPrice, taxTotal, grandTotal, termsText, expirationDate, hiddenCostFields

## Acceptance Criteria
- [ ] Customer-facing summary is generated and matches estimate totals.
- [ ] Restricted fields are not displayed to unauthorized roles.
- [ ] Summary includes terms and expiration where configured.

## Notes for Agents
This output becomes the basis for consent text during approval—keep it deterministic.


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*