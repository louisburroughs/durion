# Accounting Dimension Schema

**Version:** 1.0  
**Purpose:** Define authoritative dimension fields for GL mappings and journal entry lines  
**Domain:** accounting  
**Owner:** Accounting Domain  
**Status:** ACCEPTED  
**Date:** 2026-01-25

---

## Overview

This document defines the complete dimension schema used in GL mappings and journal entry lines. Dimensions provide multi-dimensional analysis of financial transactions across business units, locations, departments, cost centers, and other organizational structures.

All dimensions are **optional** at the mapping level but may become **required** per posting category policy. Dimensions are stored as `Map<String, String>` in JSON format for flexibility.

---

## Standard Dimension Fields

### 1. Business Unit (`businessUnitId`)

**Type:** String (UUIDv7 or entity reference)  
**Max Length:** 50 chars  
**Description:** Top-level organizational unit (division, region, subsidiary)  
**Required:** Optional (configurable per posting category)  
**Validation:** Must reference valid BusinessUnit entity (if enforced)

**Example Values:**
- `BU-001` — North America Division
- `BU-002` — Europe Division
- `BU-CORP` — Corporate

**Use Cases:**
- Segment P&L by division
- Consolidate multi-entity financials
- Region-specific reporting

---

### 2. Location (`locationId`)

**Type:** String (UUIDv7 or entity reference)  
**Max Length:** 50 chars  
**Description:** Physical location (store, warehouse, office)  
**Required:** Optional (configurable per posting category)  
**Validation:** Must reference valid Location entity (from Location domain)

**Example Values:**
- `LOC-123` — Main Street Store
- `LOC-456` — Distribution Center
- `LOC-REMOTE` — Remote Work

**Use Cases:**
- Track profitability by store
- Allocate overhead by location
- Location-specific GL accounts

---

### 3. Department (`departmentId`)

**Type:** String (UUIDv7 or entity reference)  
**Max Length:** 50 chars  
**Description:** Functional department (sales, service, parts, admin)  
**Required:** Optional (configurable per posting category)  
**Validation:** Must reference valid Department entity (if enforced)

**Example Values:**
- `DEPT-SALES` — Sales Department
- `DEPT-SERVICE` — Service Department
- `DEPT-PARTS` — Parts Department
- `DEPT-ADMIN` — Administration

**Use Cases:**
- Departmental P&L
- Cost allocation by function
- Budget vs actual by department

---

### 4. Cost Center (`costCenterId`)

**Type:** String (UUIDv7 or entity reference)  
**Max Length:** 50 chars  
**Description:** Cost allocation unit (finer than department)  
**Required:** Optional (configurable per posting category)  
**Validation:** Must reference valid CostCenter entity (if enforced)

**Example Values:**
- `CC-SVC-TECH1` — Service Technician Bay 1
- `CC-PARTS-INV` — Parts Inventory
- `CC-ADMIN-HR` — HR Cost Center

**Use Cases:**
- Granular cost tracking
- Activity-based costing
- Overhead allocation

---

### 5. Project (`projectId`)

**Type:** String (UUIDv7 or entity reference)  
**Max Length:** 50 chars  
**Description:** Project or campaign (optional, for project accounting)  
**Required:** Optional (rarely required)  
**Validation:** Must reference valid Project entity (if enforced)

**Example Values:**
- `PROJ-2026-Q1-PROMO` — Q1 2026 Promotion
- `PROJ-FACILITY-UPGRADE` — Facility Upgrade Project
- `PROJ-IT-MIGRATION` — IT System Migration

**Use Cases:**
- Project profitability
- Grant tracking
- Campaign ROI analysis

---

### 6. Product Line (`productLineId`)

**Type:** String (UUIDv7 or entity reference)  
**Max Length:** 50 chars  
**Description:** Product family or service line (optional)  
**Required:** Optional (rarely required)  
**Validation:** Must reference valid ProductLine entity (if enforced)

**Example Values:**
- `PL-VEHICLES` — Vehicle Sales
- `PL-PARTS` — Parts Sales
- `PL-SERVICE` — Service Revenue
- `PL-WARRANTY` — Warranty Services

**Use Cases:**
- Product line profitability
- Revenue mix analysis
- Service vs product revenue split

---

## Dimension Validation Rules

### Required vs Optional

- **Default:** All dimensions are **optional** unless posting category specifies otherwise
- **Per Category:** Posting categories may enforce specific dimensions as required
- **Backend Validation:** If dimension is required per policy, backend returns 422 if missing

### Data Type Enforcement

- All dimensions are **String** values (UUIDs or entity codes)
- Max length: **50 characters** per dimension
- Null/empty strings treated as "not specified"

### Entity Reference Validation

- If dimension references external entity (BusinessUnit, Location, Department), backend may validate:
  - Entity exists
  - Entity is active
  - User has permission to use entity
- Validation is **opt-in per posting category** (default: no validation for flexibility)

### Mutual Dependencies

Some dimensions may have dependencies:
- `costCenterId` typically requires `departmentId` (policy-driven)
- `projectId` may require `businessUnitId` (policy-driven)
- Backend enforces dependencies if configured per posting category

---

## JSON Schema Definition

### GL Mapping Dimensions

```json
{
  "dimensions": {
    "businessUnitId": "BU-001",
    "locationId": "LOC-123",
    "departmentId": "DEPT-SERVICE",
    "costCenterId": "CC-SVC-TECH1",
    "projectId": null,
    "productLineId": null
  }
}
```

### Journal Entry Line Dimensions

```json
{
  "lines": [
    {
      "lineId": "JEL-line-11111",
      "glAccountId": "GL-1000",
      "debitAmount": "500.00",
      "creditAmount": "0.00",
      "dimensions": {
        "businessUnitId": "BU-001",
        "locationId": "LOC-123",
        "departmentId": "DEPT-SERVICE",
        "costCenterId": "CC-SVC-TECH1"
      }
    }
  ]
}
```

---

## Dimension Lookup Endpoints

### Business Units

```
GET /v1/organizations/business-units
Response:
[
  {
    "businessUnitId": "BU-001",
    "code": "NA",
    "name": "North America Division",
    "isActive": true
  }
]
```

### Locations

```
GET /v1/locations
Response:
[
  {
    "locationId": "LOC-123",
    "code": "STORE-MAIN",
    "name": "Main Street Store",
    "isActive": true
  }
]
```

### Departments

```
GET /v1/organizations/departments
Response:
[
  {
    "departmentId": "DEPT-SERVICE",
    "code": "SVC",
    "name": "Service Department",
    "isActive": true
  }
]
```

### Cost Centers

```
GET /v1/accounting/cost-centers
Response:
[
  {
    "costCenterId": "CC-SVC-TECH1",
    "code": "TECH1",
    "name": "Service Technician Bay 1",
    "departmentId": "DEPT-SERVICE",
    "isActive": true
  }
]
```

---

## Frontend Input Rendering

### Dimension Input Component (Vue/Quasar Example)

```vue
<template>
  <q-form>
    <!-- Business Unit Selector -->
    <q-select
      v-model="dimensions.businessUnitId"
      label="Business Unit"
      :options="businessUnits"
      option-value="businessUnitId"
      option-label="name"
      clearable
      :rules="dimensionRules.businessUnit"
    />

    <!-- Location Selector -->
    <q-select
      v-model="dimensions.locationId"
      label="Location"
      :options="locations"
      option-value="locationId"
      option-label="name"
      clearable
      :rules="dimensionRules.location"
    />

    <!-- Department Selector -->
    <q-select
      v-model="dimensions.departmentId"
      label="Department"
      :options="departments"
      option-value="departmentId"
      option-label="name"
      clearable
      :rules="dimensionRules.department"
    />

    <!-- Cost Center Selector (dependent on Department) -->
    <q-select
      v-model="dimensions.costCenterId"
      label="Cost Center"
      :options="costCenters"
      option-value="costCenterId"
      option-label="name"
      :disable="!dimensions.departmentId"
      clearable
      :rules="dimensionRules.costCenter"
    />
  </q-form>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';

const dimensions = ref({
  businessUnitId: null,
  locationId: null,
  departmentId: null,
  costCenterId: null,
  projectId: null,
  productLineId: null
});

// Load dimension lookup data
const businessUnits = ref([]);
const locations = ref([]);
const departments = ref([]);
const costCenters = ref([]);

// Validation rules (per posting category policy)
const dimensionRules = computed(() => ({
  businessUnit: requireBusinessUnit.value ? [val => !!val || 'Business Unit is required'] : [],
  location: requireLocation.value ? [val => !!val || 'Location is required'] : [],
  department: requireDepartment.value ? [val => !!val || 'Department is required'] : [],
  costCenter: requireCostCenter.value ? [val => !!val || 'Cost Center is required'] : []
}));

// Watch department changes to filter cost centers
watch(() => dimensions.value.departmentId, (newDept) => {
  if (!newDept) {
    dimensions.value.costCenterId = null; // clear cost center if department cleared
  }
  loadCostCenters(newDept);
});
</script>
```

---

## Dimension Reporting

### Dimension Breakdowns

Financial reports support dimension filtering and grouping:

```
GET /v1/accounting/reports/profit-loss?businessUnitId=BU-001&departmentId=DEPT-SERVICE

Response:
{
  "reportTitle": "Profit & Loss - North America / Service Dept",
  "periodStart": "2026-01-01",
  "periodEnd": "2026-01-31",
  "dimensions": {
    "businessUnitId": "BU-001",
    "departmentId": "DEPT-SERVICE"
  },
  "revenue": "50000.00",
  "expenses": "30000.00",
  "netIncome": "20000.00"
}
```

### Multi-Dimensional Pivot

```
GET /v1/accounting/reports/expenses-by-dimension?groupBy=departmentId,locationId

Response:
{
  "reportTitle": "Expenses by Department and Location",
  "data": [
    {
      "departmentId": "DEPT-SERVICE",
      "locationId": "LOC-123",
      "totalExpenses": "15000.00"
    },
    {
      "departmentId": "DEPT-PARTS",
      "locationId": "LOC-123",
      "totalExpenses": "8000.00"
    }
  ]
}
```

---

## Extension Guidelines

### Adding New Dimensions

To add a new dimension (e.g., `salesChannelId`):

1. **Update Schema:** Add field to this document
2. **Update Backend:** Add field to `GLMapping.dimensions` Map
3. **Update Frontend:** Add selector component to dimension input forms
4. **Update Reporting:** Add dimension to pivot/filter options
5. **Update Tests:** Add test cases for new dimension validation

### Custom Dimensions

Organizations may add custom dimensions beyond the standard set:

```json
{
  "dimensions": {
    "businessUnitId": "BU-001",
    "locationId": "LOC-123",
    "customField1": "VALUE1",
    "customField2": "VALUE2"
  }
}
```

**Guidelines for Custom Dimensions:**
- Use `customField*` prefix for non-standard dimensions
- Max 10 custom dimensions per organization
- Document custom dimensions in organization-specific config
- No validation for custom dimensions (freeform strings)

---

## Testing Requirements

### Dimension Validation Tests

- **Unit Tests:** Validate dimension rules per posting category
- **Integration Tests:** Test dimension lookup endpoints
- **Frontend Tests:** Test dimension selector components
- **Negative Tests:** Test invalid dimension references (if validation enabled)

### Test Scenarios

```java
@Test
public void testRequiredDimensionValidation() {
    // Given: Posting category requires businessUnitId
    PostingCategory category = createCategoryWithRequiredDimensions("businessUnitId");
    
    // When: Create GL mapping without businessUnitId
    GLMappingRequest request = new GLMappingRequest();
    request.setPostingCategoryId(category.getId());
    request.setDimensions(Map.of("locationId", "LOC-123")); // missing businessUnitId
    
    // Then: Expect 422 validation error
    ResponseEntity<?> response = createGLMapping(request);
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
    assertTrue(response.getBody().toString().contains("businessUnitId is required"));
}
```

---

## Change Log

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-01-25 | 1.0 | Backend Team | Initial dimension schema definition |

---

## References

- Accounting Backend Contract Guide: `BACKEND_CONTRACT_GUIDE.md`
- Accounting Permission Taxonomy: `PERMISSION_TAXONOMY.md`
- Location Domain: `domains/location/.business-rules/`
- Organization Domain: `domains/organization/.business-rules/` (future)
