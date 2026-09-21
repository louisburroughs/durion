---
type: Architecture
title: Error Envelope
description: Governs the ApiError body every backend endpoint returns on 4xx/5xx — required and conditional fields, platform fallback codes, and where per-module codes live.
status: Current
tags: [api, error-handling, api-error, adr-0017, adr-0056, adr-0061, openapi]
---

Every non-2xx response from a Durion backend REST API carries the same JSON object —
`com.positivity.shared.error.ApiError` — and a client that can parse five required fields from it
can handle any error any module raises.

Scope note: this document owns the **envelope**. A module's own error codes are documented in that
module's `README.md` in `durion-positivity-backend`, because they change with the controller —
see [Per-module error codes](#per-module-error-codes).

## Schema

```json
{
  "code": "string",
  "message": "string",
  "status": 0,
  "timestamp": "string",
  "correlationId": "string",
  "fieldErrors": [
    {
      "field": "string",
      "message": "string"
    }
  ],
  "referenceId": "string",
  "nextAction": "string",
  "supportAction": "string",
  "conflicts": [
    {
      "severity": "HARD",
      "code": "string",
      "message": "string",
      "overridable": false,
      "affectedResource": "string"
    }
  ],
  "suggestedAlternatives": [
    {
      "startDateTime": "string",
      "endDateTime": "string",
      "reason": "string"
    }
  ]
}
```

## Field Semantics

| Field         | Type             | Always Present | Description |
|---------------|------------------|----------------|-------------|
| `code`        | `string`         | ✅ Yes          | Machine-readable error code (e.g. `ORDER_NOT_FOUND`). Use this in client code for programmatic error handling. |
| `message`     | `string`         | ✅ Yes          | Human-readable description intended for developers or end-user display. Do not rely on the exact phrasing in client logic. |
| `status`      | `integer`        | ✅ Yes          | HTTP status code mirrored in the body for clients that can't access response headers easily. |
| `timestamp`   | `string`         | ✅ Yes          | ISO 8601 UTC timestamp when the error occurred (e.g. `2026-03-17T14:30:00.123456789Z`). |
| `correlationId` | `string`       | ✅ Yes          | UUID identifying this specific request across all services. Include this in bug reports and support tickets. Also present in the `X-Correlation-Id` response header. |
| `fieldErrors` | `array\|null`    | ❌ Conditional  | Present (non-null) when the response contains field-level validation details; typically accompanies validation-related codes such as `VALIDATION_ERROR` or `VALIDATION_FAILED`. Each entry names the offending field and why it failed. Omitted entirely for all other error types. |
| `referenceId` | `string\|null`   | ❌ Conditional  | Reference to a workflow case, review request, or external audit record. Present for guided error flows such as self-registration review. |
| `nextAction`  | `string\|null`   | ❌ Conditional  | Recommended next step for the caller to resolve the error (e.g. "Sign in with the existing account"). May appear with or without `referenceId` — guided flows such as self-registration review pair it with a `referenceId`, while authorization refusals such as `USER_HAS_NO_ROLES` and `MANAGER_APPROVAL_REQUIRED` carry it alone. |
| `supportAction` | `string\|null` | ❌ Conditional  | Investigation guidance for operations or support staff. Not intended for end-user display. |
| `conflicts`   | `array\|null`    | ❌ Conditional  | Itemized conflicts behind a `409` whose cause is a set of named conflicts (ADR-0017 §3), e.g. `SCHEDULING_CONFLICT` from pos-shop-manager (DECISION-SHOPMGMT-002/-011). Each entry has `severity` (`HARD` cannot be overridden, `SOFT` can), `code`, `message`, `overridable` and an optional `affectedResource`. Omitted on every other error. |
| `suggestedAlternatives` | `array\|null` | ❌ Conditional | Alternatives the caller may retry with (`startDateTime`, `endDateTime`, optional `reason`), sent alongside `conflicts` when the service can compute them. |

> **Note:** Fields that are `null` or absent are omitted from the JSON payload entirely (Jackson `@JsonInclude(NON_NULL)`). Clients should treat a missing field as `null`, not as an error.

---

## Payload Examples

### HTTP 400 — Validation Error

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "status": 400,
  "timestamp": "2026-03-17T14:30:00.123456789Z",
  "correlationId": "019507b4-1f3a-7000-8e04-5c9d3a4f6e12",
  "fieldErrors": [
    {
      "field": "quantity",
      "message": "must be greater than 0"
    },
    {
      "field": "customerId",
      "message": "must not be null"
    }
  ]
}
```

### HTTP 404 — Not Found

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "Sales order '019507b4-1f3a-7000-8e04-5c9d3a4f6e12' was not found",
  "status": 404,
  "timestamp": "2026-03-17T14:30:00.123456789Z",
  "correlationId": "019507b4-1f3a-7000-8e04-5c9d3a4f6e12"
}
```

### HTTP 409 — Conflict

```json
{
  "code": "DUPLICATE_PROMO_CODE",
  "message": "Promotion code 'SUMMER25' already exists",
  "status": 409,
  "timestamp": "2026-03-17T14:30:00.123456789Z",
  "correlationId": "019507b4-1f3a-7001-8e04-5c9d3a4f6e12"
}
```

### HTTP 409 — Itemized conflicts

```json
{
  "code": "SCHEDULING_CONFLICT",
  "message": "HARD conflicts cannot be overridden",
  "status": 409,
  "timestamp": "2026-03-17T14:30:00.123456789Z",
  "correlationId": "019507b4-1f3a-7003-8e04-5c9d3a4f6e12",
  "conflicts": [
    {
      "severity": "HARD",
      "code": "BAY_DOUBLE_BOOKED",
      "message": "Bay 1 is already booked for that window",
      "overridable": false,
      "affectedResource": "Bay 1"
    }
  ],
  "suggestedAlternatives": [
    {
      "startDateTime": "2026-06-18T10:00:00-05:00",
      "endDateTime": "2026-06-18T11:00:00-05:00",
      "reason": "Bay 1 free"
    }
  ]
}
```

### HTTP 422 — Business Rule Violation

```json
{
  "code": "RETURN_QUANTITY_EXCEEDED",
  "message": "Return quantity 10 exceeds the original purchase quantity 5",
  "status": 422,
  "timestamp": "2026-03-17T14:30:00.123456789Z",
  "correlationId": "019507b4-1f3a-7002-8e04-5c9d3a4f6e12"
}
```

### HTTP 500 — Internal Server Error

```json
{
  "code": "INTERNAL_ERROR",
  "message": "Unexpected error occurred",
  "status": 500,
  "timestamp": "2026-03-17T14:30:00.123456789Z",
  "correlationId": "019507b4-1f3a-7003-8e04-5c9d3a4f6e12"
}
```

### HTTP 401 — Guided Authentication Error (Security Service)

```json
{
  "code": "ACCOUNT_LOCKED",
  "message": "Account is temporarily locked",
  "status": 401,
  "timestamp": "2026-03-17T14:30:00.123456789Z",
  "correlationId": "019507b4-1f3a-7004-8e04-5c9d3a4f6e12",
  "referenceId": "019507b4-2f3a-8000-9e04-6c9d3a4f7e13",
  "nextAction": "Wait for the lockout period to expire or contact an administrator to unlock your account.",
  "supportAction": "Check the audit log for repeated failed login attempts from this user and determine if this is a brute-force attempt."
}
```

### HTTP 403 — Forbidden

```json
{
  "code": "FORBIDDEN",
  "message": "Access denied",
  "status": 403,
  "timestamp": "2026-03-17T14:30:00.123456789Z",
  "correlationId": "019507b4-1f3a-7005-8e04-5c9d3a4f6e12"
}
```

---

## Platform Fallback Codes (pos-web-common)

Emitted by the shared `GlobalApiExceptionHandler` (auto-configured from `pos-web-common`, see
[ADR-0056](../../adr/0056-platform-global-exception-handling.adr.md)) when no service-specific
advice mapped the exception. Any service may therefore return these in addition to its own
module codes.

| Code | Status | Description |
|------|--------|-------------|
| `DUPLICATE_RESOURCE` | 409 | Unique-constraint violation (SQLSTATE 23505); message names the constraint |
| `REFERENCE_CONFLICT` | 409 | Foreign-key constraint violation |
| `DATA_INTEGRITY_VIOLATION` | 409 | Other database integrity violation |
| `MISSING_REQUIRED_VALUE` | 422 | Not-null violation on a client-supplied column; message names the column |
| `CONSTRAINT_VIOLATION` | 422 | Check-constraint violation; message names the constraint |
| `VALIDATION_ERROR` | 400 | Bean-validation failure (with `fieldErrors`) or malformed request |
| `NO_ENDPOINT` | 404 | No route on this service matches the request path (Spring MVC routing failure) |
| `NOT_FOUND` | 404 | The route exists but the resource does not — a status the application declared. `ResponseStatusException(NOT_FOUND, …)` always answers `Requested resource was not found`; its reason is never echoed, because it routinely embeds the id the client sent (#2076). A `@ResponseStatus(NOT_FOUND)` domain exception answers that same message, or the annotation's own `reason` when it declares one — a compile-time constant, not client data |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported for this path |
| `NOT_ACCEPTABLE` / `PAYLOAD_TOO_LARGE` / `UNSUPPORTED_MEDIA_TYPE` / `REQUEST_REJECTED` | 406/413/415/other 4xx | Framework-rejected request |
| `INTERNAL_ERROR` | 500 | Unhandled exception, or a not-null violation on a server-populated audit column; stack trace logged at ERROR against the `correlationId` |

### Platform codes from pos-security-common

Emitted by `LocationScopeDeniedExceptionHandler`, auto-configured for every servlet module on the
`pos-security-common` classpath and ordered ahead of module advices so the code is never collapsed into a
module's plain `FORBIDDEN` (ADR-0061 §3, #1870).

| Code | Status | Description |
|------|--------|-------------|
| `LOCATION_SCOPE_DENIED` | 403 | Caller holds the permission but not for the requested location: the permission is location-scoped on the caller's token and the requested `locationId` lies under none of the caller's assigned nodes (or the caller has no assigned node, or the location is unknown to the module's replica). Distinct from `FORBIDDEN` — the fix is a different location or a wider assignment, not a different role. The body never echoes the requested id |

---

## Per-module error codes

**A module's own error codes are documented in that module's `README.md`, under `Error codes`.**

| Module | Where |
|---|---|
| `pos-accounting` | [`pos-accounting/README.md`](../../../../durion-positivity-backend/pos-accounting/README.md#error-codes) |
| `pos-catalog` | [`pos-catalog/README.md`](../../../../durion-positivity-backend/pos-catalog/README.md#error-codes) |
| `pos-inventory` | [`pos-inventory/README.md`](../../../../durion-positivity-backend/pos-inventory/README.md#error-codes) |
| `pos-invoice` | [`pos-invoice/README.md`](../../../../durion-positivity-backend/pos-invoice/README.md#error-codes) |
| `pos-order` | [`pos-order/README.md`](../../../../durion-positivity-backend/pos-order/README.md#error-codes) |
| `pos-security-service` | [`pos-security-service/README.md`](../../../../durion-positivity-backend/pos-security-service/README.md#error-codes) |
| `pos-vehicle-inventory` | [`pos-vehicle-inventory/README.md`](../../../../durion-positivity-backend/pos-vehicle-inventory/README.md#error-codes) |
| `pos-workorder` | [`pos-workorder/README.md`](../../../../durion-positivity-backend/pos-workorder/README.md#error-codes) |

A module not listed here documents no codes of its own beyond the platform fallbacks above. That is
not a promise it raises none — it is a gap in that module's README, and the fix is to add the
section there, not to restore a table in this document.

**Why the codes are not here.** A code is minted, renamed or retired in the same pull request as
the controller or advice that throws it. A table in the `durion` repo cannot be edited in that pull
request, so it drifts by one release the moment it is written. The module README travels with the
code, is the first thing a reader of that module opens, and is reachable from the
[knowledge-catalog module entry](../../../knowledge-catalog/backend/). The envelope, by contrast,
changes only by ADR amendment, so it belongs here.

**When you add or change a code**, update the module README's `Error codes` section in the same
change as the controller. Do not add it to this document.

## Every error status carries an `ApiError`

ADR-0017 §3's rule is machine-enforced: a module that publishes an `openapi.yaml` is set to
`errorSchema: STRICT` in
[`pos-openapi-validation/src/test/resources/openapi/module-inventory.yaml`](../../../../durion-positivity-backend/pos-openapi-validation/src/test/resources/openapi/module-inventory.yaml),
which fails the build for any 4xx/5xx response whose declared body is a named schema other than
`ApiError`. Declare it explicitly on every error status — springdoc otherwise infers the
operation's *success* DTO:

```java
@ApiResponse(
        responseCode = "404",
        description = "Order not found",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
```

On an operation whose mapping `produces` a non-JSON type (a PDF, say), set
`mediaType = "application/json"` on the error `@Content`s **and** make the advice preset that
content type, or Accept negotiation drops the body.

**The gap the check cannot close: bodiless errors.** A 4xx/5xx response with no content passes,
because a bodiless error can be legitimate (`pos-catalog`'s `getTreadDesignForProduct` 404 and
`pos-customer`'s `requirementsMet` 401 are both deliberate). So an error declared
`content = @Content` whose runtime returns `ApiError` — or the reverse — still slips through.
Tracked in
[#2114](https://github.com/louisburroughs/durion-positivity-backend/issues/2114).


## Client Handling Guidelines

### Recommended Response Handling

```typescript
interface ApiError {
  // Always present
  code: string;
  message: string;
  status: number;
  timestamp: string;
  correlationId: string;
  // Conditional — see Field Semantics for when each appears
  fieldErrors?: Array<{ field: string; message: string }>;
  referenceId?: string;
  nextAction?: string;
  supportAction?: string;
  // Present together on a 409 caused by named conflicts (ADR-0017 §3).
  // A client that omits these cannot read an itemized 409.
  conflicts?: Array<{
    severity: 'HARD' | 'SOFT';
    code: string;
    message: string;
    overridable: boolean;
    affectedResource?: string;
  }>;
  suggestedAlternatives?: Array<{
    startDateTime: string;
    endDateTime: string;
    reason?: string;
  }>;
}

async function handleApiError(response: Response): Promise<never> {
  const error: ApiError = await response.json();
  
  switch (error.code) {
    case 'VALIDATION_ERROR':
    case 'VALIDATION_FAILED':
    // pos-security-service raises INVALID_REQUEST for framework-level request-binding
    // failures; it carries no fieldErrors, so displayFieldErrors falls back to an empty list.
    case 'INVALID_REQUEST':
      // Display field-level errors to the user
      displayFieldErrors(error.fieldErrors ?? []);
      break;
    case 'ORDER_NOT_FOUND':
    case 'NOT_FOUND':
      navigateTo('/404');
      break;
    case 'FORBIDDEN':
      showPermissionDeniedMessage();
      break;
    default:
      showGenericError(error.message, error.correlationId);
  }
  
  throw new Error(`[${error.correlationId}] ${error.code}: ${error.message}`);
}
```

### Correlation ID

Always log the `correlationId` when an error occurs. Include it in support tickets so errors can be traced across all services in the distributed system.

The same ID is also available in the `X-Correlation-Id` response header for access without parsing the body.

---

## Implementation Notes

- The canonical type is `com.positivity.shared.error.ApiError` in the `pos-shared-dtos` module.
- All error handler methods in `@RestControllerAdvice` classes return `ResponseEntity<ApiError>`.
- Four factory methods are available on the `ApiError` record. Use one of them rather than the
  canonical constructor, whose trailing `null`s change arity whenever the envelope gains a field:
  - `ApiError.of(code, message, status, timestamp, correlationId)` — simple error with no optional fields
  - `ApiError.withFieldErrors(code, message, status, timestamp, correlationId, fieldErrors)` — validation error
  - `ApiError.guided(code, message, status, timestamp, correlationId, referenceId, nextAction, supportAction)` — guided workflow error
  - `ApiError.withConflicts(code, message, status, timestamp, correlationId, conflicts, suggestedAlternatives)` — itemized 409 (ADR-0017 §3); pass `null` for `suggestedAlternatives` when the service cannot compute them
- `timestamp` is a `String`, and the value must come from the injected application `Clock`
  (`Instant.now(clock).toString()`). A no-arg `Instant.now()` fails the ArchUnit rule
  `productionCodeShouldNotUseNoArgNowCalls` (ADR-0024 §4).
