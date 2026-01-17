Title: [BACKEND] [STORY] Counts: Approve and Post Adjustments from Cycle Count
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/26
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

**Rewrite Variant:** inventory-flexible

## Story Intent
**As an** Inventory Manager,
**I want** a policy-driven approval workflow for inventory adjustments produced by cycle counts,
**so that** material inventory changes are reviewed and authorized while low-risk variances can be posted quickly, with full auditability and accounting visibility.

## Actors & Stakeholders
- **Inventory Manager:** Reviews/approves/rejects adjustments in approval tiers.
- **Director / Inventory Controller (Tier-2 Approver):** Approves high-risk adjustments.
- **System (Inventory Domain):** Evaluates policy thresholds, routes approvals, posts ledger entries, and updates on-hand.
- **Accounting (Downstream Consumer, future):** Consumes adjustment signals for shrink/gain reporting.

## Preconditions
1. A cycle count is completed and variance(s) are computed (see story #27).
2. An `InventoryAdjustment` is created for each variance (proposed adjustment).
3. Approver roles/permissions and threshold policies are configured.
4. User is authenticated.

## Functional Behavior

### Adjustment evaluation (policy-driven)
When an `InventoryAdjustment` is created, the system computes:
- `unitVariance = |varianceQty|`
- `valueVariance = |varianceQty × unitCost|`
- `percentVariance = |varianceQty| / max(onHandQty, 1)`

Then evaluates whether approval is required:

```
approvalRequired =
  unitVariance >= unitThreshold
  OR valueVariance >= valueThreshold
  OR percentVariance >= percentThreshold
```

### Below-threshold behavior (auto-approve)
- If `approvalRequired = false`:
  1. System sets adjustment status to `AUTO_APPROVED`.
  2. System immediately posts the adjustment:
     - creates an immutable `InventoryLedgerEntry` with type `ADJUST_CYCLE_COUNT`
     - updates `quantityOnHand`
  3. System emits `InventoryAdjustmentAutoApproved` (and/or `InventoryAdjustmentPosted`) via outbox.
- Auto-approval must still be fully auditable (actor, timestamps, values, policy version).

### Above-threshold behavior (manual approval)
- If `approvalRequired = true`:
  1. System sets adjustment status to `PENDING_APPROVAL`.
  2. System determines the required approval tier based on configured policy.
  3. System places the adjustment into the approval queue for authorized approvers.

### Approval tiers (two-tier launch model)
**Tier 1 – Manager**
- Approves adjustments above auto-approval threshold up to the Tier-2 threshold.

**Tier 2 – Director / Inventory Controller**
- Required for high-risk adjustments (e.g., `valueVariance > $1,000` or `percentVariance > 25%`, per policy).

Tiers and thresholds are configurable (policy-driven; not hard-coded).

### Approval actions
- **Approve** (authorized approver):
  - set status `POSTED`
  - create immutable `InventoryLedgerEntry (ADJUST_CYCLE_COUNT)`
  - update `quantityOnHand`
  - emit `InventoryAdjustmentPosted`
- **Reject** (authorized approver):
  - set status `REJECTED`
  - require `rejectionReason`
  - do not change on-hand; do not create ledger entry

## Alternate / Error Flows
- **Unauthorized approval attempt:** reject with access denied; status unchanged.
- **Stock item not found / inactive at posting:** fail posting, set status `FAILED`, record error details.
- **Concurrent modification:** posting must be transactional; ledger entry creation + on-hand update succeed or fail together.

## Business Rules
- Composite approval thresholds are evaluated as: **units OR value OR percent**.
- Below-threshold adjustments are auto-approved and posted.
- Above-threshold adjustments require manual approval with the correct tier.
- Posted/rejected adjustments are immutable; corrections occur via a new adjustment.
- All state transitions must be auditable.

## Notification Mechanism
**Required (v1):**
- In-app notification center badge/count for pending approvals.
- Approval dashboard/queue widget (filterable by location, SKU, variance, aging).

**Optional (configurable):**
- Email digest/escalation if pending longer than X minutes (e.g., 30–60 minutes), to avoid per-item spam.

## Data Requirements

### `InventoryAdjustment`
| Field | Type | Description |
|---|---|---|
| `adjustmentId` | UUID | Primary key |
| `stockItemId` | UUID | Stock item |
| `reasonCodeId` | UUID | Reason code (cycle count shrink/gain) |
| `varianceQty` | Integer/Decimal | Quantity variance (positive/negative) |
| `unitCost` | Money/Decimal | Cost at time of adjustment |
| `onHandQtyAtProposal` | Integer/Decimal | Used for percent variance |
| `unitVariance` | Integer/Decimal | Derived |
| `valueVariance` | Money/Decimal | Derived |
| `percentVariance` | Decimal | Derived |
| `status` | Enum | `PENDING_APPROVAL`, `AUTO_APPROVED`, `POSTED`, `REJECTED`, `FAILED` |
| `requiredApprovalTier` | Enum | `TIER_1_MANAGER`, `TIER_2_DIRECTOR` |
| `policyVersion` | String | Threshold policy version applied |
| `createdByUserId` | UUID | Who initiated |
| `approvedByUserId` | UUID (nullable) | Approver identity |
| `rejectionReason` | String (nullable) | Required on reject |
| `createdAt`, `updatedAt` | Timestamp | Server-generated |

### `InventoryLedgerEntry`
| Field | Type | Description |
|---|---|---|
| `ledgerEntryId` | UUID | Primary key |
| `stockItemId` | UUID | Stock item |
| `adjustmentId` | UUID | Source adjustment |
| `type` | Enum | `ADJUST_CYCLE_COUNT` |
| `changeInQuantity` | Integer/Decimal | Posted change |
| `quantityAfter` | Integer/Decimal | New on-hand |
| `postedAt` | Timestamp | Post timestamp |

## Acceptance Criteria

**AC1: Composite threshold triggers approval**
- Given thresholds configured for units, value, and percent
- When an adjustment is proposed
- Then `approvalRequired` is true if any threshold is exceeded.

**AC2: Below-threshold auto-approves and posts**
- Given an adjustment that does not exceed any threshold
- When it is evaluated
- Then status becomes `AUTO_APPROVED`
- And a ledger entry is created and on-hand is updated
- And `InventoryAdjustmentAutoApproved` is emitted.

**AC3: Above-threshold routes to correct tier**
- Given an adjustment above auto-approval threshold
- When it is evaluated
- Then status becomes `PENDING_APPROVAL`
- And `requiredApprovalTier` is set according to policy
- And the adjustment appears in the approval queue.

**AC4: Tiered approval posts adjustment**
- Given an adjustment is `PENDING_APPROVAL`
- And an authorized approver for the required tier approves
- Then status becomes `POSTED`
- And a ledger entry is created and on-hand is updated
- And `InventoryAdjustmentPosted` is emitted.

**AC5: Rejection requires reason and does not post**
- Given an adjustment is `PENDING_APPROVAL`
- When an authorized approver rejects with a reason
- Then status becomes `REJECTED`
- And no ledger entry is created and on-hand is unchanged.

**AC6: Notifications appear for pending approvals**
- Given an adjustment enters `PENDING_APPROVAL`
- Then in-app notification and dashboard queue reflect the pending item.

## Audit & Observability
- Audit all state transitions (created, auto-approved, approved, rejected, posted, failed) with actor, timestamps, values, and policy version.
- Metrics:
  - `inventory_adjustments_pending_approval_count`
  - `inventory_adjustments_posted_total` (tagged by reason + direction)
  - `inventory_adjustments_auto_approved_total`
  - `inventory_adjustments_requires_tier2_total`

## Original Story (Unmodified – For Traceability)
# Issue #26 — [BACKEND] [STORY] Counts: Approve and Post Adjustments from Cycle Count

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Counts: Approve and Post Adjustments from Cycle Count

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Inventory Manager**, I want approvals before posting adjustments so that shrink and corrections are controlled.

## Details
- Approval required above thresholds.
- Posting creates Adjust ledger entries and updates on-hand.

## Acceptance Criteria
- Adjustments require permission.
- Ledger entries posted.
- Thresholds enforced.
- Full audit trail.

## Integrations
- Optional accounting events for shrink/adjustment.

## Data / Entities
- InventoryLedgerEntry(Adjust), ApprovalRecord, ReasonCode, EventOutbox

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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