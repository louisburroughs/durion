Title: [BACKEND] [STORY] Master: Set Product Lifecycle State (Active/Discontinued) with Effective Dates
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/55
Labels: type:story, domain:inventory, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:ready-for-dev

---

## Story Intent
**As a** Product Manager,
**I want to** set and change the lifecycle state of a product (Active, Inactive, Discontinued),
**so that** the system enforces which products are available for sale and which are not.

## Actors & Stakeholders
- **Primary Actor:** `Product Manager` or `Inventory Admin` (sets the state).
- **Secondary Actor:** `Authorization Engine` (enforces permission to override discontinued products).
- **Indirect User:** `Service Advisor` (sees product availability via UI; may be prevented from selecting discontinued products).
- **Stakeholder:** `Sales Manager` (needs visibility into lifecycle changes for reporting).

## Preconditions
- A product exists in the Product Catalog.
- The user has permission to modify product lifecycle state (implicit `product:lifecycle:update` permission).

## Functional Behavior
1. **Input:** A user (Product Manager) navigates to a product detail page and opens a "Lifecycle" editor.
2. **Select State:** The user selects a new lifecycle state:
   - `ACTIVE`: Product is actively sellable.
   - `INACTIVE`: Product is temporarily not available for sale (e.g., out of stock, temporarily suspended).
   - `DISCONTINUED`: Product is permanently discontinued.
3. **Set Effective Date:** The user optionally specifies when the state change takes effect (defaults to "immediately").
4. **Confirmation & Audit:** The state change is persisted with:
   - The new state.
   - Effective start timestamp (UTC).
   - User who made the change.
   - Audit reason (optional, but required for overrides).
5. **Return to Availability:** Products marked `INACTIVE` can be returned to `ACTIVE` at any time. Products marked `DISCONTINUED` CANNOT be returned to `ACTIVE` (irreversible).

## Alternate / Error Flows
- **Permission Denied:** If the user lacks permission (e.g., `product:lifecycle:override_discontinued`) to mark a product as discontinued or override an existing discontinued state, the system displays an error and prevents the change.
- **Invalid Effective Date:** If the effective date is in the past or is malformed, the system rejects the change with a validation error.
- **Already Discontinued:** If a product is marked as `DISCONTINUED` and the user attempts to mark it as `ACTIVE` again, the system prevents this with a clear error message.

## Business Rules
- **Irreversibility:** Once marked `DISCONTINUED`, a product **cannot** be returned to `ACTIVE` or `INACTIVE`. The only option is to mark it with a replacement product.
- **Effective Date Semantics:** The lifecycle state change takes effect at the specified UTC timestamp. Prior transactions and commitments are unaffected.
- **Replacement Products:** A discontinued product **may** have one or more replacement products listed. This is informational and does NOT automatically redirect sales.
- **Permissions:** Overriding a product's lifecycle state (e.g., forcing a product back to active when it should remain discontinued) requires an explicit permission: `product:lifecycle:override_discontinued`. This permission is assigned to roles like `ProductAdmin` or (optionally) `PricingManager` or `InventoryController`.

## Data Requirements
### `Product` Entity (Extended Fields)
| Field Name | Type | Description | Example |
|---|---|---|---|
| `productId` | UUID | Primary key. | `prod_abc123` |
| `lifecycleState` | Enum | One of: ACTIVE, INACTIVE, DISCONTINUED. | `ACTIVE` |
| `lifecycleStateEffectiveAt` | TimestampZ | UTC timestamp when the state becomes effective. | `2023-10-27T08:00:00Z` |
| `lastStateChangedBy` | UUID (UserRef) | The user who last changed the state. | `user_xyz789` |
| `lastStateChangedAt` | TimestampZ | When the state was last changed. | `2023-10-27T08:00:00Z` |
| `lifecycleOverrideReason` | String | Reason for overriding a discontinued state (optional, required if override permission used). | `Reintroduced per customer request` |

### `ProductReplacement` Entity (New)
| Field Name | Type | Description | Example |
|---|---|---|---|
| `replacementId` | UUID | Primary key. | `repl_001` |
| `originalProductId` | UUID (ProductRef) | The discontinued product. | `prod_abc123` |
| `replacementProductId` | UUID (ProductRef) | The recommended replacement. | `prod_def456` |
| `priorityOrder` | Integer | Display order (lower = higher priority). | `1` |
| `notes` | String | Optional context for the replacement. | `Upgraded to v2.0 with better performance` |
| `effectiveAt` | TimestampZ | When this replacement is valid. | `2023-10-27T08:00:00Z` |

## Acceptance Criteria
**AC-1: Set a Product to INACTIVE**
- **Given** a product `PROD-A` with state `ACTIVE`
- **And** I have permission `product:lifecycle:update`
- **When** I set the state to `INACTIVE` with an effective date of `2023-11-01T00:00:00Z`
- **Then** the product state is updated to `INACTIVE`
- **And** the `lifecycleStateEffectiveAt` is set to `2023-11-01T00:00:00Z`
- **And** an audit event `ProductLifecycleChanged` is published with the user, reason, and effective date.

**AC-2: Set a Product to DISCONTINUED**
- **Given** a product `PROD-B` with state `ACTIVE`
- **And** I have permission `product:lifecycle:override_discontinued`
- **When** I set the state to `DISCONTINUED` with override reason `End of Life`
- **Then** the product state is updated to `DISCONTINUED`
- **And** the product can no longer be sold (Service Advisor cannot add it to Work Orders).

**AC-3: Prevent Re-activation of a Discontinued Product**
- **Given** a product `PROD-C` with state `DISCONTINUED`
- **When** I attempt to set the state to `ACTIVE` or `INACTIVE`
- **Then** the system rejects the change with the error message: "Discontinued products cannot be reactivated. Specify a replacement product instead."

**AC-4: Add Replacement Product to Discontinued Item**
- **Given** product `PROD-D` is `DISCONTINUED`
- **And** I have permission `product:lifecycle:update`
- **When** I add `PROD-E` as a replacement with priority `1`
- **Then** the system creates a `ProductReplacement` record linking `PROD-D` → `PROD-E`
- **And** the replacement is visible in the product's lifecycle detail page.

**AC-5: Effective Date with Timezone Handling**
- **Given** a user in timezone `America/New_York` enters an effective date of `2023-11-01` (date-only, no time)
- **When** the system processes this lifecycle change
- **Then** the system interprets it as `2023-11-01T00:00:00Z` (start of day UTC, or start of day in the product's governing timezone if specified)
- **And** the state change takes effect at that moment.

## Audit & Observability
- **Audit Trail:** An event `ProductLifecycleChanged` shall be published upon every state change. This event must include:
  - `productId`
  - `oldState`
  - `newState`
  - `effectiveAt`
  - `changedBy` (user)
  - `reason` (optional)
  - `overridePermissionUsed` (boolean)
- **Logging:** All state changes, both successful and denied, must be logged with structured context.
- **Metrics:**
  - `product.lifecycle.state_change.success.count`
  - `product.lifecycle.state_change.denied.count`

---

## Resolved Questions

### Question 1: Lifecycle State List (RESOLVED)

**Question:** What are the exact lifecycle states, and how do they differ?

**Answer:** Three states + one metadata field:

| State | Meaning | Sellable | Can Return To Active | Notes |
|---|---|---|---|---|
| `ACTIVE` | Product is actively available for sale. | ✅ Yes | N/A | Default state. |
| `INACTIVE` | Product is temporarily unavailable (e.g., out of stock, under revision). | ❌ No | ✅ Yes | Reversible. Use for temporary pause. |
| `DISCONTINUED` | Product is permanently end-of-life. | ❌ No | ❌ NO | Irreversible. Use for EOL. |
| `REPLACED_BY` (attribute on `DISCONTINUED`) | If discontinued, which product replaces it? | N/A | N/A | List of `ProductReplacement` records. |

**Clarification:** "Replaced" is **not a state**—it is an optional **attribute** of a discontinued product, captured as a list of replacement suggestions in the `ProductReplacement` entity.

### Question 2: Override Permissions (RESOLVED)

**Question:** Which roles can mark a product as discontinued or override a discontinued decision?

**Answer:** **Permission-based, not role-based.** Use the explicit permission: `product:lifecycle:override_discontinued`

**Assignment Strategy:**
- **Permission Owner:** `domain:security` (defines, assigns, audits)
- **Default Assignees:** `ProductAdmin`, and optionally `PricingManager` or `InventoryController` (configurable per org)
- **Enforcement:** Every state change to `DISCONTINUED` or override of an existing `DISCONTINUED` state MUST use this permission.
- **Audit:** Every use of this permission must be logged with:
  - Who used it
  - When
  - Which product
  - Override reason (mandatory free-text field)
  - Policy version

**Not Role-Hardcoded:** Do not hardcode "only ProductAdmins can mark things discontinued." Instead, use permissions that can be assigned flexibly.

### Question 3: Replacement Cardinality (RESOLVED)

**Question:** Can a single discontinued product have multiple replacement products?

**Answer:** **Yes—one-to-many replacements are explicitly allowed.**

**Implementation:**
- When marking a product as `DISCONTINUED`, the user **may** specify one or more replacements.
- Each replacement is a `ProductReplacement` record with:
  - `originalProductId` (the discontinued product)
  - `replacementProductId` (the new product)
  - `priorityOrder` (display order; lower = higher priority)
  - `notes` (optional context, e.g., "Same function, updated design")
  - `effectiveAt` (when this replacement applies)

**Rationale:**
- Some products have multiple successors (e.g., different capacities, price points, markets).
- Replacement suggestions are **advisory**—they do NOT automatically block or reroute orders.

### Question 4: Effective Date Granularity (RESOLVED)

**Question:** Should the effective date support date-only input, or must it always be a full timestamp?

**Answer:** **Support date-only input; interpret as start of day UTC (or product's governing timezone).**

**Implementation:**
- UI accepts both:
  - Full timestamp: `2023-11-01T14:30:00Z`
  - Date-only: `2023-11-01` (user-provided)
- **Conversion Logic:**
  - If date-only is provided, interpret as `YYYY-MM-DD 00:00:00` in the **product's governing timezone** (if defined).
  - Fallback: If no governing timezone exists, use `00:00:00 UTC`.
- **Storage:** Always store as UTC `TimestampZ` in the database.
- **Example:**
  - User (in `America/New_York`) inputs `2023-11-01` (date-only).
  - If product has governing timezone `America/New_York`, interpret as `2023-11-01T00:00:00 EST` → `2023-11-01T05:00:00 UTC`.
  - If no governing timezone, interpret as `2023-11-01T00:00:00 UTC`.

---

## Original Story (Unmodified – For Traceability)
## [STORY] Master: Set Product Lifecycle State (Active/Discontinued) with Effective Dates

**Description**:

This story covers the ability to set and change a product's lifecycle state (Active → Inactive, Inactive → Active, Active → Discontinued). The system should enforce lifecycle states so that discontinued products cannot be sold, and should record the effective date and reason for the change.

**Domain**: Product & Catalog

**Actors**:
- Product Manager
- Inventory Administrator

**Narrative**:
As a **Product Manager**, I want to mark products as **Active**, **Inactive**, or **Discontinued** with an effective date, so that the system enforces which products can be sold and which cannot.

**Acceptance Criteria** (Initial):
1. ✅ Lifecycle states: ACTIVE, INACTIVE, DISCONTINUED
2. ✅ Only authorized users (with `product:lifecycle:override_discontinued` permission) can mark a product as Discontinued
3. ✅ Discontinued products are not selectable in Work Order/Estimate entry
4. ✅ Effective date is stored; changes only take effect at the specified moment
5. ✅ Audit trail: all changes logged with reason, user, timestamp

---
*This issue was automatically created by the Durion Workspace Agent*