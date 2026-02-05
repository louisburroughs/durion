# Billing Backend Contract Guide

**Version:** 0.1 (CAP:092 draft; pre-OpenAPI)
**Audience:** Backend developers, Frontend developers, API consumers
**Last Updated:** 2026-02-05
**Status:** Draft (pending pos-invoice OpenAPI)

---

## Overview

This guide defines the backend contracts for **Billing Rules** (CAP:092).

**Service topology (current intent):**

- **System of record:** `pos-invoice` owns billing rules and persistence.
- **Facade/provider:** `pos-customer` reads billing rules for a party/customer and exposes them under the CRM namespace.
- **Consumer:** `pos-workorder` (WorkExec) calls the CRM facade when enforcing billing-related constraints (e.g., PO requirement at estimate approval).

This guide is derived from CAP:092 story endpoint definitions (no OpenAPI is available for `pos-invoice` yet).

---

## Conventions

- **API Gateway base URL:** `http://localhost:8080`
- **Versioning:** `/v1/...`
- **Error envelope:** `{ code, message, correlationId, fieldErrors[] }`
- **Idempotency:** Mutations SHOULD accept `Idempotency-Key`.

---

## Domain Model

### BillingRules

Billing rules are authored per party/customer (commercial account) and consumed by downstream domains.

```ts
interface BillingRules {
  partyId: string;
  purchaseOrderRequired: boolean;
  paymentTermsCode: string; // e.g., NET_30
  invoiceDeliveryMethod: InvoiceDeliveryMethod;
  invoiceGroupingStrategy: InvoiceGroupingStrategy;
}

type InvoiceDeliveryMethod = "EMAIL" | "PORTAL" | "MAIL";
type InvoiceGroupingStrategy = "PER_WORKORDER" | "PER_VEHICLE" | "SINGLE_INVOICE";
```

Notes:

- Downstream domains MUST treat billing rules as **authoritative** when present.
- If no rules exist, consumers SHOULD fall back to safe defaults (see WorkExec addendum for CAP:092).

---

## API Endpoints (Draft)

### GET http://localhost:8080/v1/billing/rules/{partyId}

**Summary:** Read billing rules for a party/customer.

**Path params:**
- `partyId` (string, required)

**Responses:**
- `200`: `BillingRules`
- `404`: Not found (no rules configured)

---

### PUT http://localhost:8080/v1/billing/rules/{partyId}

**Summary:** Idempotent upsert of billing rules for a party/customer.

**Rationale:** CAP:092 story text includes a “GET to upsert” inconsistency; this guide standardizes on `PUT` for idempotent upsert.

**Headers:**
- `Idempotency-Key` (optional but recommended)

**Request body:** `BillingRules` (server may ignore/override `partyId` in body and use the path param)

**Responses:**
- `200`: Updated
- `201`: Created
- `400`: Validation error

---

## Cross-Domain Dependency

### CRM Facade Contract

The CRM domain exposes a facade for billing rules under:

- `GET http://localhost:8080/v1/crm/accounts/parties/{partyId}/billingRules`

This endpoint is documented in the CRM contract guide and is expected to call `pos-invoice`.
