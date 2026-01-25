---
title: Accounting Domain Model
description: Entity definitions, relationships, state machines, and business rules for the accounting domain
---

## Overview

The Durion Accounting domain manages financial transactions, account structures, posting rules, and accounts payable operations. This document defines the core entities, their relationships, state transitions, and validation rules.

## Entity Definitions

### GLAccount (General Ledger Account)

**Purpose:** Represents a chart-of-accounts entry where financial transactions are posted.

**Fields:**
- `glAccountId` (UUID, PK): Unique identifier
- `organizationId` (UUID, FK): Owning entity
- `accountNumber` (String, required, unique per org): Human-readable account identifier (e.g., "1000-0001")
- `accountName` (String, required): Display name (e.g., "Cash – Operating")
- `accountType` (Enum, required): Classification: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE, CONTRA_ASSET
- `status` (Enum, derived): ACTIVE, INACTIVE, ARCHIVED (see status derivation rules below)
- `balanceType` (Enum, required): DEBIT or CREDIT (normal balance for account)
- `description` (Text, optional): Extended description for reference
- `activatedAt` (Timestamp, required): When account became available for posting
- `deactivatedAt` (Timestamp, optional): When account stopped accepting new postings
- `archivedAt` (Timestamp, optional): When account was removed from active charts
- `currentBalance` (Decimal, derived, read-only): Sum of all posted journal entry lines
- `createdBy` (UUID, audit): User who created the account
- `createdAt` (Timestamp, audit): Creation timestamp
- `modifiedBy` (UUID, audit): User who last modified
- `modifiedAt` (Timestamp, audit): Last modification timestamp
- `version` (Integer, optimistic locking): Incremented on each update

**Status Derivation Rules:**
- ACTIVE: activatedAt ≤ NOW and deactivatedAt is NULL
- INACTIVE: deactivatedAt ≤ NOW and archivedAt is NULL
- ARCHIVED: archivedAt ≤ NOW
- Status is read-only; transitions managed by deactivate/archive operations

**Immutability Rules:**
- `accountNumber` cannot change after creation
- `accountType` cannot change after account has posted transactions
- Once archived, account cannot be reactivated

**Constraints:**
- `accountNumber` must match pattern: `^\d{4}-\d{4}$` (organization-specific format)
- `deactivatedAt` must be >= `activatedAt`
- `archivedAt` must be >= `deactivatedAt` (if both set)
- Only ASSET or LIABILITY accounts can be targets for AP postings

### PostingCategory

**Purpose:** Groups GL accounts by business function (e.g., Expense, Revenue, Tax).

**Fields:**
- `postingCategoryId` (UUID, PK): Unique identifier
- `organizationId` (UUID, FK): Owning organization
- `categoryName` (String, required, unique per org): Name (e.g., "Accounts Payable Liability")
- `description` (Text, optional): Purpose and usage notes
- `status` (Enum, required): ACTIVE, ARCHIVED
- `createdAt` (Timestamp, audit)
- `modifiedAt` (Timestamp, audit)

**Constraints:**
- Used as intermediate grouping in GL mapping hierarchies
- Cannot be deleted if active mappings reference it

### MappingKey

**Purpose:** Intermediate classification for mapping events to GL accounts (e.g., a workorder event type or line item category).

**Fields:**
- `mappingKeyId` (UUID, PK): Unique identifier
- `organizationId` (UUID, FK): Owning organization
- `postingCategoryId` (UUID, FK): Parent PostingCategory
- `keyName` (String, required, unique per category): Name (e.g., "labor_cost", "parts_revenue")
- `description` (Text, optional): What this key represents
- `status` (Enum, required): ACTIVE, ARCHIVED
- `effectiveFrom` (Date, required): Date this key became available
- `effectiveUntil` (Date, optional): Date this key expired (NULL = current)
- `createdAt` (Timestamp, audit)
- `modifiedAt` (Timestamp, audit)

**Constraints:**
- Effective-dated: only one non-expired version active per key at any time
- No overlapping effectiveFrom/effectiveUntil across versions of same key
- Cannot be deleted; only marked expired

### GLMapping

**Purpose:** Maps business events (via MappingKey) to GL accounts with multi-dimensional support.

**Fields:**
- `glMappingId` (UUID, PK): Unique identifier
- `organizationId` (UUID, FK): Owning organization
- `mappingKeyId` (UUID, FK): Source MappingKey
- `glAccountId` (UUID, FK): Target GL account
- `dimensionConstraints` (JSON, optional): Filter by dimensions (e.g., `{"location_id": "loc-001"}`)
- `priority` (Integer, required): Resolution order when multiple mappings match (lower = higher priority)
- `status` (Enum, required): ACTIVE, ARCHIVED
- `effectiveFrom` (Date, required): When this mapping became valid
- `effectiveUntil` (Date, optional): When this mapping expires
- `createdAt` (Timestamp, audit)
- `modifiedAt` (Timestamp, audit)

**Constraints:**
- Effective-dated: no overlapping active mappings for same (mappingKey, dimensionConstraints) pair
- All referenced GL accounts must have status ACTIVE
- Dimension constraints must reference known dimension types (see Dimension Schema below)

### PostingRuleSet

**Purpose:** Defines rules for how business events become journal entries (versioned, state-managed).

**Fields:**
- `postingRuleSetId` (UUID, PK): Unique identifier
- `organizationId` (UUID, FK): Owning organization
- `ruleSetName` (String, required): Name (e.g., "Workorder Completion Rules v2")
- `description` (Text, optional): Purpose and scope
- `rulesJson` (JSON, required): Array of PostingRule objects (see schema below)
- `status` (Enum, required): DRAFT, PUBLISHED, ARCHIVED (see state machine below)
- `publishedAt` (Timestamp, optional): When set to PUBLISHED
- `effectiveFrom` (Date, required): Earliest date these rules apply
- `effectiveUntil` (Date, optional): Latest date these rules apply
- `baseVersion` (String, optional): Reference to parent version if derived from another
- `createdBy` (UUID, audit): Author of rule set
- `createdAt` (Timestamp, audit)
- `modifiedBy` (UUID, audit)
- `modifiedAt` (Timestamp, audit)
- `version` (Integer, optimistic locking): Incremented on each update

**PostingRule Schema (JSON):**
```json
{
  "ruleId": "rule-001",
  "eventType": "workorder.completed",
  "condition": {
    "operator": "AND",
    "predicates": [
      { "field": "serviceType", "op": "EQ", "value": "labor" },
      { "field": "status", "op": "IN", "values": ["completed", "closed"] }
    ]
  },
  "postingInstructions": [
    {
      "lineType": "DEBIT",
      "glAccountMappingKey": "labor_cost",
      "amountField": "laborAmount",
      "dimensions": { "location_id": "{{ event.locationId }}" }
    },
    {
      "lineType": "CREDIT",
      "glAccountMappingKey": "labor_revenue",
      "amountField": "laborAmount",
      "dimensions": { "location_id": "{{ event.locationId }}" }
    }
  ]
}
```

**State Machine:**
- DRAFT: Created, can be edited, cannot be used for posting
- PUBLISHED: Locked, used for posting event-to-JE conversion, cannot be edited
- ARCHIVED: Locked, no longer used for posting

**Constraints:**
- Only PUBLISHED rule sets are used for event processing
- Editing requires creating new version (set baseVersion to old ID)
- At most one PUBLISHED version per (ruleSetName, dateRange) at any time
- All referenced mappingKeys must exist and be ACTIVE

### JournalEntry

**Purpose:** Immutable record of a balanced set of GL account postings, sourced from business events.

**Fields:**
- `journalEntryId` (UUID, PK): Unique identifier
- `organizationId` (UUID, FK): Owning organization
- `referenceId` (String, optional): Link to source event (e.g., workorder ID)
- `referenceType` (String, optional): Type of reference (e.g., "workorder", "invoice")
- `description` (String, required): Narrative (e.g., "WO-12345 labor completion")
- `status` (Enum, required): DRAFT, POSTED, REVERSED (see state machine below)
- `postedAt` (Timestamp, optional): When JE was posted (moved to POSTED)
- `reversalReferenceId` (UUID, optional): Points to reversing JE if this is a reversal
- `reversedByReferenceId` (UUID, optional): Points to the JE that reversed this one
- `journalLines` (List<JournalEntryLine>, required): Debit/credit postings (see below)
- `createdBy` (UUID, audit): Who created the entry
- `createdAt` (Timestamp, audit)
- `postedBy` (UUID, audit): Who posted the entry
- `version` (Integer, optimistic locking): Incremented on each update

**State Machine:**
- DRAFT: Created, editable, not yet affecting GL account balances
- POSTED: Posted to GL, immutable, affects account balances
- REVERSED: Was reversed by another JE, immutable (appears as inverse balances)

**Immutability Rules:**
- Once POSTED, JournalEntry cannot be edited; only reversed via separate JournalEntry
- Reversal creates inverse JournalEntry with reversalReferenceId pointing to original

**Constraints:**
- All journalLines must balance: SUM(debit lines) = SUM(credit lines)
- No zero-amount lines
- Status POSTED requires postedAt and postedBy

### JournalEntryLine

**Purpose:** Individual debit or credit posting within a JournalEntry.

**Fields:**
- `journalEntryLineId` (UUID, PK): Unique identifier
- `journalEntryId` (UUID, FK): Parent JournalEntry
- `glAccountId` (UUID, FK): Target GL account
- `lineType` (Enum, required): DEBIT or CREDIT
- `amount` (Decimal, required): Posting amount (always positive; sign derived from lineType)
- `dimensions` (JSON, optional): Business dimensions (e.g., `{"location_id": "loc-001", "cost_center": "cc-100"}`)
- `createdAt` (Timestamp, audit)

**Constraints:**
- Referenced glAccountId must have status ACTIVE (can be validated at POST time, not required at DRAFT)
- Dimensions must conform to recognized dimension types (see Dimension Schema below)
- Amount must be > 0 and match currency precision (2 decimal places)

### VendorBill (Accounts Payable)

**Purpose:** Immutable record of vendor invoices and payment history (AP lifecycle).

**Fields:**
- `vendorBillId` (UUID, PK): Unique identifier
- `organizationId` (UUID, FK): Owning organization
- `vendorId` (UUID, FK): Reference to Vendor/Party entity (from people domain)
- `billNumber` (String, required, unique per vendor): Vendor's invoice identifier
- `billDate` (Date, required): Date vendor issued invoice
- `dueDate` (Date, required): Payment due date
- `totalAmount` (Decimal, required): Total invoice amount
- `status` (Enum, required): RECEIVED, APPROVED, REJECTED, PAID, OVERPAID, PARTIALLY_PAID, REVERSED (see state machine below)
- `approvalRequiredAmount` (Decimal, optional): Threshold above which approval is required
- `approvedAt` (Timestamp, optional): When bill was approved for payment
- `approvedBy` (UUID, optional): User who approved
- `rejectionReason` (Text, optional): Why bill was rejected
- `rejectedAt` (Timestamp, optional): When bill was rejected
- `rejectedBy` (UUID, optional): User who rejected
- `paidAmount` (Decimal, derived): Sum of all successful payments applied
- `remainingAmount` (Decimal, derived): totalAmount - paidAmount
- `lastPaymentDate` (Timestamp, optional): Most recent payment application
- `reversalReferenceId` (UUID, optional): If this is a reversal, points to original bill
- `reversedByReferenceId` (UUID, optional): Points to reversing bill if reversed
- `createdBy` (UUID, audit): Who received the bill
- `createdAt` (Timestamp, audit)
- `modifiedBy` (UUID, audit)
- `modifiedAt` (Timestamp, audit)
- `version` (Integer, optimistic locking): Incremented on each update

**State Machine:**
- RECEIVED: Bill received, awaiting approval/rejection decision
- APPROVED: Bill approved, ready for payment processing
- REJECTED: Bill rejected (terminal state for this cycle; can reverse and re-submit)
- PAID: Entire bill amount has been paid
- OVERPAID: Payments exceed bill amount (typically error, requires reversal)
- PARTIALLY_PAID: Some amount paid, balance outstanding
- REVERSED: Original bill reversed, no further payments accepted

**Constraints:**
- Only APPROVED bills can have payments applied
- totalAmount must be positive
- dueDate >= billDate
- Editing allowed only in RECEIVED state; thereafter immutable except for payment applications

## Dimension Schema

**Purpose:** Enables multi-dimensional GL account analysis (e.g., by location, cost center, business unit).

**Recognized Dimensions:**
- `location_id` (UUID): Physical location or facility
- `business_unit_id` (UUID): Organizational business unit
- `cost_center_id` (String): Cost center code
- `department_id` (UUID): Functional department
- `project_id` (UUID): Project/initiative identifier
- `employee_id` (UUID): Employee (for labor-specific postings)
- `customer_id` (UUID): Customer/vendor reference
- `product_id` (UUID): Product/service line reference

**Dimension Constraints:**
- All dimension values must reference valid entities from their respective domains
- Dimensions are optional on JournalEntryLine and GLMapping; NULL dimensions match all values
- Dimension queries must be exact (no range/wildcard matching)

## Business Rules

### Account Activation & Deactivation
- GL accounts must pass validation before activation (no invalid accountType, etc.)
- Once deactivated, account cannot accept new postings
- Accounts with active balance require reversal or roll-forward before archival

### Balance Validation
- All JournalEntries must balance at DRAFT time (rejected if unbalanced)
- Account-level balances derived from posted JournalEntryLines
- Reversals always create inverse-balancing entries

### Effective Dating & Overlap Detection
- PostingRuleSets must not overlap in effectiveFrom/effectiveUntil for same org
- GLMappings within same category must not overlap for identical dimensionConstraints
- MappingKeys must not overlap if from same PostingCategory
- Temporal queries must resolve overlaps deterministically (prefer latest effectiveFrom, then highest priority)

### Immutability & Audit
- All mutations require userId (createdBy, modifiedBy, postedBy, approvedBy, rejectedBy)
- Posting rules cannot be edited once PUBLISHED (must create new version)
- Journal entries cannot be edited once POSTED (reverse instead)
- Vendor bills locked after approval (except payment application)
- All changes logged with timestamp

### Multi-Entity Integrity
- GLMapping.glAccountId must reference ACTIVE GLAccount
- GLMapping.mappingKeyId must reference ACTIVE MappingKey
- JournalEntryLine.glAccountId validated at POST time (must be ACTIVE)
- VendorBill.vendorId must reference valid Vendor/Party (from people domain)
- PostingRuleSet.rulesJson mappingKeys must resolve to active GLMappings
