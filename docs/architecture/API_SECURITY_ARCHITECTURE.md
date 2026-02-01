# Moqui User Management + Spring Boot Backend Authorization Design

## 1. Scope and Goals

This design establishes Moqui as the **system of record** for:
- user identities
- authentication (login/session)
- role assignment

Spring Boot remains the enforcement platform for:
- API protection at the **API Gateway**
- method-level authorization in backend modules using `@PreAuthorize`

Trust between Moqui and the Spring Boot backend is established using a **shared secret** that is **never transmitted**. Moqui uses it to **cryptographically sign** short-lived user assertions. The backend verifies those assertions to authenticate the caller and construct authorities for Spring Security.

All communications use HTTPS.

---

## 2. Components and Responsibilities

### 2.1 Moqui Frontend
- Authenticates users using Moqui’s built-in user management.
- Determines the authenticated user’s identity and roles (Moqui roles).
- Issues a **signed assertion** for each backend call (or for a short time window) that includes:
  - the authorized `userId`
  - the authorized Moqui role(s)
  - anti-replay and expiry data

### 2.2 API Gateway (Spring Boot)
- Serves as the single entry point to backend services.
- Validates the signed Moqui assertion on every request.
- Creates the authenticated `SecurityContext` for downstream services.
- Forwards the request to the target backend module with the authenticated principal and mapped authorities.

### 2.3 Backend Services (Spring Boot Modules)
- Trust the gateway as the authentication boundary.
- Rely on Spring Security + `@PreAuthorize` for authorization decisions.
- Do not call Moqui for identity or role checks during normal request processing.

---

## 3. Trust Model and Security Properties

### 3.1 Shared Secret Usage
- A single shared secret is provisioned to:
  - Moqui (signing)
  - API Gateway (verification)
- The shared secret is stored in secure configuration (not source-controlled) and loaded at runtime.
- The shared secret is used only for **HMAC signing** of assertions (e.g., HS256). The secret itself is never sent in any request.

### 3.2 Assertion Security Requirements
Assertions MUST include:
- **Integrity**: HMAC signature computed over the assertion content.
- **Expiration**: short TTL to limit exposure.
- **Replay protection**: unique identifier per assertion and server-side replay detection.

---

## 4. Signed Assertion Format

The Moqui assertion is a compact token (JWT) signed with HMAC using the shared secret.

### 4.1 Required JWT Header Fields
- `alg`: `HS256`
- `typ`: `JWT`

### 4.2 Required JWT Claims
- `iss`: fixed issuer identifier for Moqui (e.g., `moqui`)
- `aud`: fixed audience identifier for the API Gateway (e.g., `api-gateway`)
- `sub`: the authorized Moqui `userId`
- `roles`: list of Moqui roles asserted for the user (strings)
- `iat`: issued-at timestamp
- `exp`: expiration timestamp (short-lived)
- `jti`: unique token identifier (UUID)

### 4.3 Optional Claims (if multi-tenant or contextual authorization is needed)
- `tenantId`
- `storeId` / `locationId`
- `sessionId`

---

## 5. Request Flow

### 5.1 Moqui → Gateway
1. User authenticates in Moqui (Moqui session established).
2. When Moqui needs to call a backend API:
   - Moqui determines the current authorized `userId`.
   - Moqui resolves the user’s Moqui role(s) required for the call.
   - Moqui constructs the JWT claims and signs the JWT using the shared secret.
3. Moqui calls the API Gateway over HTTPS with:
   - `Authorization: Bearer <moqui_assertion_jwt>`

### 5.2 Gateway Validation and SecurityContext Construction
On each request, the API Gateway performs:

1. **Token extraction**
   - Read `Authorization` header and extract the Bearer token.

2. **Signature verification**
   - Verify JWT signature with the shared secret.

3. **Claim validation**
   - Validate `iss` equals the configured Moqui issuer value.
   - Validate `aud` equals the configured gateway audience value.
   - Validate `exp` is not expired (allow small clock skew if required).
   - Validate required claims exist: `sub`, `roles`, `iat`, `exp`, `jti`.

4. **Replay protection**
   - Maintain a replay cache keyed by `jti` with TTL until `exp`.
   - Reject the request if `jti` has already been seen.
   - Insert `jti` on first acceptance.

5. **Authority mapping**
   - Convert Moqui roles to Spring authorities using deterministic mapping:
     - Moqui role `X` → Spring authority `ROLE_X`
   - Example:
     - `SHOP_MGR` → `ROLE_SHOP_MGR`

6. **Principal creation**
   - Create an authenticated principal:
     - `principalName` = `sub` (Moqui `userId`)
     - `authorities` = mapped Spring authorities
   - Attach additional context from optional claims if needed (e.g., `tenantId`).

7. **SecurityContext population**
   - Set the Spring `SecurityContext` with the authenticated `Authentication` object.

8. **Forwarding**
   - Route the request to the appropriate backend module with the established security context.

---

## 6. Backend Authorization with @PreAuthorize

### 6.1 Authority Model
- Backend services receive authentication from the gateway-derived security context.
- Authorities are present as `ROLE_*` strings mapped from Moqui roles.

### 6.2 Usage
Backend controllers/services use `@PreAuthorize` against these authorities:
- `@PreAuthorize("hasRole('SHOP_MGR')")`
- `@PreAuthorize("hasAuthority('ROLE_ACCOUNTING_CLERK')")`

### 6.3 Identity Access
Backend code obtains the current identity from Spring Security:
- `Authentication.getName()` returns the Moqui `userId` (`sub` claim).
- Authorities reflect Moqui role assertions after mapping.

---

## 7. Configuration and Key Material Handling

### 7.1 Shared Secret Provisioning
- The shared secret is configured independently in Moqui and the API Gateway.
- Storage requirements:
  - encrypted at rest
  - injected via environment variables or a secrets manager at runtime
  - never logged

### 7.2 Gateway Configuration Parameters
- `moqui.issuer` (expected `iss`)
- `gateway.audience` (expected `aud`)
- `hmac.sharedSecret` (verification key)
- `replayCache.ttl` (derived from `exp`, enforced server-side)

---

## 8. Logging and Audit Requirements

The API Gateway logs (without logging tokens):
- request identifier / correlation id
- `sub` (userId)
- resolved authorities
- decision outcome (accepted/rejected) and reason (expired, invalid signature, replay)

Backend services may log:
- correlation id
- authenticated `userId`
- invoked endpoint and authorization outcome
- without recording the assertion token

---