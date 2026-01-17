Title: [BACKEND] [STORY] Accounting: Reconcile POS Status with Accounting Authoritative Status
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/5
Labels: type:story, domain:accounting, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:ready-for-dev

### Recommended
- agent:accounting
- agent:story-authoring

---

## Story Intent

Enable **Cashiers and Service Advisors** to view the **authoritative accounting status** of invoices and transactions directly within the POS interface, ensuring that displayed status (pending posting, posted, reconciled) reflects the accounting system's system-of-record state, thereby reducing customer disputes, internal confusion, and manual reconciliation effort.

This story establishes **event-driven synchronization** between the POS transaction layer and the accounting posting layer, making accounting the single source of truth for financial status.

---

## Actors & Stakeholders

### Primary Actors
- **Cashier**: Views invoice accounting status when handling customer inquiries or resolving payment disputes
- **Service Advisor**: Views accounting status for orders and invoices during follow-up or collections
- **Accounting System**: System of record for financial posting status; emits authoritative status events
- **POS Status Synchronizer Service**: Subscribes to accounting events and updates local POS status cache

### Secondary Stakeholders
- **Accounting Team**: Monitors reconciliation metrics and investigates discrepancies
- **Customer**: Benefits indirectly from accurate status display (e.g., knows when payment has cleared)
- **Audit Service**: Logs all status synchronization events for compliance and troubleshooting

---

## Preconditions

1. Invoice exists in POS with local status (e.g., "submitted", "pending accounting")
2. Accounting system has received invoice and processed it (or is processing)
3. Accounting system is configured to emit `InvoiceStatusChanged` and `PostingConfirmed` events to event bus
4. POS Status Synchronizer Service is subscribed to accounting event topics
5. User has permission to view invoice details in POS

---

## Functional Behavior

### 1. Receive and Process Accounting Status Events

**Trigger**: Accounting system emits `InvoiceStatusChanged` or `PostingConfirmed` event

**Flow**:
1. Accounting system posts event to event bus with:
   - Invoice ID
   - New accounting status (e.g., "PENDING_POSTING", "POSTED", "RECONCILED", "REJECTED")
   - Posting reference (GL entry ID, batch ID)
   - Timestamp
   - Event type
2. POS Status Synchronizer Service receives event
3. Service validates event payload (invoice ID exists, status is recognized)
4. Service updates local POS status record:
   - `accountingStatus`: New status from event
   - `accountingStatusUpdatedAt`: Event timestamp
   - `postingReference`: Drilldown reference to accounting system
   - `lastSyncEvent`: Event ID for traceability
5. Service logs synchronization event to audit log
6. Service publishes internal POS notification (optional): "Invoice X status updated to POSTED"

**Outcome**: POS local status is synchronized with accounting authoritative status

---

### 2. Display Authoritative Accounting Status in POS

**Trigger**: Cashier or Service Advisor views invoice details in POS

**Flow**:
1. User opens invoice detail screen
2. POS retrieves invoice record including:
   - Local POS status (e.g., "PAYMENT_RECEIVED")
   - Accounting status (e.g., "POSTED")
   - Accounting status timestamp
   - Posting reference
3. POS displays accounting status prominently:
   - Status badge: "Posted to Accounting" (green) or "Pending Posting" (yellow)
   - Last updated timestamp: "Updated 2 minutes ago"
   - If discrepancy exists between local POS status and accounting status, show both with warning icon
4. User can click drilldown link to view accounting system posting details (if permissions allow)

**Outcome**: User sees authoritative accounting status without needing to check accounting system separately

---

### 3. Provide Drilldown to Accounting System

**Trigger**: User clicks "View in Accounting" or drilldown link on invoice detail screen

**Flow**:
1. POS retrieves posting reference from invoice record (e.g., GL entry ID, batch ID)
2. POS constructs deep link to accounting system (e.g., `https://accounting.example.com/entries/{entryId}`)
3. POS opens link in new tab/window or embedded iframe (depending on UX)
4. Accounting system authenticates user (SSO or session passthrough)
5. Accounting system displays posting detail (debit/credit lines, posting date, status, audit trail)

**Outcome**: User can drill down to accounting detail without manually searching or context-switching

---

### 4. Handle Status Discrepancies (POS vs Accounting)

**Trigger**: Accounting status and local POS status do not match expected progression

**Flow**:
1. POS detects discrepancy:
   - Example: Local POS status is "PAYMENT_RECEIVED" but accounting status is "REJECTED"
2. POS displays warning to user:
   - "⚠️ Status Mismatch: POS shows payment received, but accounting rejected posting. See details."
3. User views details:
   - POS status: PAYMENT_RECEIVED (updated 10 minutes ago)
   - Accounting status: REJECTED (updated 5 minutes ago)
   - Reason: "Invoice total does not match payment total"
4. User can escalate to supervisor or accounting team for resolution
5. System logs discrepancy event to audit log with both statuses, timestamps, and user who viewed it

**Outcome**: Discrepancies are surfaced to users immediately, not discovered during end-of-day reconciliation

---

## Alternate / Error Flows

### 1. Accounting Event Lost or Delayed

**Trigger**: Accounting system emits event but POS never receives it (network issue, event bus failure)

**Flow**:
1. POS has local status "PENDING_POSTING" with `accountingStatusUpdatedAt` timestamp
2. After configurable timeout (e.g., 1 hour), POS detects stale accounting status
3. POS Status Synchronizer Service polls accounting API for current status (fallback mechanism)
4. Service updates local status based on polling result
5. If polling also fails, system logs critical alert and displays "Accounting status unavailable" in UI
6. User can manually request sync or contact accounting team

---

### 2. Accounting Status Event for Unknown Invoice

**Trigger**: Accounting system emits event for invoice ID that does not exist in POS

**Flow**:
1. POS Status Synchronizer Service receives event with unknown invoice ID
2. Service logs warning: "Received accounting event for unknown invoice: {invoiceId}"
3. Service checks if invoice was deleted or archived
4. If not found in any POS state:
   - Service publishes dead-letter event
   - Service alerts monitoring system for manual investigation
5. Event is not retried (avoids infinite retry loop)

**Outcome**: Orphaned events do not cause system instability

---

### 3. User Lacks Permission to View Accounting Status

**Trigger**: User opens invoice detail but does not have permission to view accounting data

**Flow**:
1. POS checks user permissions
2. If `VIEW_ACCOUNTING_STATUS` permission is missing:
   - POS hides accounting status section
   - POS shows message: "Accounting details restricted. Contact supervisor."
3. Local POS status is still visible
4. Access attempt is logged to audit log

---

### 4. Accounting System Emits Conflicting Events

**Trigger**: Accounting system emits two events for same invoice with conflicting statuses (e.g., "POSTED" followed by "PENDING_POSTING")

**Flow**:
1. POS Status Synchronizer Service receives first event: "POSTED"
2. Service updates local status to "POSTED"
3. Service receives second event: "PENDING_POSTING" with earlier timestamp
4. Service detects timestamp conflict (second event is older than current state)
5. Service ignores older event and logs warning: "Received out-of-order event for invoice {invoiceId}"
6. Service alerts monitoring for accounting event ordering issue

**Outcome**: Out-of-order events do not cause status regression

---

### 5. Drilldown Link to Accounting System Fails

**Trigger**: User clicks drilldown link but accounting system is unavailable or returns error

**Flow**:
1. User clicks "View in Accounting"
2. POS attempts to open deep link to accounting system
3. Accounting system returns 503 Service Unavailable or times out
4. POS displays error message: "Accounting system temporarily unavailable. Try again later."
5. POS copies posting reference to clipboard for manual lookup
6. Error is logged to monitoring system

---

## Business Rules

### 1. Accounting Status is Authoritative (Conflict Resolution Rule)

When POS local status and accounting status conflict, **accounting status is the headline** for financial decision-making.

POS local status represents internal workflow state (e.g., "invoice created", "payment received").
Accounting status represents financial posting state (e.g., "posted to GL", "reconciled with bank").

If accounting status is "REJECTED", "REVERSED", "VOIDED", or "ON_HOLD", POS MUST surface this to users immediately with error banner, requiring resolution workflow. POS does not silently continue.

**Manual override is not a status change:** It is an exception record (`AccountingStatusException`) with reason, approver, timestamp, and full audit trail. Accounting remains source of truth; exception only affects POS workflow routing.

---

### 2. Status Progression Rules

**POS-facing Accounting Status Enum (v1 - stable contract):**

1. **PENDING_POSTING**: Accepted by accounting intake; not yet posted to GL.
2. **POSTED**: GL entries created and committed.
3. **RECONCILED**: Matched to bank statement / settlement source (or payment gateway settlement) per accounting rules.
4. **REJECTED**: Accounting rejected intake or posting (validation failure, imbalanced, closed period, etc.).
5. **REVERSED**: A posted entry was reversed (credit memo/reversal journal).
6. **VOIDED**: Invoice voided in accounting (no longer collectible; may or may not have prior posting).
7. **ON_HOLD**: Held for approval/credit/exception; no posting progression until released.
8. **DISPUTED**: Flagged as in dispute (workflow state; posting may already exist).

**Lifecycle constraints (POS-facing):**

- Typical: `PENDING_POSTING → POSTED → RECONCILED`
- Exceptional transitions allowed:
  - `PENDING_POSTING → REJECTED`
  - `POSTED → REVERSED` (and optionally `→ RECONCILED` prior to reversal)
  - Any state `→ ON_HOLD` (administrative hold), and `ON_HOLD → PENDING_POSTING|POSTED|REJECTED` (release outcome)
  - Any state `→ DISPUTED` (flag), `DISPUTED → {prior state}` not allowed; instead emit new authoritative status with reason
  - `VOIDED` is terminal from POS perspective (still retained for audit)

Backward progression (e.g., POSTED → PENDING_POSTING) is **abnormal** and MUST trigger alert.

---

### 3. Drilldown Permissions (Three-Tier Model)

**Tier 1: VIEW_ACCOUNTING_STATUS**
- May see accounting status badge, timestamp, and high-level reason (if REJECTED/ON_HOLD/DISPUTED).
- Default: Cashier, Service Advisor, Manager, Supervisor.

**Tier 2: REFRESH_ACCOUNTING_STATUS**
- May trigger manual polling refresh.
- Default: Cashier, Manager, Supervisor.

**Tier 3: VIEW_ACCOUNTING_DETAIL**
- May use drilldown link, see postingReference fields (GL entry/batch IDs), and access deep link.
- Default: Accounting role, Manager, Supervisor only.

**UI Behavior:**
- If user lacks VIEW_ACCOUNTING_STATUS: hide accounting section entirely.
- If user has status but not detail: show badge and timestamp; hide GL identifiers and drilldown link.

---

### 4. Event Idempotency and Delivery Guarantee

**Delivery guarantee:** At-least-once delivery with idempotent consumer.

**Idempotency key:** `eventId` (UUID) + `invoiceId`. Store a processed-event table with retention ≥ 30 days (configurable) to deduplicate.

**Retry policy (consumer):**
- If processing fails due to transient errors (DB/redis/network): retry with exponential backoff (e.g., 1m, 5m, 15m) up to N=10 attempts.
- After max attempts: send to **DLQ** with full payload and error metadata; alert.

**Maximum event age:**
- Process events up to 7 days old (for recovery), but mark `metadata.lateEvent=true` and raise metric.

---

### 5. Stale Status Detection and Fallback Polling

If `accountingStatusUpdatedAt` is older than **1 hour** (default, configurable), POS MUST:
- Display staleness indicator: "Last synced 2 hours ago"
- Offer manual refresh button
- Log staleness metric for monitoring

**Fallback polling triggers:**
- If event bus outage detected (no events consumed for topic for >10 minutes), enable background polling for "recently active invoices" (last 24h) at a low rate to reduce drift.

Cashiers SHOULD be trained to manually refresh stale status before making payment decisions.

---

### 6. Audit Trail Requirements

Every status synchronization event MUST be logged to audit log with:
- Invoice ID
- Old status (before event)
- New status (from event)
- Event timestamp
- Event ID
- User who viewed status (if applicable)
- Any discrepancies detected
- Sync source (EVENT, POLLING, MANUAL_REFRESH)
- Latency (event timestamp to sync completion)

Audit logs MUST be retained for minimum 7 years for financial compliance.

---

### 7. Archive-Not-Delete Policy

**POS policy:** Invoices are **archived**, not deleted, once issued/paid/closed.

**Status synchronizer:** Must update accounting status for **archived invoices** as well (same table/partition).

**Retention:** Keep invoice + accounting status + audit trail for **7 years**. If jurisdiction requires longer, make retention configurable; default remains 7 years.

**Unknown invoice events:**
- If invoiceId not found in active table, check archive store.
- If not found anywhere: send to DLQ as orphan event + alert.

---

## Data Requirements

### PostingConfirmation (Event Payload from Accounting)

```json
{
  "eventId": "string (UUID)",
  "eventType": "enum (INVOICE_STATUS_CHANGED, POSTING_CONFIRMED)",
  "invoiceId": "string (UUID)",
  "accountingStatus": "enum (PENDING_POSTING, POSTED, RECONCILED, REJECTED, REVERSED, VOIDED, ON_HOLD, DISPUTED)",
  "postingReference": {
    "glEntryId": "string (UUID)",
    "batchId": "string",
    "postingDate": "date (ISO 8601)"
  },
  "timestamp": "timestamp (ISO 8601)",
  "reason": "string (optional, e.g., rejection reason)",
  "nativeStatus": "string (optional, for diagnostics only; POS ignores)",
  "metadata": {
    "accountingSystemVersion": "string",
    "processedBy": "string (accounting system user or job)"
  }
}
```

---

### InvoiceAccountingStatus (POS Local Model)

```json
{
  "invoiceId": "string (UUID)",
  "posLocalStatus": "enum (CREATED, PAYMENT_RECEIVED, SUBMITTED_TO_ACCOUNTING, CLOSED)",
  "accountingStatus": "enum (PENDING_POSTING, POSTED, RECONCILED, REJECTED, REVERSED, VOIDED, ON_HOLD, DISPUTED)",
  "accountingStatusUpdatedAt": "timestamp (ISO 8601)",
  "postingReference": {
    "glEntryId": "string (UUID)",
    "batchId": "string",
    "postingDate": "date",
    "drilldownUrl": "string (deep link to accounting system)"
  },
  "lastSyncEvent": "string (UUID, event ID for traceability)",
  "discrepancyDetected": "boolean",
  "discrepancyReason": "string (optional)"
}
```

---

### AccountingStatusSyncAuditLog (Audit Model)

```json
{
  "auditId": "string (UUID)",
  "invoiceId": "string (UUID)",
  "eventId": "string (UUID)",
  "oldStatus": "string",
  "newStatus": "string",
  "syncedAt": "timestamp (ISO 8601)",
  "discrepancyDetected": "boolean",
  "viewedByUserId": "string (UUID, optional)",
  "metadata": {
    "syncSource": "enum (EVENT, POLLING, MANUAL_REFRESH)",
    "latencyMs": "integer (event timestamp to sync completion)"
  }
}
```

---

## Acceptance Criteria

### AC1: Accounting Status Overrides Local POS State in Display

**Given** an invoice has POS local status "PAYMENT_RECEIVED"  
**And** accounting system emits `PostingConfirmed` event with status "POSTED"  
**When** the POS Status Synchronizer Service processes the event  
**Then** the invoice's `accountingStatus` field is updated to "POSTED"  
**And** when a user views the invoice, the accounting status badge displays "Posted to Accounting" prominently  
**And** the local POS status is still visible but secondary

---

### AC2: Pending vs Posted States Are Clearly Displayed

**Given** an invoice has accounting status "PENDING_POSTING"  
**When** a Cashier views the invoice detail screen  
**Then** the accounting status badge displays "Pending Posting" with a yellow indicator  
**And** the badge shows last updated timestamp (e.g., "Updated 5 minutes ago")  
**When** the accounting status changes to "POSTED"  
**And** the Cashier refreshes or reopens the invoice  
**Then** the badge displays "Posted" with a green indicator

---

### AC3: Drilldown to Accounting System Works with Permission Check

**Given** an invoice has been posted to accounting with `glEntryId: 12345`  
**And** the user has `VIEW_ACCOUNTING_DETAIL` permission  
**When** the user clicks "View in Accounting" on the invoice detail screen  
**Then** a new browser tab opens with the accounting system's GL entry detail page for entry 12345  
**And** the user is automatically authenticated via SSO (no login prompt)

**Given** the user lacks `VIEW_ACCOUNTING_DETAIL` permission  
**When** the user views the invoice detail screen  
**Then** the "View in Accounting" link is hidden; accounting status badge remains visible

---

### AC4: Status Discrepancies Are Surfaced to User

**Given** an invoice has POS local status "PAYMENT_RECEIVED"  
**And** accounting system emits event with status "REJECTED" and reason "Invoice total mismatch"  
**When** the POS Status Synchronizer Service processes the event  
**Then** the invoice's `discrepancyDetected` flag is set to true  
**And** the `discrepancyReason` is populated with "Invoice total mismatch"  
**When** a user views the invoice  
**Then** a warning banner is displayed: "⚠️ Status Mismatch: Accounting rejected this invoice. Reason: Invoice total mismatch."  
**And** both POS and accounting statuses are shown side-by-side for comparison

---

### AC5: Audit Log Captures All Synchronization Events

**Given** accounting system emits `InvoiceStatusChanged` event for invoice 456  
**When** the POS Status Synchronizer Service processes the event  
**Then** an audit log entry is created with:  
  - `invoiceId: 456`  
  - `oldStatus: PENDING_POSTING`  
  - `newStatus: POSTED`  
  - `eventId:` (from event)  
  - `syncedAt:` (current timestamp)  
**And** the audit log is persisted to the audit database

---

### AC6: Stale Accounting Status Triggers Fallback Polling

**Given** an invoice's `accountingStatusUpdatedAt` timestamp is 2 hours old  
**And** the staleness threshold is configured to 1 hour  
**When** a user views the invoice  
**Then** the system displays "Accounting status may be outdated (last synced 2 hours ago)"  
**And** a "Refresh Status" button is displayed  
**When** the user clicks "Refresh Status"  
**Then** the POS Status Synchronizer Service polls the accounting API for current status  
**And** the invoice's accounting status is updated with the latest value  
**And** the user sees the updated status immediately

---

### AC7: Out-of-Order Events Are Ignored

**Given** an invoice has accounting status "POSTED" with timestamp "2026-01-05T10:00:00Z"  
**When** the POS Status Synchronizer Service receives an event with status "PENDING_POSTING" and timestamp "2026-01-05T09:55:00Z" (earlier)  
**Then** the service ignores the event because it is older than current state  
**And** the invoice's accounting status remains "POSTED"  
**And** a warning is logged: "Ignored out-of-order event for invoice {invoiceId}"

---

### AC8: Critical Status Changes Sync Within SLA

**Given** accounting system emits event with status in [POSTED, REJECTED, REVERSED, VOIDED]  
**When** the POS Status Synchronizer Service processes the event  
**Then** the invoice's accounting status is updated within **p95 < 5 seconds**, **p99 < 30 seconds**

**Given** accounting system emits event with status in [PENDING_POSTING, ON_HOLD, DISPUTED, RECONCILED]  
**When** the POS Status Synchronizer Service processes the event  
**Then** the invoice's accounting status is updated within **p95 < 30 seconds**, **p99 < 2 minutes**

---

## Audit & Observability

### Required Audit Events

1. **AccountingStatusSynchronized**
   - Timestamp, invoice ID, old status, new status, event ID, sync source (event/polling), latency

2. **StatusDiscrepancyDetected**
   - Timestamp, invoice ID, POS status, accounting status, discrepancy reason, viewing user ID

3. **DrilldownLinkAccessed**
   - Timestamp, user ID, invoice ID, accounting reference (GL entry ID), success/failure

4. **StaleStatusDetected**
   - Timestamp, invoice ID, last sync timestamp, staleness duration (hours), user action (viewed/refreshed)

5. **AccountingEventIgnored**
   - Timestamp, event ID, invoice ID, reason (duplicate, out-of-order, unknown invoice)

6. **FallbackPollingTriggered**
   - Timestamp, invoice ID, reason (event loss, staleness), polling result (success/failure)

---

### Metrics to Track

- **Status Sync Latency**: Time from accounting event emission to POS status update (target: p95 <5s for critical, p95 <30s for non-critical)
- **Event Processing Rate**: Events processed per minute
- **Discrepancy Rate**: Percentage of invoices with POS/accounting status mismatch (target: <1%)
- **Stale Status Count**: Number of invoices with outdated accounting status at any given time (target: <10)
- **Fallback Polling Success Rate**: Percentage of manual refresh requests that succeed (target: >95%)
- **Drilldown Link Failures**: Count of failed accounting system deep links per day (target: <5)
- **Out-of-Order Event Count**: Count of ignored out-of-order events (should be near zero)
- **DLQ Event Count**: Events sent to dead-letter queue per day (target: <5)

---

## Resolved Open Questions Summary

✅ **Q1 - Accounting Status Enum (v1 contract):** PENDING_POSTING, POSTED, RECONCILED, REJECTED, REVERSED, VOIDED, ON_HOLD, DISPUTED with defined lifecycle constraints.

✅ **Q2 - Conflict Resolution:** Accounting is authoritative for financial posting; POS operational state separate. Manual overrides create exception records, not status changes. Accounting remains source of truth.

✅ **Q3 - Event Delivery Guarantee:** At-least-once delivery with idempotent processing. Retry up to 10x with exponential backoff; DLQ after max attempts. 7-day max event age. Fallback polling after 1 hour staleness or event bus outage.

✅ **Q4 - Permissions (Three-Tier):** VIEW_ACCOUNTING_STATUS (badge only), REFRESH_ACCOUNTING_STATUS (manual refresh), VIEW_ACCOUNTING_DETAIL (drilldown + GL refs). Different defaults per role.

✅ **Q5 - Drilldown Scope:** Posting summary in POS (GL entry ID, posting date, batch ID, amount, counterparty, processor); full detail via SSO deep link to accounting system.

✅ **Q6 - Archive-Not-Delete Policy:** Invoices archived, not deleted. Status synchronizer updates archived invoices too. 7-year retention. Unknown invoices checked in archive store before DLQ.

✅ **Q7 - Latency SLA (Two-Tier):** Critical statuses (POSTED, REJECTED, REVERSED, VOIDED) p95 <5s, p99 <30s. Non-critical (PENDING_POSTING, ON_HOLD, DISPUTED, RECONCILED) p95 <30s, p99 <2min.

---

## Original Story (Unmodified – For Traceability)

# Issue #5 — [BACKEND] [STORY] Accounting: Reconcile POS Status with Accounting Authoritative Status

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Reconcile POS Status with Accounting Authoritative Status

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Cashier**, I want POS to reflect accounting's authoritative status so that disputes are minimized.

## Details
- Show "pending posting" vs "posted."
- Provide drilldown refs.

## Acceptance Criteria
- Accounting status overrides local state.
- Pending/posted states clear.
- Audit of reconciliation.

## Integrations
- Accounting emits InvoiceStatusChanged/PostingConfirmed events.

## Data / Entities
- PostingConfirmation, InvoiceStatusChangedEvent, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

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