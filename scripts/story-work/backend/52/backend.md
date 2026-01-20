Title: [BACKEND] [STORY] StorePrice: Set Location Store Price Override within Guardrails
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/52
Labels: type:story, domain:pricing, status:ready-for-dev, agent:story-authoring, agent:pricing

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:pricing
- status:ready-for-dev

### Recommended
- agent:pricing
- agent:story-authoring

---
**Rewrite Variant:** pricing-strict
---

## Story Intent
**As a** Store Manager,
**I want to** set a location-specific store price override for a product,
**so that** I can respond to local market conditions while adhering to centrally-defined pricing guardrails.

## Actors & Stakeholders
- **Store Manager (Primary Actor)**: Initiates a location-specific price override.
- **Pricing System (System Actor)**: Enforces guardrails, manages override lifecycle, and exposes effective pricing.
- **Approver (Secondary Actor)**: Approves/rejects overrides that exceed auto-approval thresholds (role-based; see decisions below).
- **Workexec System (Downstream Consumer)**: Consumes the effective price for a given product at a location.
- **Reporting / Analytics (Downstream Consumer)**: Tracks override volume, discount magnitude, and approval cycle times.

## Preconditions
1. Store Manager is authenticated and authorized to manage pricing for their assigned location(s).
2. Target product exists and has a computed base price available (from company/base price book + other base pricing rules).
3. An active `GuardrailPolicy` exists for the target location/region.

## Functional Behavior
1. Store Manager selects a product and initiates the override flow for their location.
2. System displays:
   - current base price,
   - cost (if available),
   - any active/pending override.
3. Store Manager submits an `overridePrice`.
4. System validates against the active `GuardrailPolicy` (hard limits + approval thresholds), computing:
   - resulting margin % (where cost is available),
   - discount % from base price.
5. **Scenario A — Auto-Approval**
   - If the override complies with hard guardrails and is within auto-approval thresholds, persist `LocationPriceOverride` as `ACTIVE`.
   - Publish `PriceOverrideActivated`.
6. **Scenario B — Requires Manual Approval**
   - If the override complies with hard guardrails but exceeds auto-approval threshold, persist `LocationPriceOverride` as `PENDING_APPROVAL`.
   - Create an `ApprovalRequest`, assign an approver deterministically (see BR5), and notify.
   - Effective price remains unchanged until approved.
   - Publish `PriceOverrideSubmittedForApproval`.
7. **Scenario C — Rejected by Guardrails**
   - If the override violates hard guardrails, reject the request and return a validation error explaining the violated rule.

## Alternate / Error Flows
- **Error Flow 1: Violation of Hard Guardrails**
  - Reject with a clear error (e.g., “Margin below 15% minimum” or “Discount exceeds 25% maximum”).
- **Error Flow 2: Unauthorized Action**
  - If user attempts override for an unauthorized location, return `403 Forbidden`.
- **Error Flow 3: Invalid Product**
  - If product does not exist, return `404 Not Found`.

## Business Rules
These decisions were resolved via Clarification #254 (see issue comment issuecomment-3739398431).

- **BR1: Price Precedence**
  - Effective sale price at a location is the active `LocationPriceOverride` if present; otherwise the computed base price.

- **BR2: Minimum Margin Enforcement (Hard Limit)**
  - Override cannot be created if it results in margin below `min_margin_percent` from the applicable `GuardrailPolicy`.

- **BR3: Maximum Discount Enforcement (Hard Limit)**
  - Override cannot be created if it exceeds `max_discount_percent` from the applicable `GuardrailPolicy`.

- **BR4: Approval Requirement (Soft Limit)**
  - Overrides exceeding `auto_approval_threshold_percent` must be approved before activation.

- **BR5: Approval Routing (Decision)**
  - Use a **role-based approval pool** scoped by location/region with deterministic assignment.
  - Required approver capability (example): `pricing:override:approve`.
  - Typical holders: Regional Pricing Manager (primary), Pricing Desk (fallback pool).
  - Deterministic assignment:
    1. Determine scope (Location → Region)
    2. Select primary approver for that scope
    3. If unavailable, route to pool (round-robin or least-loaded)
    4. Record `assignedApproverId` and `assignmentStrategy`

- **BR6: Approval Workflow (Decision)**
  - **In-app notification is primary; email is secondary; escalation is time-based.**
  - Recommended defaults:
    - SLA: 2 business hours
    - Escalation: T+2h notify backup/pool; T+4h escalate to Regional Manager
  - Escalations must be audit-logged.

- **BR7: Rejection Handling (Decision)**
  - Rejected overrides are retained (not deleted) with terminal `REJECTED` state.
  - Record becomes immutable after rejection.
  - Required rejection fields:
    - `rejectedBy`, `rejectedAt`, `rejectionReasonCode`, `rejectionNotes` (required)
  - Requester is notified immediately (in-app + email) with reason and next steps.

## Data Requirements
- **`LocationPriceOverride`**
  - `overrideId` (PK)
  - `locationId` (FK)
  - `productId` (FK)
  - `overridePrice` (Monetary Amount)
  - `status` (Enum: `ACTIVE`, `PENDING_APPROVAL`, `REJECTED`, `INACTIVE`)
  - `createdByUserId`
  - `createdAt` (Timestamp)
  - `approvedByUserId` (Nullable)
  - Rejection fields: `rejectedBy`, `rejectedAt`, `rejectionReasonCode`, `rejectionNotes`
  - `resolvedAt` (Timestamp, Nullable)

- **`GuardrailPolicy`**
  - `policyId` (PK)
  - `scope` (e.g., `LOCATION`, `REGION`)
  - `scopeId` (e.g., `locationId`)
  - `min_margin_percent` (Decimal)
  - `max_discount_percent` (Decimal)
  - `auto_approval_threshold_percent` (Decimal)

- **`ApprovalRequest`**
  - `requestId` (PK)
  - `overrideId` (FK)
  - `status` (Enum: `PENDING`, `APPROVED`, `REJECTED`)
  - `assignedApproverId` (UUID)
  - `assignmentStrategy` (String)

## Acceptance Criteria
- **AC1: Override within Auto-Approval Limits**
  - **Given** base price $100 and cost $50,
  - **And** `GuardrailPolicy` min margin 15% and auto-approval threshold 10%,
  - **When** Store Manager submits override $95 (5% discount),
  - **Then** create `LocationPriceOverride` with status `ACTIVE`,
  - **And** effective price becomes $95.

- **AC2: Override Requires Manual Approval**
  - **Given** base price $100 and cost $50,
  - **And** `GuardrailPolicy` min margin 15% and auto-approval threshold 10%,
  - **When** Store Manager submits override $88 (12% discount),
  - **Then** create `LocationPriceOverride` with status `PENDING_APPROVAL`,
  - **And** create `ApprovalRequest` with `assignedApproverId` populated,
  - **And** effective price remains $100 until approved.

- **AC3: Override Violates Hard Guardrail**
  - **Given** base price $100 and cost $50,
  - **And** `GuardrailPolicy` min margin 15%,
  - **When** Store Manager submits override $55 (margin below 15%),
  - **Then** reject with validation error,
  - **And** do not create a `LocationPriceOverride` record.

- **AC4: Audit Log Creation**
  - **Given** any successful or failed attempt to create/update an override,
  - **When** the transaction completes,
  - **Then** an immutable audit entry exists including user, product, location, proposed price, and outcome.

- **AC5: Rejection Persisted and Notified**
  - **Given** a pending override is rejected with a reason,
  - **When** rejection is processed,
  - **Then** override status becomes `REJECTED` (not deleted),
  - **And** requester is notified (in-app + email) with reason and next steps.

## Audit & Observability
- **Audit Trail:** Every state change of a `LocationPriceOverride` (creation, approval, rejection, deactivation) is captured in an immutable audit log with user, timestamps, and before/after values.
- **Events:** Publish domain events:
  - `PriceOverrideSubmittedForApproval`
  - `PriceOverrideActivated`
  - `PriceOverrideRejected`
- **Metrics:**
  - Overrides created (per location, per user)
  - Average override discount %
  - Time-to-approval for pending overrides

## Open Questions (if any)
- None.

## Original Story (Unmodified – For Traceability)
# Issue #52 — [BACKEND] [STORY] StorePrice: Set Location Store Price Override within Guardrails

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] StorePrice: Set Location Store Price Override within Guardrails

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Store Manager**, I want to set store price overrides so that I can compete locally within policy.

## Details
- Override layered over base price.
- Guardrails: min margin %, max discount %, approval thresholds.

## Acceptance Criteria
- Override created/updated.
- Guardrails enforced with approvals.
- Audited.

## Integrations
- Workexec receives store price for that location; reporting tracks override usage.

## Data / Entities
- LocationPriceOverride, GuardrailPolicy, ApprovalRecord, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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
