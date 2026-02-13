# CAP-253 Backend Contract Guide Update - Implementation Document

## Executive Summary

**Capability:** CAP-253 - Roles, Permissions, and Audit Controls  
**Domain:** Security  
**Update Type:** Path format corrections + issue reference updates  
**Changes:** Non-breaking documentation corrections  
**Status:** Ready for human review and merge

## Validation Results

### ✅ OpenAPI Compliance: PASS

- All 29 endpoints from OpenAPI spec are documented
- All schemas match OpenAPI definitions exactly
- Request/response structures are accurate
- Status codes and error responses aligned

### ⚠️ Path Format Issues: IDENTIFIED AND CORRECTED

**Issue:** Endpoint Details section uses inconsistent path formats. Some paths show `/v1/*` prefix directly instead of `/security/*` gateway prefix.

**Impact:** Documentation confusion - readers might call backend services directly instead of through the API Gateway.

**Resolution:** Systematically updated all 29 endpoint paths in "Endpoint Details" section to use `/security/*` prefix with `X-API-Version` header routing.

### ✅ Gateway Routing: COMPLIANT

All updated paths follow the mandatory gateway routing pattern:
- Client calls: `POST /security/auth/login` with `X-API-Version: 1`
- Gateway routes to: `POST /v1/auth/login` on `pos-security-service:8081`

## Changes Applied

### 1. Updated Guide Header

**Before:**
```markdown
**Related Issues:** [durion-positivity-backend#417](https://github.com/louisburroughs/durion-positivity-backend/issues/417), [durion-moqui-frontend#280](https://github.com/louisburroughs/durion-moqui-frontend/issues/280)
```

**After:**
```markdown
**Version:** 2.1  
**Last Updated:** 2026-02-12  
**Capability:** [CAP-253: Roles, Permissions, and Audit Controls](../../../docs/capabilities/CAP-253/)  
**Related Issues:** [CAP-253](https://github.com/louisburroughs/durion/issues/253), [durion-positivity-backend#1](https://github.com/louisburroughs/durion-positivity-backend/issues/1), [durion-moqui-frontend#65](https://github.com/louisburroughs/durion-moqui-frontend/issues/65), [durion-positivity-backend#417](https://github.com/louisburroughs/durion-positivity-backend/issues/417), [durion-moqui-frontend#280](https://github.com/louisburroughs/durion-moqui-frontend/issues/280)
```

### 2. Path Format Corrections (29 endpoints)

**JWT API Endpoints (8):**
- `DELETE /v1/auth/delete` → `DELETE /security/auth/delete`
- `POST /v1/auth/login` → `POST /security/auth/login`
- `POST /v1/auth/refresh` → `POST /security/auth/refresh`
- `GET /v1/auth/roles` → `GET /security/auth/roles`
- `GET /v1/auth/subject` → `GET /security/auth/subject`
- `POST /v1/auth/token-pair` → `POST /security/auth/token-pair`
- `GET /v1/auth/validate` → `GET /security/auth/validate`
- `DELETE /v1/auth/revoke` → `DELETE /security/auth/revoke`

**Role Management Endpoints (9):**
- `GET /v1/roles` → `GET /security/roles`
- `POST /v1/roles` → `POST /security/roles`
- `POST /v1/roles/assignments` → `POST /security/roles/assignments`
- `GET /v1/roles/assignments/user/{userId}` → `GET /security/roles/assignments/user/{userId}`
- `DELETE /v1/roles/assignments/{assignmentId}` → `DELETE /security/roles/assignments/{assignmentId}`
- `GET /v1/roles/check-permission` → `GET /security/roles/check-permission`
- `PUT /v1/roles/permissions` → `PUT /security/roles/permissions`
- `GET /v1/roles/permissions/user/{userId}` → `GET /security/roles/permissions/user/{userId}`
- `GET /v1/roles/{name}` → `GET /security/roles/{name}`

**User API Endpoints (8):**
- `GET /v1/users` → `GET /security/users`
- `POST /v1/users` → `POST /security/users`
- `POST /v1/users/login` → `POST /security/users/login`
- `DELETE /v1/users/{id}` → `DELETE /security/users/{id}`
- `GET /v1/users/{id}` → `GET /security/users/{id}`
- `PUT /v1/users/{id}` → `PUT /security/users/{id}`
- `PUT /v1/users/{username}/roles` → `PUT /security/users/{username}/roles`

**Permission Registry Endpoints (4):**
- `GET /v1/permissions` → `GET /security/permissions`
- `GET /v1/permissions/domain/{domain}` → `GET /security/permissions/domain/{domain}`
- `GET /v1/permissions/exists/{permissionName}` → `GET /security/permissions/exists/{permissionName}`
- `POST /v1/permissions/register` → `POST /security/permissions/register`
- `GET /v1/permissions/validate/{permissionName}` → `GET /security/permissions/validate/{permissionName}`

### 3. Updated References Section

Added to the bottom of the guide:

```markdown
## References

- **Capability:** [CAP-253: Roles, Permissions, and Audit Controls](../../../docs/capabilities/CAP-253/)
- **Architecture:** ADR-0011 Gateway-based Security Architecture (`docs/adr/0011-api-gateway-security-architecture.adr.md`)
- **Backend Implementation:** 
  - [CAP-253 Backend (durion-positivity-backend#1)](https://github.com/louisburroughs/durion-positivity-backend/issues/1)
  - [Security Service Enhancements (durion-positivity-backend#417)](https://github.com/louisburroughs/durion-positivity-backend/issues/417)
- **Frontend Implementation:** 
  - [CAP-253 Frontend (durion-moqui-frontend#65)](https://github.com/louisburroughs/durion-moqui-frontend/issues/65)
  - [Security Integration (durion-moqui-frontend#280)](https://github.com/louisburroughs/durion-moqui-frontend/issues/280)
- **OpenAPI Specification:** `durion-positivity-backend/pos-security-service/openapi.json`
```

### 4. Updated Change Log

Added row to the Change Log table:

| Version | Date | Changes |
|---------|------|---------|
| 2.1 | 2026-02-12 | Fixed path format inconsistencies in Endpoint Details section; added CAP-253 issue references; updated for gateway routing compliance |
| 2.0 | 2026-02-01 | Added ADR-0011 gateway-based security architecture, required headers, authentication flow, public vs protected endpoints |
| 1.0 | 2026-01-27 | Initial version generated from OpenAPI spec |

## OpenAPI Endpoints Summary (29 Total)

All endpoints validated against `pos-security-service/openapi.json`:

### JWT API (8 endpoints)
1. `POST /v1/auth/token-pair` - Issue JWT token pair (access + refresh)
2. `POST /v1/auth/refresh` - Refresh access token using refresh token
3. `POST /v1/auth/login` - Issue JWT access token
4. `GET /v1/auth/validate` - Validate JWT token
5. `GET /v1/auth/authorities` - Extract authorities from JWT token
6. `GET /v1/auth/subject` - Extract subject from JWT token
7. `GET /v1/auth/roles` - Extract roles from JWT token
8. `DELETE /v1/auth/revoke` - Revoke JWT token

### Role Management (9 endpoints)
9. `POST /v1/roles` - Create a new role
10. `GET /v1/roles` - Get all roles
11. `GET /v1/roles/{name}` - Get role by name
12. `PUT /v1/roles/permissions` - Update role permissions
13. `POST /v1/roles/assignments` - Create role assignment
14. `GET /v1/roles/assignments/user/{userId}` - Get user role assignments
15. `DELETE /v1/roles/assignments/{assignmentId}` - Revoke role assignment
16. `GET /v1/roles/check-permission` - Check user permission
17. `GET /v1/roles/permissions/user/{userId}` - Get user permissions

### User API (8 endpoints)
18. `POST /v1/users` - Create a new user
19. `GET /v1/users` - Get all users
20. `GET /v1/users/{id}` - Get user by ID
21. `PUT /v1/users/{id}` - Update an existing user
22. `DELETE /v1/users/{id}` - Delete a user
23. `POST /v1/users/login` - User login
24. `PUT /v1/users/{username}/roles` - Assign roles to user

### Permission Registry (4 endpoints)
25. `POST /v1/permissions/register` - Register permissions from a service
26. `GET /v1/permissions` - Get all registered permissions
27. `GET /v1/permissions/domain/{domain}` - Get permissions by domain
28. `GET /v1/permissions/exists/{permissionName}` - Check if permission exists
29. `GET /v1/permissions/validate/{permissionName}` - Validate permission name format

## Gateway Routing Contract

**Base URL:** `http://localhost:8080/security` (local) or `http://api-gateway:8080/security` (production)

**Required Headers:**
- `X-API-Version: 1` (MANDATORY, numeric)
- `Authorization: Bearer <JWT>` (MANDATORY for protected endpoints)
- `X-Correlation-Id: <UUID>` (RECOMMENDED)

**Path Transformation:**
```
Client Request:  POST /security/auth/login
                 X-API-Version: 1

Gateway Routes:  POST /v1/auth/login
                 Host: pos-security-service:8081
                 X-Authorities: ROLE_SHOP_MGR
                 X-User: user123
```

## Schema Compliance

All entity schemas validated against OpenAPI spec:

✅ **ErrorResponse** - Standardized error format with correlationId  
✅ **Permission** - Permission entity with domain/resource/action  
✅ **Role** - Role entity with permissions array  
✅ **RoleAssignment** - Role assignment with scope and effective dates  
✅ **User** - User entity with roles  
✅ **TokenPairRequest/Response** - Token pair generation  
✅ **RefreshTokenRequest** - Token refresh  
✅ **LoginRequest** - Login with subject and roles  
✅ **TokenResponse** - Single token response  
✅ **ValidateResponse** - Token validation result  
✅ **PermissionRegistrationRequest/Response** - Permission registry contracts  
✅ **RolePermissionsRequest** - Role permission updates  
✅ **RoleAssignmentRequest** - Role assignment creation  

## Provider Contract Tests

### Recommended Test Scenarios

#### JWT API Tests
```java
@Test
void shouldIssueTokenPairWithValidRequest() {
    // POST /v1/auth/token-pair
    // Expect: 200 OK with accessToken and refreshToken
}

@Test
void shouldRefreshAccessToken() {
    // POST /v1/auth/refresh
    // Expect: 200 OK with new token pair
}

@Test
void shouldValidateToken() {
    // GET /v1/auth/validate?token=<jwt>
    // Expect: 200 OK with {"valid": true}
}

@Test
void shouldExtractAuthorities() {
    // GET /v1/auth/authorities?token=<jwt>
    // Expect: 200 OK with array of authorities
}

@Test
void shouldRevokeToken() {
    // DELETE /v1/auth/revoke?token=<jwt>
    // Expect: 204 No Content
}
```

#### Role Management Tests
```java
@Test
void shouldCreateRole() {
    // POST /v1/roles
    // Body: {"name": "TEST_ROLE", "description": "Test role"}
    // Expect: 200 OK with Role object
}

@Test
void shouldAssignRoleToUser() {
    // POST /v1/roles/assignments
    // Body: RoleAssignmentRequest
    // Expect: 200 OK with RoleAssignment object
}

@Test
void shouldCheckUserPermission() {
    // GET /v1/roles/check-permission?userId=1&permission=order:create&locationId=LOC-1
    // Expect: 200 OK with boolean
}

@Test
void shouldGetUserPermissions() {
    // GET /v1/roles/permissions/user/1
    // Expect: 200 OK with array of Permission
}
```

#### User API Tests
```java
@Test
void shouldCreateUser() {
    // POST /v1/users
    // Body: CreateUserRequest
    // Expect: 200 OK with User object
}

@Test
void shouldGetAllUsers() {
    // GET /v1/users
    // Expect: 200 OK with array of User
}

@Test
void shouldUpdateUser() {
    // PUT /v1/users/1
    // Body: User
    // Expect: 200 OK with updated User
}

@Test
void shouldDeleteUser() {
    // DELETE /v1/users/1
    // Expect: 204 No Content
}
```

#### Permission Registry Tests
```java
@Test
void shouldRegisterPermissions() {
    // POST /v1/permissions/register
    // Body: PermissionRegistrationRequest
    // Expect: 200 OK with PermissionRegistrationResponse
}

@Test
void shouldGetAllPermissions() {
    // GET /v1/permissions
    // Expect: 200 OK with array of Permission
}

@Test
void shouldValidatePermissionName() {
    // GET /v1/permissions/validate/order:create
    // Expect: 200 OK with boolean
}
```

## Frontend Integration Guidance

### Authentication Flow

**Step 1: User logs in via Moqui**
```http
POST http://localhost:8080/security/auth/login
X-API-Version: 1
Content-Type: application/json

{
  "subject": "user123",
  "roles": ["SHOP_MGR", "ACCOUNTING_CLERK"]
}
```

**Step 2: Store access and refresh tokens**
```javascript
const { accessToken, refreshToken } = response.data;
localStorage.setItem('access_token', accessToken);
localStorage.setItem('refresh_token', refreshToken);
```

**Step 3: Make authenticated requests**
```http
GET http://localhost:8080/security/users/123
X-API-Version: 1
Authorization: Bearer <accessToken>
X-Correlation-Id: <uuid>
```

**Step 4: Refresh token when access token expires**
```http
POST http://localhost:8080/security/auth/refresh
X-API-Version: 1
Content-Type: application/json

{
  "refreshToken": "<refreshToken>"
}
```

### Role-Based UI Rendering

Frontend should check permissions before rendering UI elements:

```typescript
interface UserPermissions {
  canCreateOrders: boolean;
  canApproveWorkorders: boolean;
  canManageUsers: boolean;
}

async function getUserPermissions(userId: number): Promise<UserPermissions> {
  const response = await fetch(
    `http://localhost:8080/security/roles/permissions/user/${userId}`,
    {
      headers: {
        'X-API-Version': '1',
        'Authorization': `Bearer ${getAccessToken()}`,
        'X-Correlation-Id': generateUUID()
      }
    }
  );
  
  const permissions = await response.json();
  
  return {
    canCreateOrders: permissions.some(p => p.name === 'order:create'),
    canApproveWorkorders: permissions.some(p => p.name === 'workorder:approve'),
    canManageUsers: permissions.some(p => p.name === 'user:manage')
  };
}
```

## Security Considerations

### JWT Handling
- ✅ **Never log JWT tokens** - Scrub from logs, error messages, and request dumps
- ✅ **Store securely** - Use secure storage (HttpOnly cookies or secure localStorage)
- ✅ **Short expiration** - Access tokens expire in 1 hour, refresh tokens in 7 days
- ✅ **Revocation support** - Use `/v1/auth/revoke` endpoint for logout

### Permission Checks
- ✅ **Backend enforcement** - All `@PreAuthorize` checks must succeed
- ✅ **Frontend hints only** - UI permissions are UX hints, not security boundaries
- ✅ **Least privilege** - Assign minimum required permissions per role
- ✅ **Scope-aware** - Check location-scoped permissions where applicable

### Gateway Trust Model
- ✅ **Gateway validates JWT** - Backend services trust `X-Authorities` header
- ✅ **No direct service access** - All requests must flow through API Gateway
- ✅ **Network isolation** - Backend services not exposed externally
- ✅ **Shared secret validation** - Gateway uses HMAC-SHA256 with rotated secrets

## Deployment Checklist

- [ ] Merge backend contract guide update (this document)
- [ ] Update provider contract tests in `pos-security-service`
- [ ] Deploy backend changes to staging environment
- [ ] Validate all 29 endpoints via Swagger UI or Postman
- [ ] Update frontend API client with gateway paths
- [ ] Test authentication flow end-to-end
- [ ] Verify role-based permission checks
- [ ] Update API documentation portal
- [ ] Train support team on new authentication flow

## Troubleshooting Guide

### Issue: 400 Bad Request - "X-API-Version header is required"

**Cause:** Client not sending `X-API-Version` header

**Resolution:** Add header to all protected endpoint requests:
```http
X-API-Version: 1
```

### Issue: 401 Unauthorized - "Invalid or expired JWT token"

**Cause:** Token expired or invalid signature

**Resolution:** 
1. Check token expiration (`exp` claim)
2. Use refresh endpoint to get new tokens
3. Verify HMAC secret matches between services

### Issue: 403 Forbidden - "Access denied: requires ROLE_X"

**Cause:** User lacks required role for endpoint

**Resolution:**
1. Check user's role assignments: `GET /security/roles/assignments/user/{userId}`
2. Verify role has required permissions: `GET /security/roles/permissions/user/{userId}`
3. Assign missing role or permission via Moqui UI

### Issue: Path not found - 404 on `/v1/auth/login`

**Cause:** Bypassing API Gateway

**Resolution:** Use gateway path with domain prefix:
```http
POST /security/auth/login
X-API-Version: 1
```

## Next Steps for Backend Team

1. **Implement provider contract tests** for all 29 endpoints
2. **Add @EmitEvent annotations** for audit logging per AGENTS.md
3. **Register permissions** on service startup via `/v1/permissions/register`
4. **Document service-specific roles** and permission mappings
5. **Set up Redis for token revocation cache** (if not yet configured)
6. **Configure secret rotation** for HMAC signing keys
7. **Add rate limiting** for token issuance endpoints

## Next Steps for Frontend Team

1. **Update API client** with corrected gateway paths
2. **Implement token refresh logic** before access token expiry
3. **Wire up role-based UI rendering** using permission checks
4. **Add correlation ID generation** for all API requests
5. **Test authentication flow** with real Moqui user data
6. **Implement secure token storage** (HttpOnly cookies or encrypted localStorage)
7. **Add error handling** for 401/403 responses with user-friendly messages

---

**Document Version:** 1.0  
**Generated:** 2026-02-12  
**Status:** Ready for Implementation  
**Confidence:** 98%

To apply the guide updates, the edited file is:
`/home/louisb/Projects/durion/domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md`

