Title: [BACKEND] [STORY] Counts: Execute Cycle Count and Record Variances
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/27
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

**Rewrite Variant:** inventory-flexible

## Story Intent
**As an** Auditor,
**I want** to record a blind physical count for a cycle count task and (when needed) perform bounded recounts,
**so that** the system can compute variances against expected quantities with a complete, immutable audit trail and escalate unresolved discrepancies for investigation.

## Actors & Stakeholders
- **Auditor (Primary Actor):** Performs the physical count and submits results.
- **Inventory Manager (Stakeholder):** Reviews variances, triggers recounts beyond the auditor’s allowance, approves exceptions, and oversees investigations.
- **System (Inventory Domain):** Owns tasks, expected quantity, variance calculation, recount workflow state, and audit trail.
- **Accounting (Downstream Consumer, future):** May consume variance/adjustment signals.

## Preconditions
1. An active `CycleCountTask` exists for a specific product and storage location/bin.
2. The system has an `expectedQuantity` for that product at that location (not shown to the auditor during counting).
3. The Auditor is authenticated and authorized to execute cycle count tasks.

## Functional Behavior

### Happy Path (Initial Count)
1. Auditor selects an active `CycleCountTask` from their assigned list.
2. System displays task details (location/bin, product/SKU, description) but **does not** display `expectedQuantity` (blind count).
3. Auditor counts physical items in the specified bin.
4. Auditor submits `actualQuantity`.
5. System validates `actualQuantity` (non-negative numeric; precision consistent with the item’s UOM rules).
6. System creates an immutable `CountEntry` (sequence 1).
7. System calculates `variance = actualQuantity - expectedQuantity`.
8. System updates `CycleCountTask` status to `COUNTED_PENDING_REVIEW` (or equivalent) and references the latest `CountEntry`.

### Recount Flow (Bounded)
Recounts are supported for auditability and error correction.

#### Recount persistence rules (mandatory)
- Every recount creates a **new** immutable `CountEntry`.
- Recount entries must reference the prior count via:
  - `recountOfCountEntryId`
  - `recountSequenceNumber` (1, 2, 3…)
- Overwriting or updating prior entries is not allowed.

#### Who can trigger recounts
- **Auditor** may trigger **one immediate recount**:
  - only before manager approval/finalization,
  - intended to correct obvious mistakes.
- **Inventory Manager (or higher)** may trigger recounts:
  - after review,
  - after variance threshold breach,
  - as part of approval workflow.

#### Recount limits
- Hard cap per `CycleCountTask`: **3 total counts** (original count + up to **2 recounts**).
- When the cap is reached, the system blocks further recounts and routes the task to investigation.

## Alternate / Error Flows
- **Invalid quantity submitted:** reject and return a validation error (e.g., “Quantity must be zero or a positive number”).
- **Unauthorized recount attempt:** reject with access denied (auditor attempting a second recount; non-manager attempting manager-level recount).
- **Recount cap exceeded:**
  - System blocks further recounts.
  - System sets task status to `REQUIRES_INVESTIGATION`.
  - System requires manager sign-off and a required “root cause note” (e.g., damage, theft, system error, supplier issue) before proceeding to any adjustment flow.

## Business Rules
- `variance = actualQuantity - expectedQuantity`.
- Positive variance = surplus; negative variance = shortage; zero = match.
- All `CountEntry` records are immutable.
- Blind count: `expectedQuantity` is never shown during count entry.

### Permissions (as decided)
- `TRIGGER_RECOUNT_SELF` — Auditor (limited to one immediate recount).
- `TRIGGER_RECOUNT_ANY` — Inventory Manager+.

## Data Requirements

### `CountEntry` (new entity)
| Field | Type | Description |
|---|---|---|
| `countEntryId` | UUID | Primary key |
| `cycleCountTaskId` | UUID | Parent task |
| `auditorId` | UUID | Who performed the count |
| `actualQuantity` | Integer/Decimal | Non-negative |
| `expectedQuantity` | Integer/Decimal | Stored for audit (not displayed during entry) |
| `variance` | Integer/Decimal | `actual - expected` |
| `countedAt` | Timestamp | Server-generated |
| `recountSequenceNumber` | Integer | 1 for initial count, increments per recount |
| `recountOfCountEntryId` | UUID (nullable) | Prior count entry reference |

### `CycleCountTask` (state + pointers)
| Field | Type | Description |
|---|---|---|
| `status` | Enum | Must include `COUNTED_PENDING_REVIEW` and `REQUIRES_INVESTIGATION` |
| `latestCountEntryId` | UUID | Pointer to latest count entry |
| `totalCountEntries` | Integer | Used to enforce cap |

## Acceptance Criteria

**AC1: Initial count creates immutable entry and computes variance**
- Given a `CycleCountTask` with `expectedQuantity = 100`
- When Auditor submits `actualQuantity = 102`
- Then system creates a new `CountEntry` (sequence 1) and stores `variance = +2`
- And task status becomes `COUNTED_PENDING_REVIEW`.

**AC2: Recount creates a new entry referencing prior**
- Given a task has an initial `CountEntry`
- When a recount is triggered and submitted
- Then system creates a new `CountEntry` with `recountOfCountEntryId` pointing to the prior entry
- And increments `recountSequenceNumber`.

**AC3: Auditor may trigger only one immediate recount**
- Given an auditor has already triggered one recount for a task
- When the auditor attempts to trigger a second recount
- Then the system rejects the request as unauthorized.

**AC4: Manager can trigger recounts beyond auditor allowance (until cap)**
- Given a task has an initial count and one auditor recount
- When a manager triggers another recount
- Then the system allows it (subject to cap) and records a new `CountEntry`.

**AC5: Cap enforcement and investigation escalation**
- Given a task already has 3 total `CountEntry` records
- When any user attempts to trigger another recount
- Then the system blocks the recount
- And sets task status to `REQUIRES_INVESTIGATION`.

**AC6: Invalid input rejected**
- Given an auditor is counting
- When they submit a negative quantity
- Then the system rejects with a validation error.

## Audit & Observability
- **Audit trail:** Every `CountEntry` creation must be logged immutably with `countEntryId`, `cycleCountTaskId`, `auditorId`, `actualQuantity`, `expectedQuantity`, `variance`, `recountSequenceNumber`, `recountOfCountEntryId`, and timestamp.
- **Metrics:**
  - `cycle_counts_submitted_total`
  - `cycle_counts_recounts_total`
  - `cycle_counts_requires_investigation_total`
  - `cycle_count_submission_duration_seconds`
- **Event publishing (future-safe design):** For non-zero variance, design to emit `InventoryVarianceDetected` including item/product ID, location, variance quantity, and reference to `CountEntry`.

## Original Story (Unmodified – For Traceability)
## Backend Implementation for Story

**Original Story**: [STORY] Counts: Execute Cycle Count and Record Variances

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Auditor**, I want to record counted quantities so that variances can be reviewed and corrected.

## Details
- Count tasks per bin.
- Record counts; optional recount.
- Variance report generated.

## Acceptance Criteria
- Counts recorded.
- Variance computed.
- Recount supported (basic).
- Audited.

## Integrations
- May later emit accounting adjustment events.

## Data / Entities
- CycleCountTask, CountEntry, VarianceReport, AuditLog

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