# Developer Quick Reference: Story #65

## Story: Audit Trail for Price Overrides, Refunds, and Cancellations

**Status**: ✅ Ready for Implementation  
**Domains**: Security (primary), Audit (secondary)  
**Full Story**: See `FINAL_STORY.md` in this directory

---

## Quick Facts

- **Primary Persona**: Compliance Auditor
- **Screens to Create**: 5 new Moqui screens under `apps/pos/screen/audit/`
- **Screens to Modify**: 7 screens to add "View Audit Trail" drilldown links
- **Backend Services Required**: 10 capability endpoints (see Backend Services section)
- **Permissions Required**: 9 permission strings (see Permissions section)

---

## Permissions (AUD-SEC-003)

```
audit:log:view                    # List access
audit:log:view-detail             # Detail access
audit:export:execute              # Export request
audit:export:download             # Export download
audit:pricing-snapshot:view       # Pricing evidence
audit:pricing-trace:view          # Pricing trace
audit:payload:view                # Raw payload (restricted)
audit:proof:view                  # Immutability proof fields (display-only)
audit:scope:cross-location        # Cross-location search
```

---

## Role Mapping

| Role | Permissions |
|------|------------|
| **Compliance Auditor** | view, view-detail, export (execute + download), pricing-snapshot, pricing-trace |
| **Shop Manager** | view, view-detail (no export, no raw payload) |
| **Support/Operations** | view, view-detail; payload only if explicitly granted |

---

## Backend Services Required

### Query
1. `GET /audit/logs/search` — List query (mandatory date range + indexed filter)
2. `GET /audit/logs/detail?eventId=...` — Detail fetch
3. `GET /audit/meta/eventTypes` — Event type dropdown
4. `GET /audit/meta/reasonCodes` — Reason code display metadata
5. `GET /audit/meta/locations` — Location dropdown

### Pricing Evidence
6. `GET /audit/pricing/snapshot?snapshotId=...` — Pricing snapshot
7. `GET /audit/pricing/trace?ruleTraceId=...` — Pricing rule trace (paginated)

### Export
8. `POST /audit/export/request` — Export request (returns exportId)
9. `GET /audit/export/status?exportId=...` — Export status polling
10. `GET /audit/export/download?exportId=...` — Export download

---

## Screens to Create

| Screen Path | Purpose | Key Features |
|------------|---------|--------------|
| `apps/pos/screen/audit/AuditTrail.xml` | Search/list | Mandatory date range, indexed filters, pagination, export action |
| `apps/pos/screen/audit/AuditTrailDetail.xml` | Detail view | Read-only fields, reference links, pricing evidence links |
| `apps/pos/screen/audit/PricingSnapshot.xml` | Pricing snapshot | Read-only, redacted fields, display with currency |
| `apps/pos/screen/audit/PricingTrace.xml` | Pricing rule trace | Read-only, evaluation steps, pagination support |
| `apps/pos/screen/audit/ExportStatus.xml` | Export job status | Status polling, download link when complete |

---

## Screens to Modify (Add Drilldown Link)

Add "View Audit Trail" link/button to:
1. `OrderDetail.xml` → pre-filter to `orderId`
2. `InvoiceDetail.xml` → pre-filter to `invoiceId`
3. `WorkOrderDetail.xml` → pre-filter to `workOrderId`
4. `AppointmentDetail.xml` → pre-filter to `appointmentId`
5. `MechanicDetail.xml` → pre-filter to `mechanicId`
6. `MovementDetail.xml` → pre-filter to `movementId`
7. `EstimateLineDetail.xml` → pre-filter to `productId`/`sku`/`partNumber`

---

## Query Guardrails (AUD-SEC-005)

**Backend enforces; UI mirrors for UX:**
- **Mandatory date range**: `fromUtc` and `toUtc` required
- **Maximum window**: 90 days
- **Mandatory indexed filter**: At least one of:
  - `eventType`, `workOrderId`, `appointmentId`, `mechanicId`, `movementId`, `productId`, `sku`, `partNumber`, `actorId`, `aggregateId`, `correlationId`, `reasonCode`
- **Timezone**: UTC inputs/storage; display in user timezone

---

## Key Entities (Audit Domain)

### AuditLog
- `eventId` (UUIDv7) — dedupe key; used for drilldown
- `eventType` (string) — from controlled vocabulary
- `occurredAt` (datetime w/ timezone)
- `tenantId` (UUIDv7) — enforced server-side
- `locationId` (UUIDv7) — required for scoping
- `actor` (object): `actorType`, `actorId`, `displayName`
- `aggregateType`, `aggregateId`
- `reasonCode`, `reasonNotes`
- `correlationId` — W3C trace correlation
- `rawPayload` — restricted to `audit:payload:view`
- Optional: `hash`, `prevHash`, `signature` (immutability proof)

### PricingSnapshot
- `snapshotId` (UUIDv7)
- `timestamp` (datetime w/ timezone)
- `quoteContext` (redacted per AUD-SEC-004)
- `finalPrice` (money)
- `ruleTraceId` (optional link to trace)

### PricingRuleTrace
- `ruleTraceId` (UUIDv7)
- `evaluationSteps[]`: `ruleId`, `status`, `inputs` (redacted), `outputs` (redacted)
- Pagination: `pageToken`, `nextPageToken`, `isTruncated`

---

## Export Flow

1. **User clicks Export**
2. `POST /audit/export/request` → returns `exportId`
3. **Poll** `GET /audit/export/status?exportId=...` every 2-5 seconds
4. When `status=COMPLETED`, show download link
5. `GET /audit/export/download?exportId=...` → CSV file + SHA-256 digest

---

## Security Constraints

- **Tenant isolation** (AUD-SEC-001): All data tenant-scoped; enforced server-side
- **Location scoping** (AUD-SEC-002): Default to user's current location; cross-location requires `audit:scope:cross-location` + explicit `locationIds[]`
- **Authorization** (AUD-SEC-003): Permission checks before screen rendering and service calls
- **Redaction** (AUD-SEC-004): Raw payload behind permission; redacted fields render as escaped text (never HTML)

---

## Error Handling

| Error | UI Behavior |
|-------|------------|
| Date range missing/invalid | Show inline error "Date range is required and maximum 90 days" |
| No indexed filter | Show inline error "At least one filter required" |
| Date range > 90 days | Show inline error "Maximum date range is 90 days" |
| Unauthorized (403) | Show "You do not have access to Audit Trail" |
| Cross-location denied | Show "Cross-location search requires additional permission" |
| Export too large | Show "Narrow date range and try again" |
| Generic failure (5xx) | Show error + correlation id + retry button |

---

## Testing Checklist

### Unit Tests
- [ ] Service contract validation (mandatory date range, indexed filter, max 90-day window)
- [ ] Permission checks (verify screens enforce correct permissions)
- [ ] Timezone display, currency formatting, reason code mapping

### Integration Tests
- [ ] Search with various filter combinations
- [ ] Deep-link from Order/Invoice/Work Order to Audit Trail
- [ ] Export job: request → poll → download
- [ ] Pricing evidence drilldown: snapshot → trace
- [ ] Authorization: unauthorized blocked, authorized succeeds

### End-to-End Tests
- [ ] Compliance Auditor workflow: navigate → search → detail → pricing evidence → export
- [ ] Shop Manager workflow: navigate → search → detail (no export)
- [ ] Cross-location search with permission
- [ ] Date range validation error handling
- [ ] Empty state display

---

## Moqui Implementation Patterns

### Permission Check
```xml
<screen>
  <actions>
    <if condition="!ec.user.hasPermission('audit:log:view')">
      <return error="true" message="You do not have access to Audit Trail"/>
    </if>
  </actions>
  <!-- screen content -->
</screen>
```

### Date Range Input
```xml
<field name="fromUtc">
  <default-field><date-time type="date" format="yyyy-MM-dd"/></default-field>
</field>
<field name="toUtc">
  <default-field><date-time type="date" format="yyyy-MM-dd"/></default-field>
</field>
```

### Conditional Field (Raw Payload)
```xml
<field name="rawPayload">
  <conditional-field condition="ec.user.hasPermission('audit:payload:view')">
    <display text="${rawPayload}" encode="true"/>
  </conditional-field>
</field>
```

### Deep-Link (from Order Detail)
```xml
<transition name="viewAuditTrail">
  <default-response url="../audit/AuditTrail">
    <parameter name="orderId" from="orderId"/>
    <parameter name="fromUtc" from="now().minusDays(90).format('yyyy-MM-dd')"/>
    <parameter name="toUtc" from="now().format('yyyy-MM-dd')"/>
  </default-response>
</transition>
```

---

## Acceptance Criteria Summary

12 scenarios covering:
1. Auditor views list with mandatory filters
2. Filter by order drilldown
3. View detail with pricing evidence
4. Append-only enforcement
5. Export report (async job)
6. Unauthorized user blocked
7. Cross-location search with permission
8. Cross-location search without permission
9. Date range validation
10. Missing indexed filter validation
11. Immutability proof fields display
12. Raw payload restricted access

---

## References

- **Full Story**: `FINAL_STORY.md` (599 lines)
- **Handoff Summary**: `SUMMARY.md`
- **Domain Business Rules**: `durion/domains/audit/.business-rules/AGENT_GUIDE.md`
- **Security Domain**: `durion/.github/agents/domains/security-domain.agent.md`
- **Audit Domain**: `durion/.github/agents/domains/audit-domain.agent.md`

---

**Ready to implement!** 🚀
