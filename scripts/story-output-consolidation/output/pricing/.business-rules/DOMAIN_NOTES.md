# PRICING_DOMAIN_NOTES.md

## Summary

This document is the non-normative rationale and decision log for the Pricing domain. It explains why the normative decisions in `AGENT_GUIDE.md` were chosen, what alternatives were considered, and what auditors/architects should inspect to verify correctness. It is intended for architecture review, governance, and long-term maintainability.

## Completed items

- [x] Linked each Decision ID to a detailed rationale

## Decision details

### DECISION-PRICING-001 — Money representation and rounding

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Money values are represented as `{ amount: "<decimal-string>", currencyUomId: "USD" }`; rounding rules are backend-owned and UI must not recompute totals.
- Alternatives considered:
  - Option A (chosen): Decimal-string + `currencyUomId` (Moqui-aligned)
  - Option B: Integer minor-units (cents)
- Reasoning and evidence:
  - Avoids floating-point issues and keeps UI display deterministic.
  - Currency code is required to avoid implicit defaults.
- Architectural implications:
  - All pricing endpoints use the same money DTO.
  - Contract tests validate money shape for quote/snapshot/promotion results.
- Auditor-facing explanation:
  - Inspect that stored snapshots contain the currency code and that UI never re-derives extended totals.
- Migration & backward-compatibility notes:
  - If any endpoints currently use `{ amount, currency }`, add a compatibility mapper until clients are updated.
- Governance & owner recommendations:
  - Pricing owns the money DTO for pricing endpoints; changes require cross-domain review.

### DECISION-PRICING-002 — Pricing API base path and versioning

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: All Pricing REST APIs are published under `/pricing/v1/...`.
- Alternatives considered:
  - Option A (chosen): Dedicated `pricing` namespace
  - Option B: Mixed service paths per feature
- Reasoning and evidence:
  - Predictable routing for gateway, observability, and client configuration.
- Architectural implications:
  - Gateway routing and frontend API clients must use the single base path.
- Auditor-facing explanation:
  - Inspect gateway route configs and service discovery for the `pricing` prefix.
- Migration & backward-compatibility notes:
  - Provide temporary redirects for legacy `/v1/...` paths if they exist.
- Governance & owner recommendations:
  - New endpoints require a versioned path and documented contract.

### DECISION-PRICING-003 — Effective dating and timezone semantics

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Effective-dated timestamps are stored in UTC but interpreted in store-local timezone for “as-of” evaluation; timestamp ranges are half-open `[start, end)`.
- Alternatives considered:
  - Option A (chosen): Store-local evaluation
  - Option B: UTC evaluation everywhere
- Reasoning and evidence:
  - Store operations are anchored to the store’s calendar; UTC-only leads to off-by-one “day” bugs.
- Architectural implications:
  - UI must show the store timezone and use date-time pickers for timestamp-effective fields.
- Auditor-facing explanation:
  - Inspect a rule that starts/ends on a boundary and confirm evaluation matches store-local time.
- Migration & backward-compatibility notes:
  - If historical records were authored as UTC, keep their meaning by storing original timezone metadata when possible.
- Governance & owner recommendations:
  - Any change in timezone semantics requires explicit ADR.

### DECISION-PRICING-004 — Price book scope model (location/tier/currency)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: A PriceBook has explicit scope fields: `locationId` optional, `customerTierId` optional, and `currencyUomId` required (single-currency per book).
- Alternatives considered:
  - Option A (chosen): Scope fields on PriceBook
  - Option B: Encode scope entirely as rule conditions
- Reasoning and evidence:
  - Scoped books reduce complexity for rule authoring and evaluation.
- Architectural implications:
  - Quote evaluation selects the “most specific” applicable book by scope before evaluating rules.
- Auditor-facing explanation:
  - Inspect that quotes record which book and scope were used.
- Migration & backward-compatibility notes:
  - Backfill default (global) book for existing tenants.
- Governance & owner recommendations:
  - Pricing owns book scope taxonomy.

### DECISION-PRICING-005 — Price book rule condition model (single-dimension)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: A rule may express at most one condition dimension (location OR tier OR none); combined conditions are modeled via scoped books.
- Alternatives considered:
  - Option A (chosen): Single-dimension rules
  - Option B: Multi-dimensional condition expression in a rule
- Reasoning and evidence:
  - Keeps UI editors and evaluation deterministic.
- Architectural implications:
  - Admin UI must not allow authors to create multi-condition rules.
- Auditor-facing explanation:
  - Inspect a combined scope use-case and confirm it is modeled with scoped book(s).
- Migration & backward-compatibility notes:
  - For existing multi-condition rules, split into scoped books.
- Governance & owner recommendations:
  - Changes to this model require a compatibility plan for existing rules.

### DECISION-PRICING-006 — Rule precedence and deterministic tie-breakers

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Rule ordering is deterministic: (1) target specificity (SKU > CATEGORY > GLOBAL), then (2) higher priority, then (3) stable tie-breaker by ruleId ascending.
- Alternatives considered:
  - Option A (chosen): Stable tie-breaker with ruleId
  - Option B: Latest-updated wins
- Reasoning and evidence:
  - Stable ordering prevents “random” pricing in presence of authoring mistakes.
- Architectural implications:
  - Backend must log the winning rule and tie-breaker path.
- Auditor-facing explanation:
  - Inspect quote breakdown showing evaluated rules and the selected winner.
- Migration & backward-compatibility notes:
  - Add linting checks to reject ambiguous rules at authoring time.
- Governance & owner recommendations:
  - Maintain a “rule authoring” playbook for analysts.

### DECISION-PRICING-007 — Promotion code constraints and uniqueness

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Promotion codes are globally unique (case-insensitive) and immutable once created; UI validates length (1–32) and charset `[A-Z0-9_-]`.
- Alternatives considered:
  - Option A (chosen): Constrained, case-insensitive codes
  - Option B: Free-form codes
- Reasoning and evidence:
  - Prevents confusing customer experiences and reduces support issues.
- Architectural implications:
  - Backend provides stable error codes for invalid/duplicate codes.
- Auditor-facing explanation:
  - Inspect duplicate-code prevention and audit trails for promotion creation.
- Migration & backward-compatibility notes:
  - Existing codes outside charset remain valid but are not allowed for new creation.
- Governance & owner recommendations:
  - Marketing owns naming conventions; Pricing enforces constraints.

### DECISION-PRICING-008 — Single promotion per estimate behavior

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: At most one promotion is active on an estimate at a time; applying a new promotion replaces the existing one (backend-authoritative, idempotent by estimate+code).
- Alternatives considered:
  - Option A (chosen): Replace-by-default
  - Option B: Block when one already applied
- Reasoning and evidence:
  - Simplifies totals and avoids stacking ambiguity.
- Architectural implications:
  - Backend returns a single “applied promotion” descriptor and adjustments.
- Auditor-facing explanation:
  - Inspect estimate pricing history showing replaced promotion.
- Migration & backward-compatibility notes:
  - If stacking existed, introduce a migration plan and UI messaging.
- Governance & owner recommendations:
  - Policy changes require approval from Finance/Marketing.

### DECISION-PRICING-009 — Promotion eligibility rule evaluation model

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Eligibility rules evaluate with AND semantics by default; a promotion with zero eligibility rules is eligible for all contexts.
- Alternatives considered:
  - Option A (chosen): AND + no-rules eligible
  - Option B: OR
  - Option C: No-rules ineligible
- Reasoning and evidence:
  - AND is conservative when rules exist; no-rules eligible matches “unrestricted promotion” intent.
- Architectural implications:
  - Backend returns `isEligible` plus `reasonCode` when not eligible.
- Auditor-facing explanation:
  - Inspect eligibility evaluations and reason codes for denied applications.
- Migration & backward-compatibility notes:
  - If behavior differs today, mark as `safe_to_defer: true` until confirmed.
- Governance & owner recommendations:
  - Eligibility logic changes require governance review due to customer-impact.

### DECISION-PRICING-010 — Restriction decisions and override eligibility

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Restriction evaluate returns only: `ALLOW`, `BLOCK`, `ALLOW_WITH_OVERRIDE`; only `ALLOW_WITH_OVERRIDE` is override-eligible.
- Alternatives considered:
  - Option A (chosen): Override eligibility encoded as a distinct decision
  - Option B: Allow overrides on `BLOCK` with a flag
- Reasoning and evidence:
  - Prevents bypassing hard safety or compliance restrictions.
- Architectural implications:
  - UI shows override controls only when decision is `ALLOW_WITH_OVERRIDE`.
- Auditor-facing explanation:
  - Inspect that overrides exist only for override-eligible decisions.
- Migration & backward-compatibility notes:
  - Map any legacy “BLOCK but overrideable” flags to `ALLOW_WITH_OVERRIDE`.
- Governance & owner recommendations:
  - Restriction decision taxonomy is governance-controlled.

### DECISION-PRICING-011 — Override reason codes catalog source

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Override reason codes are fetched from a backend catalog endpoint; UI never hardcodes codes.
- Alternatives considered:
  - Option A (chosen): Backend catalog endpoint
  - Option B: Frontend static list
- Reasoning and evidence:
  - Codes change under governance; dynamic fetch reduces release coupling.
- Architectural implications:
  - Add caching and offline-safe behavior (disable override if catalog cannot load).
- Auditor-facing explanation:
  - Inspect reason code catalog changes and approval process.
- Migration & backward-compatibility notes:
  - Provide a default “OTHER” code only if backend explicitly includes it.
- Governance & owner recommendations:
  - Pricing/Compliance own the catalog.

### DECISION-PRICING-012 — Restriction override request shape and transactionId requirement

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Override requests require `transactionId`, `policyVersion`, selected `ruleId` (when multiple candidates exist), `overrideReasonCode`, and `notes`.
- Alternatives considered:
  - Option A (chosen): Require transactionId and policyVersion
  - Option B: Allow pre-transaction overrides
- Reasoning and evidence:
  - Ensures overrides are traceable and not stale.
- Architectural implications:
  - UI must disable override until a transaction exists; backend returns conflict on policyVersion mismatch.
- Auditor-facing explanation:
  - Inspect override records linked to transactions and their policy version.
- Migration & backward-compatibility notes:
  - If existing overrides lack transaction linkage, backfill or mark as legacy.
- Governance & owner recommendations:
  - Override workflow changes require security domain review.

### DECISION-PRICING-013 — Pricing snapshot contract and UX pattern

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Snapshot drilldown is read-only and opens in a modal/drawer; response includes breakdown sufficient for explainability.
- Alternatives considered:
  - Option A (chosen): Modal/drawer drilldown
  - Option B: Dedicated route
- Reasoning and evidence:
  - Preserves editing context and reduces navigation errors.
- Architectural implications:
  - UI must handle 403/404/5xx explicitly with safe states.
- Auditor-facing explanation:
  - Inspect snapshot immutability and trace to quote inputs.
- Migration & backward-compatibility notes:
  - If deep-linking becomes required, add a route later without breaking modal flow.
- Governance & owner recommendations:
  - Snapshots are compliance-relevant; changes require audit agent review.

### DECISION-PRICING-014 — Snapshot authorization and sensitive-field redaction

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Snapshot view permission is inherited from document access; sensitive fields (e.g., cost) are redacted unless explicit permission exists.
- Alternatives considered:
  - Option A (chosen): Inherited access + redaction
  - Option B: Dedicated snapshot permission
- Reasoning and evidence:
  - Prevents authorization drift and minimizes leaks.
- Architectural implications:
  - Backend must support field-level redaction or omit sensitive fields.
- Auditor-facing explanation:
  - Inspect that unauthorized roles cannot retrieve cost.
- Migration & backward-compatibility notes:
  - Introduce a `pricing:cost:view` permission as needed.
- Governance & owner recommendations:
  - Any new sensitive field must specify a redaction policy.

### DECISION-PRICING-015 — Audit/history contract for pricing admin changes

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Admin-managed entities expose audit history with `changedAt`, `changedBy`, `action`, and a safe diff summary.
- Alternatives considered:
  - Option A (chosen): Standard audit contract
  - Option B: No audit UI
- Reasoning and evidence:
  - Pricing changes are dispute-prone; audit visibility is required for support.
- Architectural implications:
  - UI must render audit entries without assuming optional fields.
- Auditor-facing explanation:
  - Inspect change history for pricing rules affecting disputed transactions.
- Migration & backward-compatibility notes:
  - Backfill audit entries where possible; otherwise mark as “history not available”.
- Governance & owner recommendations:
  - Audit schema changes require governance sign-off.

### DECISION-PRICING-016 — Deactivation mechanism (status vs effectiveEndAt)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Deactivation is implemented by setting `effectiveEndAt`; status fields are derived for display and filtering.
- Alternatives considered:
  - Option A (chosen): End-date
  - Option B: `status=INACTIVE`
- Reasoning and evidence:
  - End-dating is time-travel friendly and reduces ambiguity.
- Architectural implications:
  - List endpoints filter by “effective as-of” time.
- Auditor-facing explanation:
  - Inspect that changes create new versions rather than mutating historical rules.
- Migration & backward-compatibility notes:
  - For legacy status-based records, compute end-date equivalents.
- Governance & owner recommendations:
  - Effective dating policy changes require ADR.

### DECISION-PRICING-017 — Product lookup contract for pricing admin screens

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Pricing admin screens use Inventory product search to select `productId`, returning at minimum `productId`, `sku`, and `name`.
- Alternatives considered:
  - Option A (chosen): Use Inventory API
  - Option B: Duplicate product data into Pricing
- Reasoning and evidence:
  - Prevents data duplication and inconsistent search results.
- Architectural implications:
  - UI uses a shared product picker component backed by Inventory.
- Auditor-facing explanation:
  - Inspect that Pricing does not mutate product identity data.
- Migration & backward-compatibility notes:
  - If no API exists, define one in Inventory rather than building ad-hoc pricing endpoints.
- Governance & owner recommendations:
  - Inventory owns product identity; changes require Inventory review.

### DECISION-PRICING-018 — MSRP historical immutability and permissions

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Past-effective MSRP records are immutable unless the actor holds `pricing:msrp:manage:historical`; edits must generate an audit event.
- Alternatives considered:
  - Option A (chosen): Immutable by default + elevated permission
  - Option B: Editable history
- Reasoning and evidence:
  - Prevents rewriting history and reduces pricing disputes.
- Architectural implications:
  - UI renders historical records as read-only unless permission present.
- Auditor-facing explanation:
  - Inspect that historical edits are rare and fully audited.
- Migration & backward-compatibility notes:
  - Introduce the permission and update roles gradually.
- Governance & owner recommendations:
  - Compliance review for any historical edit policy change.
    - Pros: Complete audit trail, pricing reproducibility, regulatory compliance
    - Cons: Storage overhead, schema complexity

- **Option B:** Reference to pricing rules only
  - Pros: Minimal storage
  - Cons: Cannot reproduce pricing if rules change, incomplete audit
- **Option C:** No snapshots (recompute on demand)
  - Pros: No storage overhead
  - Cons: Cannot reproduce historical pricing, audit failure
- **Reasoning and evidence:**
  - Regulatory requirement: pricing decisions must be auditable years later
  - Rules/MSRP/overrides may change after quote; need historical pricing
  - Customer disputes require exact pricing breakdown at transaction time
  - Reproducibility: same inputs should yield same price (even years later)
  - Industry standard: financial systems use immutable snapshots
- **Architectural implications:**
  - **Components affected:**
    - Pricing engine: Creates snapshot during quote
    - Workexec service: Stores snapshot ID on line items
    - Audit UI: Displays snapshot details
  - **Database schema:**

    ```sql
    CREATE TABLE pricing_snapshot (
      id UUID PRIMARY KEY,
      line_item_id UUID NOT NULL, -- estimate or work order line
      product_id UUID NOT NULL,
      quantity DECIMAL(19,4) NOT NULL,
      unit_price DECIMAL(19,4) NOT NULL,
      extended_price DECIMAL(19,4) NOT NULL,
      msrp_at_time DECIMAL(19,4),
      cost_at_time DECIMAL(19,4),
      currency VARCHAR(3) NOT NULL,
      applied_rules JSONB, -- rule IDs and logic
      applied_overrides JSONB, -- override IDs
      applied_promotions JSONB, -- promotion IDs and discounts
      policy_version VARCHAR(50), -- guardrail policy version
      source_context JSONB, -- location, customer tier, quote date
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      -- Immutable: no updated_at or update logic
      CONSTRAINT no_updates CHECK (created_at = created_at) -- prevents updates via triggers
    );
    
    CREATE INDEX idx_snapshot_line ON pricing_snapshot(line_item_id);
    CREATE INDEX idx_snapshot_created ON pricing_snapshot(created_at);
    ```

  - **Snapshot creation:**

    ```java
    @Transactional
    public PricingSnapshot createSnapshot(PriceQuote quote, UUID lineItemId) {
        PricingSnapshot snapshot = new PricingSnapshot();
        snapshot.setLineItemId(lineItemId);
        snapshot.setProductId(quote.getProductId());
        snapshot.setQuantity(quote.getQuantity());
        snapshot.setUnitPrice(quote.getUnitPrice());
        snapshot.setExtendedPrice(quote.getExtendedPrice());
        snapshot.setMsrpAtTime(quote.getMsrpAtTime());
        snapshot.setCostAtTime(quote.getCostAtTime());
        snapshot.setCurrency(quote.getCurrency());
        
        // Serialize applied rules
        snapshot.setAppliedRules(serializeRules(quote.getAppliedRules()));
        snapshot.setAppliedOverrides(serializeOverrides(quote.getAppliedOverrides()));
        snapshot.setAppliedPromotions(serializePromotions(quote.getAppliedPromotions()));
        
        snapshot.setPolicyVersion(guardrailService.getCurrentPolicyVersion());
        snapshot.setSourceContext(serializeContext(quote.getRequest()));
        
        return snapshotRepo.save(snapshot);
        // Note: No update method exists
    }
    ```

  - **Audit drilldown:**

    ```typescript
    interface PricingSnapshotView {
      id: string;
      unitPrice: number;
      extendedPrice: number;
      breakdown: {
        msrp: number;
        cost: number;
        margin: number;
      };
      appliedRules: Array<{
        ruleId: string;
        ruleName: string;
        adjustment: number;
      }>;
      appliedOverrides: Array<{
        overrideId: string;
        description: string;
      }>;
      capturedAt: string;
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify snapshots exist for all lines, snapshots are never modified
  - **Query example:**

    ```sql
    -- Find line items without snapshots
    SELECT li.id, li.estimate_id, li.work_order_id, li.product_id
    FROM line_item li
    LEFT JOIN pricing_snapshot ps ON ps.line_item_id = li.id
    WHERE ps.id IS NULL;
    
    -- Verify immutability (no updates)
    -- (Database constraint prevents updates; this checks for attempts)
    SELECT tablename, schemaname
    FROM pg_tables
    WHERE tablename = 'pricing_snapshot';
    -- Ensure no UPDATE triggers or policies exist
    ```

  - **Expected outcome:** All lines have snapshots, zero update operations
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create pricing_snapshot table
    2. Backfill snapshots for recent transactions (best-effort)
    3. Deploy snapshot creation in pricing engine
    4. Update Workexec to reference snapshot IDs
  - **Backfill strategy:**

    ```sql
    -- Create synthetic snapshots for recent lines (90 days)
    INSERT INTO pricing_snapshot 
      (id, line_item_id, product_id, quantity, unit_price, extended_price, 
       currency, created_at)
    SELECT 
      gen_random_uuid(),
      li.id,
      li.product_id,
      li.quantity,
      li.unit_price,
      li.extended_price,
      'USD',
      li.created_at
    FROM line_item li
    WHERE li.created_at >= NOW() - INTERVAL '90 days'
      AND NOT EXISTS (
        SELECT 1 FROM pricing_snapshot ps WHERE ps.line_item_id = li.id
      );
    -- Note: Historical snapshots lack rule context (not reproducible)
    ```

- **Governance & owner recommendations:**
  - **Owner:** Pricing domain with Compliance oversight
  - **Policy:** Retain snapshots for 7 years (regulatory requirement)
  - **Monitoring:** Alert on snapshot creation failures
  - **Archival:** Move snapshots older than 2 years to cold storage

### DECISION-PRICING-006 — Restriction Rule Evaluation and Override Records

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-006)
- **Decision:** Restriction rules block product sale/installation based on location or service context. Rules are evaluated synchronously and return decision + reason codes. Overrides require manager permission and create immutable override records. Override ID is stored on transaction line for audit.
- **Alternatives considered:**
  - **Option A (Chosen):** Synchronous evaluation with override records
    - Pros: Real-time decisions, clear audit trail, manager control
    - Cons: Requires manager availability
  - **Option B:** Warnings only (no blocking)
    - Pros: Never blocks workflow
    - Cons: Violates business rules, compliance risk
  - **Option C:** Async approval workflow
    - Pros: Non-blocking for user
    - Cons: Delayed feedback, complex state management
- **Reasoning and evidence:**
  - Some products are restricted by regulation (e.g., location emissions standards)
  - Blocking at quote time prevents downstream issues
  - Override audit trail supports compliance reviews
  - Manager approval ensures accountability
  - Industry standard: restrictions with override capability
- **Architectural implications:**
  - **Components affected:**
    - Restriction service: Evaluates rules
    - Override service: Creates override records
    - Pricing API: Returns restriction decisions
  - **Database schema:**

    ```sql
    CREATE TABLE restriction_rule (
      id UUID PRIMARY KEY,
      name VARCHAR(100) NOT NULL,
      product_id UUID, -- null = applies to category/all
      category_id UUID,
      restriction_type VARCHAR(50) NOT NULL, -- LOCATION, SERVICE_TYPE, EMISSIONS
      condition JSONB NOT NULL, -- location IDs, service codes, etc.
      reason_code VARCHAR(50) NOT NULL,
      message TEXT NOT NULL,
      is_active BOOLEAN NOT NULL DEFAULT true,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    
    CREATE TABLE restriction_override (
      id UUID PRIMARY KEY,
      restriction_rule_id UUID NOT NULL REFERENCES restriction_rule(id),
      product_id UUID NOT NULL,
      location_id UUID,
      overridden_by UUID NOT NULL,
      override_reason TEXT NOT NULL,
      approved_by UUID, -- manager who approved
      approved_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      -- Immutable: no updates
    );
    
    CREATE INDEX idx_restriction_product ON restriction_rule(product_id, is_active);
    CREATE INDEX idx_override_audit ON restriction_override(created_at, overridden_by);
    ```

  - **Evaluation logic:**

    ```java
    public RestrictionDecision evaluateRestrictions(
        UUID productId, UUID locationId, String serviceType) {
        
        List<RestrictionRule> rules = ruleRepo.findApplicable(productId);
        
        for (RestrictionRule rule : rules) {
            if (!rule.isActive()) continue;
            
            // Check condition match
            if (matchesCondition(rule, locationId, serviceType)) {
                return new RestrictionDecision(
                    false, // blocked
                    List.of(rule.getId()),
                    List.of(rule.getReasonCode()),
                    rule.getMessage()
                );
            }
        }
        
        return new RestrictionDecision(true, List.of(), List.of(), null);
    }
    
    @Transactional
    @PreAuthorize("hasAuthority('RESTRICTION_OVERRIDE')")
    public RestrictionOverride createOverride(OverrideRequest request) {
        RestrictionOverride override = new RestrictionOverride();
        override.setRestrictionRuleId(request.getRuleId());
        override.setProductId(request.getProductId());
        override.setLocationId(request.getLocationId());
        override.setOverriddenBy(request.getUserId());
        override.setOverrideReason(request.getReason());
        override.setApprovedBy(request.getApprovedBy()); // manager
        override.setApprovedAt(Instant.now());
        
        RestrictionOverride saved = overrideRepo.save(override);
        
        // Audit log
        auditService.log(new AuditEvent(
            "RESTRICTION_OVERRIDE",
            request.getUserId(),
            saved.getId(),
            request.getReason()
        ));
        
        return saved;
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify overrides have manager approval, no orphan overrides
  - **Query example:**

    ```sql
    -- Find overrides without manager approval (should be zero)
    SELECT id, product_id, location_id, overridden_by, created_at
    FROM restriction_override
    WHERE approved_by IS NULL OR approved_at IS NULL;
    
    -- Audit override patterns by user
    SELECT overridden_by, COUNT(*) as override_count,
           COUNT(DISTINCT product_id) as products_affected
    FROM restriction_override
    WHERE created_at >= NOW() - INTERVAL '90 days'
    GROUP BY overridden_by
    HAVING COUNT(*) > 10 -- flag frequent overriders
    ORDER BY override_count DESC;
    ```

  - **Expected outcome:** All overrides approved, monitor frequent overriders
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create restriction_rule and restriction_override tables
    2. Load initial restriction rules from business requirements
    3. Deploy evaluation logic in pricing API
    4. Train managers on override process
  - **Initial rules:**

    ```sql
    -- Example: Restrict certain products in CA due to emissions
    INSERT INTO restriction_rule 
      (id, name, category_id, restriction_type, condition, reason_code, message, is_active)
    VALUES
      (gen_random_uuid(), 
       'California Emissions Restriction',
       'cat-engine-parts-uuid',
       'LOCATION',
       '{"stateCode": "CA"}'::jsonb,
       'EMISSIONS_NON_COMPLIANT',
       'This product does not meet California emissions standards',
       true);
    ```

- **Governance & owner recommendations:**
  - **Owner:** Pricing domain with Legal/Compliance oversight
  - **Policy:** Review restriction rules quarterly for accuracy
  - **Monitoring:** Alert on high override rate (>5% of transactions)
  - **Training:** Annual compliance training on restrictions

### DECISION-PRICING-007 — Quote Request Race Condition Handling

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-007)
- **Decision:** Multiple price quote requests can be in-flight simultaneously. Each request includes a requestId. UI must ignore out-of-order responses by checking requestId matches the latest request. Backend is stateless and idempotent (same inputs = same outputs).
- **Alternatives considered:**
  - **Option A (Chosen):** Client-side requestId matching
    - Pros: Simple backend, handles race conditions, no server state
    - Cons: Requires client discipline
  - **Option B:** Server-side request tracking
    - Pros: Server controls ordering
    - Cons: Adds state, complex cleanup, doesn't solve network latency issues
  - **Option C:** Cancel previous requests
    - Pros: Clean semantics
    - Cons: Wastes computation, complex cancellation logic
- **Reasoning and evidence:**
  - UI allows rapid changes (user types quantity, selects product)
  - Network latency varies (fast request may arrive after slow request)
  - Out-of-order responses cause UI to display wrong price
  - Stateless backend simplifies scaling and testing
  - Request ID is standard pattern for idempotency and matching
- **Architectural implications:**
  - **Components affected:**
    - Pricing API: Returns requestId in response
    - Frontend: Tracks latest requestId and ignores stale responses
  - **Request/response:**

    ```typescript
    // Request
    interface PriceQuoteRequest {
      requestId: string; // UUID generated by client
      productId: string;
      quantity: number;
      locationId: string;
      customerId?: string;
      quoteDate: string;
    }
    
    // Response
    interface PriceQuoteResponse {
      requestId: string; // echoed from request
      productId: string;
      unitPrice: number;
      extendedPrice: number;
      breakdown: PriceBreakdown;
      warnings: string[];
    }
    ```

  - **Client-side handling:**

    ```typescript
    class PricingService {
      private latestRequestId: string | null = null;
      
      async getQuote(request: PriceQuoteRequest): Promise<PriceQuoteResponse> {
        const requestId = crypto.randomUUID();
        this.latestRequestId = requestId;
        
        const response = await api.post('/pricing/quote', {
          ...request,
          requestId
        });
        
        // Ignore if newer request was made
        if (response.requestId !== this.latestRequestId) {
          console.log('Ignoring stale response', response.requestId);
          throw new StaleResponseError();
        }
        
        return response;
      }
    }
    ```

  - **Backend (stateless):**

    ```java
    @PostMapping("/pricing/quote")
    public PriceQuoteResponse getQuote(@RequestBody PriceQuoteRequest request) {
        // Validate inputs
        validateRequest(request);
        
        // Calculate price (pure function, no side effects)
        PriceQuote quote = pricingEngine.calculate(request);
        
        // Build response (echo requestId)
        return PriceQuoteResponse.builder()
            .requestId(request.getRequestId())
            .productId(quote.getProductId())
            .unitPrice(quote.getUnitPrice())
            .extendedPrice(quote.getExtendedPrice())
            .breakdown(quote.getBreakdown())
            .warnings(quote.getWarnings())
            .build();
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify quote API is stateless, responses include requestId
  - **Monitoring:**

    ```sql
    -- Track quote response times (detect slow queries)
    SELECT 
      AVG(response_time_ms) as avg_time,
      PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY response_time_ms) as p95,
      PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY response_time_ms) as p99
    FROM pricing_api_log
    WHERE endpoint = '/pricing/quote'
      AND created_at >= NOW() - INTERVAL '1 hour';
    ```

  - **Expected outcome:** P95 < 500ms, P99 < 1000ms
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add requestId to quote API request/response schema
    2. Update frontend to generate and track requestIds
    3. Deploy with backward compatibility (optional requestId initially)
    4. Make requestId required after transition period
  - **Testing:** Simulate race conditions with concurrent requests
- **Governance & owner recommendations:**
  - **Owner:** Pricing domain with Frontend team
  - **Policy:** All quote requests must include requestId
  - **Monitoring:** Alert on high quote latency (P95 > 1s)
  - **Documentation:** Document requestId pattern in API guide

### DECISION-PRICING-008 — Price Source Attribution in Breakdown

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-008)
- **Decision:** Price quote responses include breakdown trace showing which rules/overrides/promotions were applied. Source attribution enables troubleshooting and customer explanations. Breakdown includes rule IDs, names, adjustments, and precedence order.
- **Alternatives considered:**
  - **Option A (Chosen):** Full breakdown with source attribution
    - Pros: Transparent, debuggable, customer-friendly, auditable
    - Cons: Larger response payloads
  - **Option B:** Final price only (no breakdown)
    - Pros: Minimal payload
    - Cons: Opaque, cannot troubleshoot, poor customer experience
  - **Option C:** Breakdown on demand (separate API)
    - Pros: Optimized default response
    - Cons: Requires second API call, stale data risk
- **Reasoning and evidence:**
  - "Why is my price different?" is common customer question
  - Support teams need breakdown to explain pricing
  - Price troubleshooting requires knowing which rules applied
  - Regulatory transparency requirements (itemized pricing)
  - Industry standard: show work in pricing calculations
- **Architectural implications:**
  - **Components affected:**
    - Pricing engine: Builds breakdown during calculation
    - Quote response: Includes breakdown object
    - UI: Displays breakdown in tooltip or expandable section
  - **Response schema:**

    ```typescript
    interface PriceQuoteResponse {
      requestId: string;
      productId: string;
      unitPrice: number;
      extendedPrice: number;
      breakdown: {
        msrp: number;
        basePrice: number; // after price book rules
        adjustments: Array<{
          source: 'PRICE_BOOK_RULE' | 'LOCATION_OVERRIDE' | 'PROMOTION';
          sourceId: string;
          sourceName: string;
          adjustmentType: 'PERCENTAGE' | 'FIXED_AMOUNT';
          adjustmentValue: number;
          resultingPrice: number;
          appliedAt: number; // precedence order
        }>;
        finalPrice: number;
      };
      warnings: Array<{
        code: string;
        message: string;
      }>;
    }
    ```

  - **Breakdown builder:**

    ```java
    public PriceBreakdown buildBreakdown(PriceCalculation calc) {
        PriceBreakdown breakdown = new PriceBreakdown();
        breakdown.setMsrp(calc.getMsrp());
        
        List<Adjustment> adjustments = new ArrayList<>();
        int order = 1;
        
        // Base price from price book
        if (calc.getAppliedRule() != null) {
            adjustments.add(new Adjustment(
                "PRICE_BOOK_RULE",
                calc.getAppliedRule().getId(),
                calc.getAppliedRule().getName(),
                calc.getAppliedRule().getAdjustmentType(),
                calc.getAppliedRule().getAdjustmentValue(),
                calc.getBasePrice(),
                order++
            ));
        }
        
        // Location override
        if (calc.getAppliedOverride() != null) {
            adjustments.add(new Adjustment(
                "LOCATION_OVERRIDE",
                calc.getAppliedOverride().getId(),
                "Store Price Override",
                "FIXED_AMOUNT",
                calc.getAppliedOverride().getOverridePrice(),
                calc.getPriceAfterOverride(),
                order++
            ));
        }
        
        // Promotions
        for (AppliedPromotion promo : calc.getAppliedPromotions()) {
            adjustments.add(new Adjustment(
                "PROMOTION",
                promo.getId(),
                promo.getCode(),
                promo.getDiscountType(),
                promo.getDiscountValue(),
                promo.getPriceAfterDiscount(),
                order++
            ));
        }
        
        breakdown.setAdjustments(adjustments);
        breakdown.setFinalPrice(calc.getFinalPrice());
        return breakdown;
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify breakdowns accurately reflect applied rules
  - **Sample validation:**

    ```sql
    -- Verify promotion discounts match promotion definitions
    SELECT ps.id, ps.applied_promotions, po.discount_value
    FROM pricing_snapshot ps
    CROSS JOIN LATERAL jsonb_array_elements(ps.applied_promotions) AS promo
    JOIN promotion_offer po ON po.id = (promo->>'promotionId')::uuid
    WHERE (promo->>'discountValue')::decimal != po.discount_value;
    ```

  - **Expected outcome:** Zero mismatches between snapshot and promotion
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add breakdown object to quote response schema
    2. Update pricing engine to build breakdowns
    3. Deploy with backward compatibility (optional breakdown)
    4. Update UI to display breakdowns
  - **Backward compatibility:** Old clients ignore breakdown field
- **Governance & owner recommendations:**
  - **Owner:** Pricing domain with UX team
  - **Policy:** Breakdowns must be accurate and customer-friendly
  - **Review cadence:** Quarterly review of breakdown clarity
  - **Documentation:** Document breakdown interpretation in help docs

### DECISION-PRICING-009 — Warnings vs Errors in Quote Response

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-009)
- **Decision:** Price quote API distinguishes warnings from errors. Errors block quote (HTTP 400/404). Warnings return HTTP 200 with price and warning array (e.g., "MSRP unavailable, using fallback"). UI displays warnings but allows proceeding.
- **Alternatives considered:**
  - **Option A (Chosen):** Warnings with HTTP 200, errors with HTTP 4xx
    - Pros: Clear semantics, allows proceeding with warnings, explicit error handling
    - Cons: Requires client to check warnings array
  - **Option B:** All issues return errors (HTTP 4xx)
    - Pros: Simplest error handling
    - Cons: Cannot proceed with degraded data, poor UX
  - **Option C:** All issues return warnings (HTTP 200)
    - Pros: Never blocks workflow
    - Cons: Cannot distinguish critical errors, data quality issues
- **Reasoning and evidence:**
  - Some pricing issues are informational (e.g., cost unavailable)
  - Blocking workflow for warnings hurts productivity
  - Critical errors (product not found) must block to prevent bad data
  - Clear distinction helps users understand severity
  - Industry pattern: warnings vs errors based on blockingness
- **Architectural implications:**
  - **Components affected:**
    - Pricing API: Returns warnings array
    - Frontend: Displays warnings banner
  - **Response schema:**

    ```typescript
    interface PriceQuoteResponse {
      requestId: string;
      productId: string;
      unitPrice: number;
      extendedPrice: number;
      breakdown: PriceBreakdown;
      warnings: Array<{
        code: string; // e.g., 'MSRP_UNAVAILABLE'
        severity: 'INFO' | 'WARNING';
        message: string;
        field?: string;
      }>;
    }
    
    // Error response (HTTP 400)
    interface ErrorResponse {
      errorCode: string;
      message: string;
      field?: string;
    }
    ```

  - **Warning generation:**

    ```java
    public PriceQuote calculate(PriceRequest request) {
        List<Warning> warnings = new ArrayList<>();
        
        // Get MSRP
        BigDecimal msrp = null;
        try {
            msrp = msrpService.getMSRP(request.getProductId(), request.getQuoteDate());
        } catch (NotFoundException e) {
            warnings.add(new Warning(
                "MSRP_UNAVAILABLE",
                "WARNING",
                "MSRP not found, using base price only"
            ));
        }
        
        // Get cost
        BigDecimal cost = null;
        try {
            cost = costService.getCost(request.getProductId());
        } catch (Exception e) {
            warnings.add(new Warning(
                "COST_UNAVAILABLE",
                "INFO",
                "Cost data unavailable, margin cannot be calculated"
            ));
        }
        
        // Calculate price (continue despite warnings)
        PriceQuote quote = calculatePrice(request, msrp, cost);
        quote.setWarnings(warnings);
        
        return quote;
    }
    ```

  - **Frontend handling:**

    ```typescript
    async function loadPrice(productId: string, quantity: number) {
      const response = await pricingService.getQuote({
        requestId: crypto.randomUUID(),
        productId,
        quantity,
        locationId: selectedLocation
      });
      
      // Display price
      displayPrice(response.unitPrice);
      
      // Show warnings if present
      if (response.warnings.length > 0) {
        showWarningsBanner(response.warnings.map(w => w.message));
      }
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify warnings are appropriate, not masking errors
  - **Monitoring:**

    ```sql
    -- Track warning rates
    SELECT 
      warning_code,
      COUNT(*) as occurrence_count,
      ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER(), 2) as percentage
    FROM (
      SELECT jsonb_array_elements(warnings)->>'code' as warning_code
      FROM pricing_api_log
      WHERE endpoint = '/pricing/quote'
        AND warnings IS NOT NULL
        AND created_at >= NOW() - INTERVAL '7 days'
    ) warnings
    GROUP BY warning_code
    ORDER BY occurrence_count DESC;
    ```

  - **Expected outcome:** Warning rates < 5%, no unexpected warning codes
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add warnings array to response schema
    2. Update pricing engine to collect warnings
    3. Deploy with backward compatibility (empty warnings array)
    4. Update UI to display warnings
  - **Testing:** Test all warning scenarios (missing MSRP, cost, etc.)
- **Governance & owner recommendations:**
  - **Owner:** Pricing domain
  - **Policy:** Document all warning codes and meanings
  - **Review cadence:** Monthly review of warning rates
  - **Monitoring:** Alert on warning rate spike (>10%)

### DECISION-PRICING-010 — Historical Pricing Reproducibility

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-010)
- **Decision:** Pricing calculations must be reproducible given historical context (date, product, location, customer, rules at that time). Immutable snapshots provide reproducibility for closed transactions. Active quotes use current rules.
- **Alternatives considered:**
  - **Option A (Chosen):** Snapshots for transactions, current rules for quotes
    - Pros: Reproducible history, always-current quotes, clear boundary
    - Cons: Must maintain snapshot storage
  - **Option B:** Always recalculate (no snapshots)
    - Pros: No storage overhead
    - Cons: Cannot reproduce historical pricing, audit failure
  - **Option C:** Version all rules and time-travel queries
    - Pros: Perfect reproducibility
    - Cons: Extremely complex, performance issues
- **Reasoning and evidence:**
  - Audit requirement: reproduce pricing for closed transactions
  - Customer disputes: "You quoted $X but charged $Y"
  - Regulatory compliance: demonstrate pricing accuracy at transaction time
  - Snapshots balance reproducibility with system complexity
  - Active quotes must use current rules (show today's price)
- **Architectural implications:**
  - **Components affected:**
    - Snapshot service: Creates snapshots at transaction time
    - Pricing engine: Uses current rules for active quotes
    - Audit service: Retrieves and displays snapshots
  - **Reproducibility guarantee:**

    ```java
    // For active quote: use current rules
    public PriceQuote getCurrentQuote(PriceRequest request) {
        return pricingEngine.calculate(request); // uses current rules/MSRP/overrides
    }
    
    // For historical transaction: use snapshot
    public PriceQuote getHistoricalPrice(UUID snapshotId) {
        PricingSnapshot snapshot = snapshotRepo.findById(snapshotId)
            .orElseThrow(() -> new NotFoundException("Snapshot not found"));
        
        // Reconstruct quote from snapshot (no recalculation)
        return PriceQuote.fromSnapshot(snapshot);
    }
    
    // Verify reproducibility (for audit)
    public boolean verifySnapshot(UUID snapshotId) {
        PricingSnapshot snapshot = snapshotRepo.findById(snapshotId)
            .orElseThrow(() -> new NotFoundException("Snapshot not found"));
        
        // Reconstruct request context
        PriceRequest historicalRequest = PriceRequest.builder()
            .productId(snapshot.getProductId())
            .quantity(snapshot.getQuantity())
            .locationId(extractFromContext(snapshot, "locationId"))
            .customerId(extractFromContext(snapshot, "customerId"))
            .quoteDate(snapshot.getCreatedAt().toLocalDate())
            .build();
        
        // Note: Cannot perfectly reproduce if rules changed
        // Snapshot is source of truth, not recalculation
        return true; // snapshot is authoritative
    }
    ```

  - **Snapshot completeness:**

    ```sql
    -- Verify snapshots have complete context for reproducibility
    SELECT id, product_id, created_at
    FROM pricing_snapshot
    WHERE applied_rules IS NULL -- missing rule context
       OR source_context IS NULL -- missing request context
    LIMIT 100;
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify snapshots exist for all financial transactions, snapshots contain complete context
  - **Query example:**

    ```sql
    -- Find invoiced lines without pricing snapshots
    SELECT il.id, il.invoice_id, il.product_id, il.unit_price
    FROM invoice_line il
    LEFT JOIN pricing_snapshot ps ON ps.line_item_id = il.id
    WHERE il.invoice_status = 'PAID'
      AND ps.id IS NULL;
    ```

  - **Expected outcome:** Zero paid invoice lines without snapshots
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Deploy snapshot creation for all new transactions
    2. Backfill recent transactions (90 days)
    3. Accept that older transactions may lack complete snapshots
    4. Document snapshot coverage date
  - **Coverage documentation:**

    ```sql
    -- Document snapshot coverage
    SELECT 
      MIN(created_at) as earliest_snapshot,
      MAX(created_at) as latest_snapshot,
      COUNT(*) as total_snapshots
    FROM pricing_snapshot;
    ```

- **Governance & owner recommendations:**
  - **Owner:** Pricing domain with Compliance
  - **Policy:** All financial transactions must have pricing snapshots
  - **Retention:** 7-year retention for snapshots
  - **Review cadence:** Annual audit of snapshot coverage

---

## Enhancements from consolidated stories (institutional memory)

This section captures additional rationale and cross-cutting patterns that emerged from the consolidated frontend stories (#236, #161, #84, and references to #115). These are **non-normative** notes intended to explain *why* we are leaning toward certain patterns and what risks we are actively managing.

### Why “backend-authoritative totals” keeps showing up

Across estimate totals (#236), quote pricing (#115), and order line overrides (#84), the same failure mode appears: if the UI computes *anything* beyond formatting, we get drift between what the customer saw and what the system later charges.

Key drivers:

- **Auditability:** disputes require “show your work” and immutable evidence (snapshots).
- **Rounding and tax edge cases:** tax jurisdiction rules, inclusive/exclusive modes, and rounding adjustments are notoriously non-intuitive and vary by policy.
- **Concurrency:** multiple edits in quick succession can cause out-of-order responses; the UI must treat the backend as the source of truth and ignore stale results.

This reinforces existing decisions:

- DECISION-PRICING-001 (money + rounding backend-owned)
- DECISION-PRICING-013/014 (snapshot drilldown + redaction)
- DECISION-PRICING-006/008/009 (determinism + explainability + warnings)

### Cross-domain boundary pressure points

The stories highlight repeated boundary questions:

- **Workexec owns estimate lifecycle and screens**, but **Pricing/Tax owns calculation** (#236).
- **Order/Checkout owns order lines**, but **Pricing owns override policy and reason catalogs** (#84).
- **Pricing owns promotions**, but scope and activation semantics may intersect with store/location governance (#161).

Institutional memory: these boundaries tend to blur in Moqui implementations because screens can call services across components. We should keep the *contract* boundaries crisp even if the runtime wiring is flexible.

---

## Decision Log (additions based on new stories)

> These entries capture newly identified patterns and decisions implied by the stories. They are intended to be linked into `AGENT_GUIDE.md` once confirmed, but are recorded here first as institutional memory.

### DECISION-PRICING-019 — Estimate totals/tax calculation is backend-authoritative and snapshot-backed

- **Motivation (from #236):** Estimates must show subtotal/tax/fees/discounts/grand total that are correct, up-to-date, and reproducible for disputes; approval must be blocked when required tax configuration is missing.
- **Decision (proposed / emerging):**
  - Totals and taxes for an estimate are calculated by backend Pricing/Tax services and returned as a single authoritative response.
  - Each successful calculation returns a `calculationSnapshotId` (or equivalent) that the UI can open read-only.
  - UI must not compute taxes/totals; it only formats and displays backend-provided values and warnings/errors.
- **Tradeoffs:**
  - Pros: auditability, consistency, fewer client bugs, centralized policy.
  - Cons: more backend calls; requires careful UX for loading states and partial failures.
- **Rejected alternatives:**
  - Client-side tax/totals computation:
    - Rejected due to policy drift, rounding differences, and inability to guarantee audit reproducibility.
  - “Compute totals only at approval time”:
    - Rejected because advisors need accurate totals during editing; also increases surprise failures at approval.
- **Risk assessment:**
  - Risk: increased latency and perceived slowness during rapid edits.
  - Mitigation: serialize recalculation calls; show “recalculating” state; ignore stale responses; optionally debounce recalculation triggers.
  - Risk: missing tax configuration blocks workflow unexpectedly.
  - Mitigation: deterministic error codes + actionable messaging; allow editing to continue; provide explicit “Recalculate totals” action after config is fixed.
- **Auditor-facing notes:**
  - Inspect that approvals cannot proceed when backend indicates missing tax configuration.
  - Inspect that snapshots exist for successful calculations and are immutable/read-only.

### DECISION-PRICING-020 — Workflow gating uses deterministic backend error codes (not UI heuristics)

- **Motivation (from #236, #84, #161):** UI must block “Proceed to Approval” or “Submit override” based on authoritative backend outcomes, not inferred conditions.
- **Decision (proposed / emerging):**
  - Backend returns stable `code` values for blocking configuration/policy failures (e.g., missing jurisdiction, missing tax code, override not allowed).
  - UI gates actions based on:
    1) explicit backend capability flags when available, and
    2) backend error codes on attempted action (403/409/4xx) as the final authority.
- **Tradeoffs:**
  - Pros: consistent enforcement; fewer false positives/negatives in UI.
  - Cons: requires backend to maintain a stable error taxonomy and documentation.
- **Rejected alternatives:**
  - UI infers readiness from presence/absence of fields (e.g., “if taxTotal exists then ready”):
    - Rejected because it fails under partial responses, warnings, and policy changes.
- **Risk assessment:**
  - Risk: error code drift across services/domains.
  - Mitigation: publish a shared error code registry for pricing/tax/override flows; add contract tests.
- **Auditor-facing notes:**
  - Inspect that blocking conditions are enforced server-side even if UI is bypassed.

### DECISION-PRICING-021 — UI must serialize recalculation requests and ignore stale responses for estimate totals

- **Motivation (from #236; aligns with quote request race handling):** Users can edit multiple estimate lines quickly; recalculation responses can arrive out of order.
- **Decision (proposed / emerging):**
  - UI serializes estimate totals recalculation calls per estimate (one in flight).
  - If multiple recalculation triggers occur, UI either:
    - queues the latest request (drop intermediate), or
    - uses a requestId/sequence to ignore stale responses.
- **Tradeoffs:**
  - Pros: prevents totals flicker and incorrect display.
  - Cons: may delay final totals update if recalculation is slow.
- **Rejected alternatives:**
  - Allow concurrent recalculation calls and “last response wins”:
    - Rejected due to out-of-order response risk.
- **Risk assessment:**
  - Risk: backend may not echo requestId for estimate totals calculation.
  - Mitigation: UI-side serialization is sufficient even without requestId; if backend supports requestId, adopt it for symmetry with quote APIs.
- **Auditor-facing notes:**
  - Inspect logs/telemetry for repeated recalculation calls and ensure no inconsistent totals are persisted.

### DECISION-PRICING-022 — Promotion admin must align with effective-dating semantics and code immutability

- **Motivation (from #161):** Promotion creation/activation/deactivation must be deterministic, auditable, and consistent with effective dating and code uniqueness.
- **Decision (proposed / emerging):**
  - Promotion codes remain immutable and case-insensitive unique (already DECISION-PRICING-007).
  - Promotion validity windows follow half-open `[start, end)` semantics and store-local interpretation (DECISION-PRICING-003).
  - Deactivation should prefer end-dating (`effectiveEndAt`) consistent with DECISION-PRICING-016, unless backend contract requires explicit status transitions.
- **Tradeoffs:**
  - Pros: consistent time-travel semantics; fewer “is it active?” ambiguities.
  - Cons: requires clarity on whether promotions are timestamp-effective or date-only.
- **Rejected alternatives:**
  - Free-form code edits after creation:
    - Rejected due to customer confusion and audit complexity.
  - UI-only “active” toggles without backend enforcement:
    - Rejected due to policy drift and concurrency issues.
- **Risk assessment:**
  - Risk: mismatch between promotion model (status vs effectiveEndAt) and UI assumptions.
  - Mitigation: UI must treat backend as authoritative and render actions based on returned state/capabilities; do not hardcode state machine.
- **Auditor-facing notes:**
  - Inspect audit history for activation/deactivation events (DECISION-PRICING-015).

### DECISION-PRICING-023 — Override reason catalogs are domain-owned and must not be conflated

- **Motivation (from #84):** Restriction overrides already have a reason catalog (DECISION-PRICING-011), but price overrides also need reasons; conflating catalogs risks governance mistakes.
- **Decision (proposed / emerging):**
  - Reason catalogs are fetched from backend and never hardcoded (already DECISION-PRICING-011).
  - **Price override reasons** and **restriction override reasons** should be separate catalogs unless governance explicitly defines a shared taxonomy.
- **Tradeoffs:**
  - Pros: clearer governance, avoids using the wrong reasons for the wrong control.
  - Cons: more endpoints/catalogs to manage.
- **Rejected alternatives:**
  - Reuse restriction override reasons for price overrides by default:
    - Rejected unless governance confirms the semantics are identical.
- **Risk assessment:**
  - Risk: UI implements against the wrong catalog endpoint.
  - Mitigation: require explicit backend contract naming and documentation; add integration tests that validate catalog “type”.
- **Auditor-facing notes:**
  - Inspect that override records reference valid reason codes from the correct catalog.

---

## Known Risks & Mitigations (cross-cutting)

### 1) Tax configuration gaps can block approvals unexpectedly

- **Observed in:** #236
- **Risk:** stores without configured jurisdictions/tax codes cause blocking errors at the point of approval or recalculation.
- **Mitigations:**
  - Deterministic error codes (`ERR_CONFIG_JURISDICTION_MISSING`, `ERR_MISSING_TAX_CODE`) and correlation IDs.
  - UI allows continued editing; blocks only the approval transition.
  - Operational: add monitoring for frequency of these errors by location; treat spikes as configuration incidents.

### 2) Concurrency and out-of-order responses

- **Observed in:** #236 and existing quote race decision
- **Risk:** stale totals/prices displayed, leading to customer disputes.
- **Mitigations:**
  - Serialize recalculation calls; ignore stale responses.
  - Prefer requestId echoing where feasible (symmetry with quote APIs).

### 3) Multi-currency ambiguity

- **Observed in:** #236 and #161
- **Risk:** UI formats money incorrectly or submits fixed-amount promotions without currency context.
- **Mitigations:**
  - Require `currencyUomId` in money DTOs (DECISION-PRICING-001).
  - Ensure estimate payload includes currency or provides an authoritative source.

### 4) Cross-domain idempotency expectations

- **Observed in:** #236 and #84 referencing `Idempotency-Key` (DECISION-INVENTORY-012)
- **Risk:** duplicate line item saves, duplicate override requests, or duplicate approval transitions.
- **Mitigations:**
  - Adopt idempotency keys for user-triggered mutations consistently.
  - Ensure backend defines idempotency scope keys (e.g., `(estimateId, idempotencyKey)` or `(orderId, lineItemId, idempotencyKey)`).

---

## Open Questions (updated / consolidated)

> These are intentionally duplicated/merged from the stories to preserve institutional memory and highlight what is blocking implementation.

### Estimate totals & tax calculation (#236)

1. **Domain ownership/label correction (blocking):** Confirm whether estimate totals/tax recalculation UI wiring belongs under Pricing or Workexec ownership, and whether to split into two stories (workexec screen wiring vs pricing calculation contract).
2. **Exact Moqui artifacts (blocking):** Confirm actual screen names/paths for estimate editing and where totals panel should live (expected: `durion-workexec/EstimateEdit.xml` per DECISION-INVENTORY-002).
3. **Backend contract (blocking):** Provide exact service names/REST endpoints and DTO schemas for:
   - calculate totals for estimate
   - load calculation snapshot details
   - proceed-to-approval transition  
   Include definitive error `code` list and `fieldErrors` shape.
4. **Missing tax code policy (blocking):** Is the policy to fail calculation (`ERR_MISSING_TAX_CODE`) or apply a default tax code? If defaulting is allowed, how is it represented in snapshot/audit and UI (warning vs silent)?
5. **Tax-inclusive vs tax-exclusive (blocking):** Which modes must frontend support now, and how does backend indicate the mode for an estimate/location?
6. **Currency source (blocking if multi-currency):** Does estimate payload include `currencyUomId/currencyCode`? If not, what is the authoritative source for currency formatting?

### Promotions admin (#161)

1. **Permissions (blocking):** Confirm exact permission strings for:
   - view promotions list/detail
   - manage promotions create/activate/deactivate  
   Also confirm whether UI should hide actions vs show disabled with “no permission”.
2. **Scope model (blocking):** Backend contract for promotion scope:
   - `storeCode`, `locationId`, both, or structured `scope` object?
   - representation for “all stores/locations” (null vs explicit flag)?
3. **Effective dating fields (blocking):** Are promotions timestamp-effective (`effectiveStartAt/effectiveEndAt`) with store-local interpretation (preferred), or date-only (`startDate/endDate`)? Provide exact field names and formats.
4. **Fixed amount currency (blocking):** For `FIXED_INVOICE`, does backend require Money `{ amount, currencyUomId }` or separate fields? Which currency applies (store vs tenant default)?
5. **Activation/deactivation mechanism (blocking):** For promotions, is deactivation:
    - status transition,
    - end-dating (`effectiveEndAt`),
    - or both? Provide authoritative behavior and response payload.
6. **Code normalization (blocking):** Should UI uppercase codes on input/submit, or rely on backend case-insensitive uniqueness only?

### Order line price override (#84)

1. **System-of-record + endpoint ownership (blocking):** Is the “line price override” API owned by Pricing (`/pricing/v1/...`) or by Order/Checkout (non-pricing base path)? Provide exact REST paths or Moqui service names and payloads for:
    - create override request
    - (optional) apply approved override
    - load per-line `canOverridePrice` capability flag and override summary
2. **Permission strings (blocking):** Exact permission identifiers for:
    - requesting a price override (advisor)
    - applying an override (if separate)
    - manager approval validation (if token-based)
3. **Reason code catalog for price overrides (blocking):** What endpoint provides authoritative reason codes for **price overrides**? Confirm whether it reuses restriction override reasons or is separate.
4. **Manager approval token acquisition (blocking):** How does POS obtain `managerApprovalId` (re-auth modal, scanned token, selection from pending approvals)? Provide UX entry point and data shape.
5. **Multiple overrides per line (blocking):** If a line already has an override in `PENDING_APPROVAL` or `APPLIED`, can a new override request be created? If yes, does it supersede prior override or keep multiple with an “active” pointer? What capability flag or error code indicates “cannot create new override”?

---

## End

End of document.
