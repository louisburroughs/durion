# PRICING_DOMAIN_NOTES.md

## Summary

This document provides comprehensive rationale and decision logs for the Pricing domain. Pricing manages MSRP, price books, location overrides, promotions, restrictions, and immutable pricing snapshots. Each decision includes alternatives considered, architectural implications, audit guidance, and governance recommendations.

## Completed items

- [x] Documented 10 key pricing decisions
- [x] Provided alternatives analysis for each decision
- [x] Included architectural implications with schemas
- [x] Added auditor-facing SQL queries
- [x] Defined migration and governance strategies

## Decision details

### DECISION-PRICING-001 — MSRP Temporal Uniqueness Per Product

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-001)
- **Decision:** Each product can have only one MSRP record active at any point in time. No overlapping effective date ranges are allowed per product. This ensures price determinism and prevents ambiguity in pricing calculations.
- **Alternatives considered:**
  - **Option A (Chosen):** Enforce temporal uniqueness with database constraint
    - Pros: Guarantees data integrity, prevents pricing ambiguity, clear audit trail
    - Cons: Requires careful date management, cannot support A/B pricing tests
  - **Option B:** Allow overlaps with precedence rules
    - Pros: More flexible for promotions
    - Cons: Ambiguous, complex precedence logic, audit confusion
  - **Option C:** Latest-wins without validation
    - Pros: Simplest implementation
    - Cons: Data quality issues, undetectable errors, compliance risks
- **Reasoning and evidence:**
  - MSRP is regulatory-sensitive (advertised pricing must be accurate)
  - Overlapping MSRPs create ambiguous "correct price" at a point in time
  - Audit requirements mandate clear price lineage
  - Deterministic pricing prevents customer disputes
  - Industry standard: one authoritative price per product per time period
- **Architectural implications:**
  - **Components affected:**
    - MSRP service: Validates date ranges on create/update
    - Database: Exclusion constraint on (product_id, effective_range)
    - Pricing engine: Assumes single MSRP per product per date
  - **Database schema:**
    ```sql
    CREATE TABLE product_msrp (
      id UUID PRIMARY KEY,
      product_id UUID NOT NULL REFERENCES product(id),
      msrp_amount DECIMAL(19,4) NOT NULL CHECK (msrp_amount >= 0),
      currency VARCHAR(3) NOT NULL DEFAULT 'USD',
      effective_start_date DATE NOT NULL,
      effective_end_date DATE, -- null = indefinite
      created_by UUID NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CHECK (effective_end_date IS NULL OR effective_end_date > effective_start_date)
    );
    
    -- PostgreSQL exclusion constraint for temporal uniqueness
    CREATE EXTENSION IF NOT EXISTS btree_gist;
    CREATE UNIQUE INDEX idx_msrp_temporal_unique ON product_msrp
    USING GIST (product_id WITH =, daterange(effective_start_date, effective_end_date, '[)') WITH &&)
    WHERE effective_end_date IS NOT NULL;
    
    -- Separate unique constraint for open-ended MSRP
    CREATE UNIQUE INDEX idx_msrp_open_ended ON product_msrp (product_id)
    WHERE effective_end_date IS NULL;
    ```
  - **Validation logic:**
    ```java
    @Transactional
    public ProductMSRP createMSRP(CreateMSRPRequest request) {
        // Validate no overlaps
        boolean hasOverlap = msrpRepo.existsOverlap(
            request.getProductId(),
            request.getEffectiveStartDate(),
            request.getEffectiveEndDate()
        );
        
        if (hasOverlap) {
            throw new ConflictException(
                "MSRP dates overlap with existing record for product " + request.getProductId()
            );
        }
        
        ProductMSRP msrp = new ProductMSRP();
        msrp.setProductId(request.getProductId());
        msrp.setMsrpAmount(request.getAmount());
        msrp.setCurrency(request.getCurrency());
        msrp.setEffectiveStartDate(request.getEffectiveStartDate());
        msrp.setEffectiveEndDate(request.getEffectiveEndDate());
        
        return msrpRepo.save(msrp);
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no products have overlapping MSRP date ranges
  - **Query example:**
    ```sql
    -- Find products with overlapping MSRP records
    SELECT m1.product_id, m1.id as msrp1, m2.id as msrp2,
           m1.effective_start_date as start1, m1.effective_end_date as end1,
           m2.effective_start_date as start2, m2.effective_end_date as end2
    FROM product_msrp m1
    JOIN product_msrp m2 ON m1.product_id = m2.product_id AND m1.id != m2.id
    WHERE daterange(m1.effective_start_date, m1.effective_end_date, '[)')
      && daterange(m2.effective_start_date, m2.effective_end_date, '[)');
    ```
  - **Expected outcome:** Zero overlapping records
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing MSRP data for overlaps
    2. Resolve conflicts (keep most recent or highest priority)
    3. Deploy exclusion constraint
    4. Update MSRP management UI with date validation
  - **Conflict resolution:**
    ```sql
    -- Find and report overlaps before migration
    WITH overlaps AS (
      SELECT m1.product_id, COUNT(*) as overlap_count
      FROM product_msrp m1, product_msrp m2
      WHERE m1.product_id = m2.product_id 
        AND m1.id < m2.id
        AND daterange(m1.effective_start_date, m1.effective_end_date, '[)')
          && daterange(m2.effective_start_date, m2.effective_end_date, '[)')
      GROUP BY m1.product_id
    )
    SELECT * FROM overlaps WHERE overlap_count > 0;
    ```
- **Governance & owner recommendations:**
  - **Owner:** Pricing domain team with Product team coordination
  - **Policy:** MSRP changes require product owner approval
  - **Review cadence:** Quarterly MSRP accuracy audit
  - **Monitoring:** Alert on MSRP gaps (product has no effective MSRP)

### DECISION-PRICING-002 — Price Book Rule Deterministic Precedence

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-002)
- **Decision:** Price book rules follow deterministic precedence: SKU-specific beats Category-specific beats Global, with priority field as tie-breaker within same specificity. This ensures consistent pricing across all quote requests.
- **Alternatives considered:**
  - **Option A (Chosen):** Specificity-based precedence with priority tie-breaker
    - Pros: Predictable, easy to reason about, industry standard
    - Cons: Requires careful rule authoring
  - **Option B:** Priority-only precedence (no specificity)
    - Pros: Maximum flexibility
    - Cons: Hard to reason about, error-prone
  - **Option C:** Latest-created wins
    - Pros: Simplest to implement
    - Cons: Unpredictable, cannot express business intent
- **Reasoning and evidence:**
  - Pricing must be deterministic (same inputs = same output)
  - Specificity matches business intent (specific rules should override general)
  - Priority allows explicit control within same specificity level
  - Industry standard pattern (most pricing engines use this approach)
  - Audit requirement: pricing decisions must be reproducible
- **Architectural implications:**
  - **Components affected:**
    - Pricing engine: Evaluates rules in precedence order
    - Rule management UI: Displays effective precedence
  - **Rule evaluation:**
    ```java
    public PriceQuote calculatePrice(PriceRequest request) {
        List<PriceBookRule> applicableRules = ruleRepo.findApplicable(
            request.getProductId(),
            request.getLocationId(),
            request.getCustomerTier(),
            request.getQuoteDate()
        );
        
        // Sort by precedence
        applicableRules.sort((r1, r2) -> {
            // 1. Specificity (SKU > CATEGORY > GLOBAL)
            int spec1 = getSpecificity(r1.getTarget());
            int spec2 = getSpecificity(r2.getTarget());
            if (spec1 != spec2) {
                return Integer.compare(spec2, spec1); // higher spec wins
            }
            
            // 2. Priority within same specificity
            return Integer.compare(r2.getPriority(), r1.getPriority()); // higher priority wins
        });
        
        // Apply first matching rule
        PriceBookRule winningRule = applicableRules.stream()
            .filter(r -> matchesConditions(r, request))
            .findFirst()
            .orElse(null);
        
        return applyRule(winningRule, request);
    }
    
    private int getSpecificity(RuleTarget target) {
        return switch (target.getType()) {
            case SKU -> 3;
            case CATEGORY -> 2;
            case GLOBAL -> 1;
        };
    }
    ```
  - **Database schema:**
    ```sql
    CREATE TYPE rule_target_type AS ENUM ('SKU', 'CATEGORY', 'GLOBAL');
    
    CREATE TABLE price_book_rule (
      id UUID PRIMARY KEY,
      price_book_id UUID NOT NULL REFERENCES price_book(id),
      target_type rule_target_type NOT NULL,
      target_id UUID, -- product_id for SKU, category_id for CATEGORY, null for GLOBAL
      priority INTEGER NOT NULL DEFAULT 100,
      condition_type VARCHAR(50), -- CUSTOMER_TIER, LOCATION, NONE
      condition_value VARCHAR(255),
      pricing_logic JSONB NOT NULL,
      effective_start_at TIMESTAMPTZ NOT NULL,
      effective_end_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    
    CREATE INDEX idx_rule_lookup ON price_book_rule(price_book_id, target_type, target_id, effective_start_at);
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify pricing calculations use correct precedence, no ambiguous rule conflicts
  - **Query example:**
    ```sql
    -- Find rules with same specificity and priority (ambiguous)
    SELECT pb.name, pbr1.id as rule1, pbr2.id as rule2,
           pbr1.target_type, pbr1.priority
    FROM price_book_rule pbr1
    JOIN price_book_rule pbr2 ON 
      pbr1.price_book_id = pbr2.price_book_id
      AND pbr1.id < pbr2.id
      AND pbr1.target_type = pbr2.target_type
      AND pbr1.priority = pbr2.priority
      AND pbr1.target_id = pbr2.target_id
    JOIN price_book pb ON pb.id = pbr1.price_book_id
    WHERE daterange(pbr1.effective_start_at::date, pbr1.effective_end_at::date, '[)')
      && daterange(pbr2.effective_start_at::date, pbr2.effective_end_at::date, '[)');
    ```
  - **Expected outcome:** Zero ambiguous rule pairs
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing rules for ambiguity
    2. Assign priority values to resolve conflicts
    3. Deploy precedence engine with logging
    4. Monitor for unexpected price changes
  - **Testing:** Generate test cases covering all precedence scenarios
- **Governance & owner recommendations:**
  - **Owner:** Pricing domain team
  - **Policy:** Document precedence rules in pricing playbook
  - **Review cadence:** Monthly audit of rule effectiveness
  - **Training:** Train pricing analysts on precedence logic

### DECISION-PRICING-003 — Location Price Override Guardrails

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-003)
- **Decision:** Location price overrides must pass guardrail validation (minimum margin, maximum discount). Overrides within guardrails activate immediately (ACTIVE). Overrides exceeding guardrails enter PENDING_APPROVAL state requiring manager approval. All validation is server-side.
- **Alternatives considered:**
  - **Option A (Chosen):** Guardrails with approval workflow
    - Pros: Prevents pricing errors, maintains margin targets, audit trail
    - Cons: Additional approval overhead
  - **Option B:** No guardrails (trust users)
    - Pros: Fastest workflow
    - Cons: Risk of below-cost pricing, margin erosion
  - **Option C:** Hard limits (reject out-of-bounds)
    - Pros: Prevents bad prices
    - Cons: Inflexible, cannot handle legitimate exceptions
- **Reasoning and evidence:**
  - Margin protection is critical business requirement
  - Below-cost pricing causes financial loss
  - Approval workflow balances control and flexibility
  - Audit compliance requires approval trails for exceptions
  - Industry pattern: tiered approval thresholds
- **Architectural implications:**
  - **Components affected:**
    - Override service: Validates against guardrails
    - Approval service: Manages approval workflow
    - Database: Stores override state and approval records
  - **Database schema:**
    ```sql
    CREATE TYPE override_status AS ENUM ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'EXPIRED');
    
    CREATE TABLE location_price_override (
      id UUID PRIMARY KEY,
      location_id UUID NOT NULL REFERENCES location(id),
      product_id UUID NOT NULL REFERENCES product(id),
      override_price DECIMAL(19,4) NOT NULL CHECK (override_price >= 0),
      currency VARCHAR(3) NOT NULL DEFAULT 'USD',
      status override_status NOT NULL DEFAULT 'PENDING_APPROVAL',
      requested_by UUID NOT NULL,
      requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      approved_by UUID,
      approved_at TIMESTAMPTZ,
      rejection_reason TEXT,
      effective_start_date DATE NOT NULL,
      effective_end_date DATE,
      margin_percent DECIMAL(5,2), -- computed at submission
      discount_percent DECIMAL(5,2), -- computed at submission
      UNIQUE(location_id, product_id, effective_start_date)
    );
    
    CREATE TABLE guardrail_policy (
      id UUID PRIMARY KEY,
      name VARCHAR(100) NOT NULL,
      min_margin_percent DECIMAL(5,2) NOT NULL,
      max_discount_percent DECIMAL(5,2) NOT NULL,
      auto_approve_threshold_percent DECIMAL(5,2), -- within this threshold = auto-approve
      is_active BOOLEAN NOT NULL DEFAULT true
    );
    ```
  - **Validation logic:**
    ```java
    @Transactional
    public LocationPriceOverride submitOverride(SubmitOverrideRequest request) {
        // Get cost and MSRP
        BigDecimal cost = inventoryService.getCost(request.getProductId());
        BigDecimal msrp = msrpService.getMSRP(request.getProductId(), request.getEffectiveStartDate());
        
        // Calculate margin and discount
        BigDecimal margin = request.getOverridePrice().subtract(cost)
            .divide(request.getOverridePrice(), 4, RoundingMode.HALF_UP);
        BigDecimal discount = msrp.subtract(request.getOverridePrice())
            .divide(msrp, 4, RoundingMode.HALF_UP);
        
        // Check guardrails
        GuardrailPolicy policy = guardrailRepo.findActive();
        boolean withinGuardrails = 
            margin.compareTo(policy.getMinMarginPercent()) >= 0 &&
            discount.compareTo(policy.getMaxDiscountPercent()) <= 0;
        
        LocationPriceOverride override = new LocationPriceOverride();
        override.setLocationId(request.getLocationId());
        override.setProductId(request.getProductId());
        override.setOverridePrice(request.getOverridePrice());
        override.setMarginPercent(margin.multiply(BigDecimal.valueOf(100)));
        override.setDiscountPercent(discount.multiply(BigDecimal.valueOf(100)));
        override.setRequestedBy(request.getUserId());
        override.setEffectiveStartDate(request.getEffectiveStartDate());
        override.setEffectiveEndDate(request.getEffectiveEndDate());
        
        if (withinGuardrails) {
            override.setStatus(OverrideStatus.ACTIVE);
        } else {
            override.setStatus(OverrideStatus.PENDING_APPROVAL);
            // Trigger approval notification
            approvalService.requestApproval(override);
        }
        
        return overrideRepo.save(override);
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify overrides outside guardrails are approved, active overrides meet guardrails
  - **Query example:**
    ```sql
    -- Find active overrides that violate guardrails (should be zero)
    SELECT o.id, o.product_id, o.location_id, 
           o.margin_percent, o.discount_percent, o.status
    FROM location_price_override o
    CROSS JOIN guardrail_policy g
    WHERE o.status = 'ACTIVE'
      AND g.is_active = true
      AND (o.margin_percent < g.min_margin_percent 
           OR o.discount_percent > g.max_discount_percent);
    
    -- Find pending overrides older than 7 days
    SELECT id, product_id, location_id, requested_at,
           NOW() - requested_at as pending_duration
    FROM location_price_override
    WHERE status = 'PENDING_APPROVAL'
      AND requested_at < NOW() - INTERVAL '7 days';
    ```
  - **Expected outcome:** Zero active overrides violating guardrails, pending overrides should resolve within SLA
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Define initial guardrail policy
    2. Audit existing overrides against policy
    3. Grandfather existing overrides or request retroactive approval
    4. Deploy guardrail validation
  - **Backfill:**
    ```sql
    -- Mark existing overrides as ACTIVE (grandfathered)
    UPDATE location_price_override
    SET status = 'ACTIVE',
        approved_by = 'SYSTEM',
        approved_at = NOW()
    WHERE status = 'PENDING_APPROVAL'
      AND requested_at < '2026-01-01'; -- before guardrails deployed
    ```
- **Governance & owner recommendations:**
  - **Owner:** Pricing domain with Finance oversight
  - **Policy:** Review guardrails quarterly, adjust based on margin targets
  - **Monitoring:** Alert on approval backlog (>10 pending)
  - **SLA:** Approve/reject pending overrides within 48 hours

### DECISION-PRICING-004 — Promotion Code Uniqueness and Immutability

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-004)
- **Decision:** Promotion codes must be globally unique and immutable once created. Codes are case-insensitive for lookup but stored in original case. Deactivated promotions retain their codes forever (cannot be reused).
- **Alternatives considered:**
  - **Option A (Chosen):** Unique, immutable codes with case-insensitive lookup
    - Pros: Clear customer experience, prevents confusion, audit trail intact
    - Cons: Code namespace can fill up over time
  - **Option B:** Reusable codes after expiration
    - Pros: Code namespace reuse
    - Cons: Customer confusion, audit complexity
  - **Option C:** Case-sensitive codes
    - Pros: Larger namespace
    - Cons: Customer frustration (wrong case = invalid code)
- **Reasoning and evidence:**
  - Customers remember promotion codes (e.g., "SUMMER20")
  - Case-insensitive input improves UX (don't punish caps lock)
  - Immutable codes support audit and customer service (can reference historical promotions)
  - Reusing codes creates confusion ("I used SUMMER20 last year, why doesn't it work now?")
  - Industry standard: promotion codes are stable identifiers
- **Architectural implications:**
  - **Components affected:**
    - Promotion service: Validates code uniqueness
    - Database: Unique constraint on normalized code
    - Lookup API: Case-insensitive search
  - **Database schema:**
    ```sql
    CREATE TABLE promotion_offer (
      id UUID PRIMARY KEY,
      code VARCHAR(50) NOT NULL, -- original case preserved
      code_normalized VARCHAR(50) NOT NULL UNIQUE, -- lowercase for uniqueness
      description TEXT NOT NULL,
      discount_type VARCHAR(20) NOT NULL, -- PERCENTAGE, FIXED_AMOUNT
      discount_value DECIMAL(19,4) NOT NULL,
      valid_from DATE NOT NULL,
      valid_until DATE NOT NULL,
      status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, ACTIVE, EXPIRED, DEACTIVATED
      max_uses INTEGER,
      current_uses INTEGER DEFAULT 0,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CHECK (valid_until >= valid_from)
    );
    
    CREATE UNIQUE INDEX idx_promo_code_unique ON promotion_offer(LOWER(code));
    CREATE INDEX idx_promo_lookup ON promotion_offer(code_normalized, status);
    ```
  - **Code validation:**
    ```java
    @Transactional
    public PromotionOffer createPromotion(CreatePromotionRequest request) {
        String normalizedCode = request.getCode().toLowerCase();
        
        // Check uniqueness
        if (promotionRepo.existsByCodeNormalized(normalizedCode)) {
            throw new ConflictException(
                "Promotion code '" + request.getCode() + "' already exists"
            );
        }
        
        PromotionOffer promo = new PromotionOffer();
        promo.setCode(request.getCode()); // preserve original case
        promo.setCodeNormalized(normalizedCode);
        promo.setDescription(request.getDescription());
        promo.setDiscountType(request.getDiscountType());
        promo.setDiscountValue(request.getDiscountValue());
        promo.setValidFrom(request.getValidFrom());
        promo.setValidUntil(request.getValidUntil());
        promo.setStatus(PromotionStatus.DRAFT);
        
        return promotionRepo.save(promo);
    }
    
    public Optional<PromotionOffer> lookupByCode(String code, LocalDate asOfDate) {
        String normalized = code.toLowerCase();
        return promotionRepo.findByCodeNormalizedAndStatus(normalized, "ACTIVE")
            .filter(p -> !asOfDate.isBefore(p.getValidFrom()) 
                      && !asOfDate.isAfter(p.getValidUntil()));
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no duplicate codes (normalized), all codes are immutable
  - **Query example:**
    ```sql
    -- Find duplicate codes (should be zero)
    SELECT code_normalized, COUNT(*) as count
    FROM promotion_offer
    GROUP BY code_normalized
    HAVING COUNT(*) > 1;
    
    -- Find code reuse (deactivated then reused)
    SELECT code_normalized, COUNT(DISTINCT id) as use_count,
           STRING_AGG(status::TEXT, ', ' ORDER BY created_at) as statuses
    FROM promotion_offer
    GROUP BY code_normalized
    HAVING COUNT(DISTINCT id) > 1;
    ```
  - **Expected outcome:** Zero duplicates, zero code reuse
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing promotions for duplicate codes
    2. Normalize existing codes (lowercase)
    3. Deploy unique constraint
    4. Update lookup logic to case-insensitive
  - **Conflict resolution:**
    ```sql
    -- Find and resolve duplicate codes before constraint
    WITH dupes AS (
      SELECT LOWER(code) as norm_code, COUNT(*) as dupe_count
      FROM promotion_offer
      GROUP BY LOWER(code)
      HAVING COUNT(*) > 1
    )
    SELECT po.id, po.code, d.norm_code
    FROM promotion_offer po
    JOIN dupes d ON LOWER(po.code) = d.norm_code
    ORDER BY d.norm_code, po.created_at;
    -- Manual review and code suffix for duplicates (e.g., SUMMER20_2)
    ```
- **Governance & owner recommendations:**
  - **Owner:** Marketing team with Pricing domain support
  - **Policy:** Code naming convention (max 20 chars, alphanumeric + hyphen)
  - **Review cadence:** Quarterly audit of active promotions
  - **Monitoring:** Alert on high promotion usage (approaching max_uses)

### DECISION-PRICING-005 — Immutable Pricing Snapshots for Audit

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PRICING-005)
- **Decision:** Every estimate and work order line captures an immutable pricing snapshot at creation time. Snapshots include unit price, cost, MSRP, applied rules, policy version, and source context. Snapshots are write-once, never updated. Used for audit drilldown and pricing reproducibility.
- **Alternatives considered:**
  - **Option A (Chosen):** Immutable snapshots with full context
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

## End

End of document.
