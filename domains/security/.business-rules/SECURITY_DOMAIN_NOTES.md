# SECURITY_DOMAIN_NOTES.md

## Summary

This document provides comprehensive rationale and decision logs for the Security domain. Security manages RBAC (roles, permissions, assignments), user provisioning, authorization decisions, and audit trails. Each decision includes alternatives, architectural implications, audit guidance, and governance.

## Completed items

- [x] Documented 10 key security decisions
- [x] Provided alternatives analysis
- [x] Included architectural schemas
- [x] Added auditor SQL queries
- [x] Defined governance strategies

## Decision details

### DECISION-SECURITY-001 — Deny-by-Default Authorization

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-001)
- **Decision:** All authorization decisions default to DENY unless an explicit permission grant exists. No implicit permissions. Missing permission = access denied.
- **Alternatives considered:**
  - **Option A (Chosen):** Explicit deny-by-default
    - Pros: Secure by default, clear semantics, prevents privilege escalation
    - Cons: Requires explicit permission management
  - **Option B:** Allow-by-default for authenticated users
    - Pros: Simpler initial setup
    - Cons: Security risk, violates least privilege
  - **Option C:** Mixed model (some allow, some deny)
    - Pros: Flexible
    - Cons: Confusing, error-prone, security gaps
- **Reasoning and evidence:**
  - Security best practice: deny-by-default prevents accidental access
  - Compliance requirement: demonstrate access was explicitly granted
  - Least privilege principle: grant minimum necessary permissions
  - Industry standard: all enterprise RBAC systems use deny-by-default
- **Architectural implications:**
  - **Components affected:**
    - Authorization service: Returns DENY unless grant found
    - Permission check: Explicit grant required
  - **Authorization logic:**
    ```java
    public AuthorizationDecision authorize(Principal principal, String permission, Resource resource) {
        // Find all roles for principal
        List<Role> roles = roleService.getRolesForPrincipal(principal.getId());
        
        // Check if any role grants the permission
        for (Role role : roles) {
            if (role.hasPermission(permission)) {
                // Additional checks (scope, resource type, etc.)
                if (matchesScope(role, resource)) {
                    return AuthorizationDecision.ALLOW;
                }
            }
        }
        
        // Default: DENY
        return AuthorizationDecision.DENY;
    }
    ```
  - **Database schema:**
    ```sql
    -- No explicit deny records needed; absence = deny
    CREATE TABLE role_permission (
      role_id UUID NOT NULL REFERENCES role(id),
      permission_key VARCHAR(255) NOT NULL,
      PRIMARY KEY (role_id, permission_key)
    );
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify denied requests have no matching grants
  - **Query example:**
    ```sql
    -- Sample audit: verify denies had no grants
    SELECT al.user_id, al.permission, al.resource_id, al.decision
    FROM authorization_log al
    LEFT JOIN principal_role pr ON pr.principal_id = al.user_id
    LEFT JOIN role_permission rp ON rp.role_id = pr.role_id AND rp.permission_key = al.permission
    WHERE al.decision = 'DENY'
      AND rp.role_id IS NOT NULL; -- should be zero (deny but grant exists = bug)
    ```
  - **Expected outcome:** Zero denials with existing grants
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit current implicit permissions
    2. Create explicit grants for existing access patterns
    3. Deploy deny-by-default logic
    4. Monitor for unexpected denials
  - **Communication:** Notify users that all access requires explicit grants
- **Governance & owner recommendations:**
  - **Owner:** Security domain team
  - **Policy:** Annual review of all permission grants
  - **Monitoring:** Alert on high denial rates (may indicate missing grants)

### DECISION-SECURITY-002 — Permission Key Naming Convention

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-002)
- **Decision:** Permission keys follow strict naming convention: `domain:resource:action` using snake_case. Examples: `pricing:price_book:edit`, `workexec:work_order:cancel`. Keys are immutable once registered.
- **Alternatives considered:**
  - **Option A (Chosen):** Structured naming with validation
    - Pros: Consistent, discoverable, prevents typos, self-documenting
    - Cons: Requires validation infrastructure
  - **Option B:** Free-form permission strings
    - Pros: Maximum flexibility
    - Cons: Inconsistent, hard to audit, typo-prone
  - **Option C:** Numeric permission IDs
    - Pros: Compact storage
    - Cons: Not human-readable, requires lookup
- **Reasoning and evidence:**
  - Structured keys enable permission discovery and grouping
  - Domain prefix supports multi-domain systems
  - Snake_case matches industry standards (AWS IAM, Kubernetes RBAC)
  - Immutability prevents permission drift and audit confusion
  - Validation catches errors at registration time
- **Architectural implications:**
  - **Components affected:**
    - Permission registry: Validates keys at registration
    - Authorization service: Uses keys for lookups
  - **Validation:**
    ```java
    private static final Pattern PERMISSION_PATTERN = 
        Pattern.compile("^[a-z_]+:[a-z_]+:[a-z_]+$");
    
    public void registerPermission(Permission permission) {
        String key = permission.getKey();
        
        if (!PERMISSION_PATTERN.matcher(key).matches()) {
            throw new ValidationException(
                "Permission key must match pattern 'domain:resource:action' using snake_case: " + key
            );
        }
        
        if (permissionRepo.existsByKey(key)) {
            // Idempotent: already registered
            return;
        }
        
        permissionRepo.save(permission);
    }
    ```
  - **Database schema:**
    ```sql
    CREATE TABLE permission (
      key VARCHAR(255) PRIMARY KEY, -- immutable
      description TEXT NOT NULL,
      domain VARCHAR(50) NOT NULL, -- extracted from key
      resource VARCHAR(50) NOT NULL,
      action VARCHAR(50) NOT NULL,
      registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      registered_by VARCHAR(100) -- service name
    );
    
    CREATE INDEX idx_permission_domain ON permission(domain);
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all permissions follow naming convention
  - **Query example:**
    ```sql
    -- Find permissions violating naming convention
    SELECT key, domain, resource, action
    FROM permission
    WHERE key !~ '^[a-z_]+:[a-z_]+:[a-z_]+$';
    ```
  - **Expected outcome:** Zero violations
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Audit existing permissions
    2. Rename non-conforming permissions
    3. Deploy validation
    4. Update services to register permissions on startup
  - **Rename mapping:**
    ```sql
    -- Example: rename old permissions to new convention
    UPDATE role_permission
    SET permission_key = 'pricing:price_book:edit'
    WHERE permission_key = 'EDIT_PRICE_BOOK';
    ```
- **Governance & owner recommendations:**
  - **Owner:** Security team with domain coordination
  - **Policy:** All new permissions must follow convention
  - **Documentation:** Maintain permission catalog by domain

### DECISION-SECURITY-003 — Role Name Case-Insensitive Uniqueness

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-003)
- **Decision:** Role names must be globally unique in a case-insensitive manner. "Manager" and "MANAGER" are considered duplicates. Original case is preserved for display but uniqueness is enforced on normalized (lowercase) name.
- **Alternatives considered:**
  - **Option A (Chosen):** Case-insensitive uniqueness with case-preserved display
    - Pros: Prevents confusion, matches user expectations, consistent
    - Cons: Requires normalized column
  - **Option B:** Case-sensitive uniqueness
    - Pros: Simpler database constraint
    - Cons: Confusing ("Manager" vs "manager" allowed), error-prone
  - **Option C:** Force all-uppercase or all-lowercase
    - Pros: Simplest uniqueness
    - Cons: Ugly display, violates branding (e.g., "CRM Manager")
- **Reasoning and evidence:**
  - Users expect "Manager" and "manager" to be the same role
  - Case-sensitive uniqueness creates duplicate-seeming roles
  - Display case may have branding significance (preserve it)
  - Industry standard: case-insensitive role/user names
- **Architectural implications:**
  - **Components affected:**
    - Role service: Validates uniqueness on normalized name
    - Database: Unique constraint on normalized column
  - **Database schema:**
    ```sql
    CREATE TABLE role (
      id UUID PRIMARY KEY,
      name VARCHAR(100) NOT NULL, -- original case
      name_normalized VARCHAR(100) NOT NULL UNIQUE, -- lowercase
      description TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    
    CREATE UNIQUE INDEX idx_role_name_unique ON role(LOWER(name));
    ```
  - **Validation:**
    ```java
    @Transactional
    public Role createRole(CreateRoleRequest request) {
        String normalized = request.getName().toLowerCase();
        
        if (roleRepo.existsByNameNormalized(normalized)) {
            throw new ConflictException(
                "Role name already exists: " + request.getName()
            );
        }
        
        Role role = new Role();
        role.setName(request.getName()); // preserve case
        role.setNameNormalized(normalized);
        role.setDescription(request.getDescription());
        
        return roleRepo.save(role);
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no duplicate normalized names
  - **Query example:**
    ```sql
    -- Find duplicate normalized names
    SELECT name_normalized, COUNT(*) as count
    FROM role
    GROUP BY name_normalized
    HAVING COUNT(*) > 1;
    ```
  - **Expected outcome:** Zero duplicates
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add name_normalized column
    2. Backfill with lowercase names
    3. Identify and resolve conflicts
    4. Deploy unique constraint
  - **Conflict resolution:**
    ```sql
    -- Find existing case-insensitive duplicates
    SELECT LOWER(name) as norm, STRING_AGG(name, ', ') as variants, COUNT(*)
    FROM role
    GROUP BY LOWER(name)
    HAVING COUNT(*) > 1;
    ```
- **Governance & owner recommendations:**
  - **Owner:** Security team
  - **Policy:** Role names should use title case (e.g., "Price Manager")
  - **Documentation:** Publish role naming guidelines

### DECISION-SECURITY-004 — User Provisioning Event-Driven Architecture

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-004)
- **Decision:** User provisioning creates a User record in Security domain and publishes a UserProvisioned event. People domain subscribes to the event and creates UserPersonLink asynchronously. Provisioning API returns immediately (does not wait for People domain).
- **Alternatives considered:**
  - **Option A (Chosen):** Event-driven async linking
    - Pros: Decoupled domains, fast provisioning, resilient to People domain outages
    - Cons: Eventually consistent, requires event infrastructure
  - **Option B:** Synchronous API call to People domain
    - Pros: Immediate consistency
    - Cons: Tight coupling, slow, brittle (People down = provisioning blocked)
  - **Option C:** Security owns UserPersonLink
    - Pros: No cross-domain coordination
    - Cons: Violates domain boundaries, duplicate person data
- **Reasoning and evidence:**
  - User provisioning is time-sensitive (new employees need immediate access)
  - People domain may have complex validation/workflow
  - Event-driven architecture supports domain independence
  - Eventually consistent UserPersonLink is acceptable (link happens within seconds)
  - Industry pattern: event-driven provisioning (Auth0, Okta)
- **Architectural implications:**
  - **Components affected:**
    - Security service: Creates User, publishes event
    - Event bus: Delivers UserProvisioned event
    - People service: Subscribes to event, creates UserPersonLink
  - **Event schema:**
    ```json
    {
      "eventType": "UserProvisioned",
      "userId": "usr-12345",
      "personId": "per-67890",
      "email": "john.doe@example.com",
      "provisionedBy": "admin-001",
      "provisionedAt": "2026-01-19T18:00:00Z",
      "initialRoles": ["employee"],
      "correlationId": "corr-abc123"
    }
    ```
  - **Provisioning API:**
    ```java
    @Transactional
    public ProvisioningResponse provisionUser(ProvisionUserRequest request) {
        // Create User in Security domain
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPersonId(request.getPersonId());
        userRepo.save(user);
        
        // Assign initial roles
        for (String roleId : request.getInitialRoles()) {
            assignRole(user.getId(), roleId);
        }
        
        // Publish event (async)
        eventPublisher.publish(new UserProvisionedEvent(
            user.getId(),
            request.getPersonId(),
            request.getEmail(),
            request.getInitialRoles()
        ));
        
        return new ProvisioningResponse(user.getId(), "PROVISIONED");
    }
    ```
  - **People subscriber:**
    ```java
    @EventListener
    public void onUserProvisioned(UserProvisionedEvent event) {
        try {
            // Create UserPersonLink
            UserPersonLink link = new UserPersonLink();
            link.setUserId(event.getUserId());
            link.setPersonId(event.getPersonId());
            link.setLinkedAt(Instant.now());
            linkRepo.save(link);
            
            log.info("Linked user {} to person {}", event.getUserId(), event.getPersonId());
        } catch (DataIntegrityViolationException e) {
            // Idempotent: link already exists
            log.debug("Link already exists for user {}", event.getUserId());
        }
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all provisioned users have UserPersonLink
  - **Query example:**
    ```sql
    -- Find users without person links (lag OK if recent)
    SELECT u.id, u.email, u.person_id, u.created_at,
           NOW() - u.created_at as age
    FROM "user" u
    LEFT JOIN user_person_link upl ON upl.user_id = u.id
    WHERE upl.id IS NULL
      AND u.created_at < NOW() - INTERVAL '5 minutes'; -- alert if > 5 min lag
    ```
  - **Expected outcome:** Zero unlinked users older than 5 minutes
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Deploy event infrastructure
    2. Deploy People event subscriber
    3. Update provisioning API to publish events
    4. Backfill missing links for existing users
  - **Backfill:**
    ```sql
    INSERT INTO user_person_link (id, user_id, person_id, linked_at)
    SELECT gen_random_uuid(), u.id, u.person_id, u.created_at
    FROM "user" u
    WHERE NOT EXISTS (
      SELECT 1 FROM user_person_link upl WHERE upl.user_id = u.id
    );
    ```
- **Governance & owner recommendations:**
  - **Owner:** Security team with People team coordination
  - **Monitoring:** Alert on provisioning event lag (> 1 minute)
  - **SLA:** UserPersonLink created within 60 seconds of provisioning

### DECISION-SECURITY-005 — Principal-Role Assignment with Effective Dating

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-005)
- **Decision:** Role assignments support effective dating (start/end dates). Assignments are only active within their effective date range. Supports temporary permissions (e.g., acting manager) and scheduled role changes.
- **Alternatives considered:**
  - **Option A (Chosen):** Effective dating with temporal queries
    - Pros: Supports temporary roles, scheduled changes, audit history
    - Cons: More complex queries
  - **Option B:** No dating (immediate activate/deactivate)
    - Pros: Simpler model
    - Cons: Cannot schedule future changes, manual coordination required
  - **Option C:** Separate temporary assignment table
    - Pros: Clear separation
    - Cons: Duplicate logic, query complexity
- **Reasoning and evidence:**
  - Temporary permissions are common (vacation coverage, acting roles)
  - Scheduled role changes reduce coordination overhead
  - Effective dating provides complete audit history
  - Industry standard: most IAM systems support temporal assignments
- **Architectural implications:**
  - **Components affected:**
    - Assignment service: Validates dates, queries active assignments
    - Authorization service: Checks effective date range
  - **Database schema:**
    ```sql
    CREATE TABLE principal_role (
      id UUID PRIMARY KEY,
      principal_id UUID NOT NULL, -- user or group
      role_id UUID NOT NULL REFERENCES role(id),
      effective_start_at TIMESTAMPTZ NOT NULL,
      effective_end_at TIMESTAMPTZ, -- null = indefinite
      assigned_by UUID NOT NULL,
      assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CHECK (effective_end_at IS NULL OR effective_end_at > effective_start_at)
    );
    
    CREATE INDEX idx_principal_role_active ON principal_role(principal_id, role_id)
    WHERE effective_end_at IS NULL OR effective_end_at > NOW();
    ```
  - **Query active assignments:**
    ```java
    public List<Role> getActiveRoles(UUID principalId, Instant asOf) {
        return principalRoleRepo.findActive(principalId, asOf).stream()
            .map(PrincipalRole::getRole)
            .collect(Collectors.toList());
    }
    
    // SQL
    SELECT r.*
    FROM principal_role pr
    JOIN role r ON r.id = pr.role_id
    WHERE pr.principal_id = ?
      AND pr.effective_start_at <= ?
      AND (pr.effective_end_at IS NULL OR pr.effective_end_at > ?)
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify effective dating is respected in authorization
  - **Query example:**
    ```sql
    -- Find authorizations outside effective date range
    SELECT al.user_id, al.permission, al.timestamp, al.decision,
           pr.effective_start_at, pr.effective_end_at
    FROM authorization_log al
    JOIN principal_role pr ON pr.principal_id = al.user_id
    JOIN role_permission rp ON rp.role_id = pr.role_id AND rp.permission_key = al.permission
    WHERE al.decision = 'ALLOW'
      AND (al.timestamp < pr.effective_start_at 
           OR (pr.effective_end_at IS NOT NULL AND al.timestamp >= pr.effective_end_at));
    ```
  - **Expected outcome:** Zero authorizations outside date range
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add effective date columns (nullable initially)
    2. Backfill existing assignments (start=created_at, end=null)
    3. Update authorization queries to check dates
    4. Deploy assignment scheduling UI
  - **Backfill:**
    ```sql
    UPDATE principal_role
    SET effective_start_at = COALESCE(effective_start_at, assigned_at),
        effective_end_at = NULL
    WHERE effective_start_at IS NULL;
    ```
- **Governance & owner recommendations:**
  - **Owner:** Security team
  - **Policy:** Temporary assignments should have explicit end dates
  - **Monitoring:** Alert on soon-to-expire assignments (7 days warning)

### DECISION-SECURITY-006 — Authorization Decision Caching

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-006)
- **Decision:** Authorization decisions are cached for 5 minutes per (principal, permission, resource) tuple. Cache is invalidated on role changes. Balances performance and security.
- **Alternatives considered:**
  - **Option A (Chosen):** Short TTL cache with invalidation
    - Pros: Fast authorization, reduces DB load, bounded staleness
    - Cons: Slight delay for permission changes
  - **Option B:** No caching (always query DB)
    - Pros: Always accurate
    - Cons: High latency, poor scalability
  - **Option C:** Long TTL cache (30+ minutes)
    - Pros: Best performance
    - Cons: Stale permissions for extended period, security risk
- **Reasoning and evidence:**
  - Authorization checks are high-frequency operations
  - 5-minute staleness is acceptable for most use cases
  - Cache invalidation ensures critical changes propagate quickly
  - Industry standard: short-lived auth caches (AWS, Google use similar TTLs)
- **Architectural implications:**
  - **Components affected:**
    - Authorization service: Implements caching
    - Role management: Invalidates cache on changes
  - **Cache key:**
    ```
    auth:{principalId}:{permission}:{resourceId}
    TTL: 300 seconds (5 minutes)
    ```
  - **Cached authorization:**
    ```java
    public AuthorizationDecision authorize(Principal principal, String permission, Resource resource) {
        String cacheKey = String.format("auth:%s:%s:%s", 
            principal.getId(), permission, resource.getId());
        
        // Check cache
        AuthorizationDecision cached = redis.get(cacheKey, AuthorizationDecision.class);
        if (cached != null) {
            return cached;
        }
        
        // Compute decision
        AuthorizationDecision decision = computeAuthorization(principal, permission, resource);
        
        // Cache result
        redis.set(cacheKey, decision, 300, TimeUnit.SECONDS);
        
        return decision;
    }
    
    public void invalidateCacheForPrincipal(UUID principalId) {
        // Invalidate all cache entries for this principal
        redis.deletePattern("auth:" + principalId + ":*");
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify cache invalidation happens on role changes
  - **Monitoring:**
    ```sql
    -- Track cache effectiveness
    SELECT 
      COUNT(*) as total_checks,
      SUM(CASE WHEN cache_hit THEN 1 ELSE 0 END) as cache_hits,
      ROUND(100.0 * SUM(CASE WHEN cache_hit THEN 1 ELSE 0 END) / COUNT(*), 2) as hit_rate
    FROM authorization_log
    WHERE timestamp >= NOW() - INTERVAL '1 hour';
    ```
  - **Expected outcome:** Cache hit rate > 80%
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Deploy Redis infrastructure
    2. Implement caching layer (disabled initially)
    3. Test cache invalidation
    4. Enable caching with monitoring
  - **Rollback:** Feature flag to disable caching
- **Governance & owner recommendations:**
  - **Owner:** Security team with Infrastructure
  - **Monitoring:** Alert on cache hit rate < 70% or cache unavailability
  - **Review cadence:** Quarterly review of TTL appropriateness

### DECISION-SECURITY-007 — RBAC vs Financial Exception Audit Separation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-007)
- **Decision:** RBAC audit events (role changes, permission grants) are stored separately from financial exception audit events (price overrides, refunds). Each has its own table, schema, and retention policy. Unified audit UI aggregates both.
- **Alternatives considered:**
  - **Option A (Chosen):** Separate tables with unified UI
    - Pros: Optimized schemas, different retention, clear ownership
    - Cons: More complex queries for unified view
  - **Option B:** Single audit table for all events
    - Pros: Simpler schema
    - Cons: Schema bloat, conflicting retention needs, performance issues
  - **Option C:** Completely separate audit systems
    - Pros: Maximum isolation
    - Cons: No unified view, user confusion
- **Reasoning and evidence:**
  - RBAC and financial audits have different requirements
  - RBAC audits: 90-day retention, focus on who/what changed
  - Financial audits: 7-year retention, focus on transaction details
  - Separate tables support different indexes and partitioning strategies
  - Unified UI meets user need for holistic audit view
- **Architectural implications:**
  - **Components affected:**
    - RBAC audit service: Logs RBAC events
    - Financial audit service: Logs financial exception events
    - Audit UI: Aggregates and displays both
  - **Database schemas:**
    ```sql
    -- RBAC audit (90-day retention)
    CREATE TABLE rbac_audit_event (
      id UUID PRIMARY KEY,
      event_type VARCHAR(50) NOT NULL, -- ROLE_CREATED, PERMISSION_GRANTED, etc.
      actor_id UUID NOT NULL,
      subject_type VARCHAR(50) NOT NULL, -- ROLE, PERMISSION, ASSIGNMENT
      subject_id UUID NOT NULL,
      change_details JSONB,
      timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    
    CREATE INDEX idx_rbac_audit_time ON rbac_audit_event(timestamp);
    CREATE INDEX idx_rbac_audit_actor ON rbac_audit_event(actor_id, timestamp);
    
    -- Financial exception audit (7-year retention)
    CREATE TABLE financial_exception_audit (
      id UUID PRIMARY KEY,
      event_type VARCHAR(50) NOT NULL, -- PRICE_OVERRIDE, REFUND, CANCELLATION
      actor_id UUID NOT NULL,
      resource_type VARCHAR(50) NOT NULL, -- ORDER, INVOICE, PAYMENT
      resource_id UUID NOT NULL,
      amount DECIMAL(19,4),
      currency VARCHAR(3),
      reason TEXT,
      approved_by UUID,
      timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    
    CREATE INDEX idx_financial_audit_time ON financial_exception_audit(timestamp);
    CREATE INDEX idx_financial_audit_resource ON financial_exception_audit(resource_type, resource_id);
    ```
  - **Unified query:**
    ```java
    public List<AuditEvent> getAuditTrail(AuditQuery query) {
        List<AuditEvent> events = new ArrayList<>();
        
        // Query RBAC events
        events.addAll(rbacAuditRepo.findByQuery(query));
        
        // Query financial events
        events.addAll(financialAuditRepo.findByQuery(query));
        
        // Sort by timestamp
        events.sort(Comparator.comparing(AuditEvent::getTimestamp).reversed());
        
        return events;
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify audit events are in correct tables with appropriate retention
  - **Query example:**
    ```sql
    -- Verify RBAC audits are purged after 90 days
    SELECT COUNT(*) as old_rbac_events
    FROM rbac_audit_event
    WHERE timestamp < NOW() - INTERVAL '90 days';
    -- Should be zero (auto-purged)
    
    -- Verify financial audits are retained
    SELECT COUNT(*) as financial_events
    FROM financial_exception_audit
    WHERE timestamp < NOW() - INTERVAL '7 years';
    -- Should have historical events
    ```
  - **Expected outcome:** RBAC events purged, financial events retained
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create both audit tables
    2. Deploy separate audit services
    3. Migrate existing audit events to appropriate tables
    4. Deploy unified audit UI
  - **Migration:**
    ```sql
    -- Split existing audit_log into two tables
    INSERT INTO rbac_audit_event (id, event_type, actor_id, subject_type, subject_id, timestamp)
    SELECT id, event_type, actor_id, subject_type, subject_id, timestamp
    FROM audit_log
    WHERE event_type IN ('ROLE_CREATED', 'PERMISSION_GRANTED', 'ASSIGNMENT_ADDED');
    
    INSERT INTO financial_exception_audit (id, event_type, actor_id, resource_type, resource_id, timestamp)
    SELECT id, event_type, actor_id, resource_type, resource_id, timestamp
    FROM audit_log
    WHERE event_type IN ('PRICE_OVERRIDE', 'REFUND', 'CANCELLATION');
    ```
- **Governance & owner recommendations:**
  - **Owner:** Security team for RBAC, Finance team for financial exceptions
  - **Policy:** RBAC 90-day retention, financial 7-year retention
  - **Monitoring:** Alert on audit write failures

### DECISION-SECURITY-008 — Idempotent Permission Grant/Revoke

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-008)
- **Decision:** Granting an already-granted permission is a no-op (returns 200/204, does not create duplicate). Revoking a non-existent grant is a no-op (returns 200/204, does not error). Supports safe retries and scripted provisioning.
- **Alternatives considered:**
  - **Option A (Chosen):** Idempotent with no-op semantics
    - Pros: Safe retries, script-friendly, consistent state
    - Cons: May hide user errors
  - **Option B:** Error on duplicate grant/missing revoke
    - Pros: Explicit feedback
    - Cons: Breaks retries, complicates automation
  - **Option C:** Upsert for grants (update timestamp)
    - Pros: Always updates
    - Cons: Misleading timestamps, not truly idempotent
- **Reasoning and evidence:**
  - Provisioning scripts should be idempotent (run multiple times safely)
  - Network failures cause retries without guaranteed response delivery
  - No-op semantics match desired end state (grant exists or doesn't)
  - Industry standard: AWS IAM policies are idempotent
- **Architectural implications:**
  - **Components affected:**
    - Permission grant service: Checks existence before insert
    - Permission revoke service: Checks existence before delete
  - **Grant logic:**
    ```java
    @Transactional
    public GrantResponse grantPermission(UUID roleId, String permissionKey) {
        // Check if already granted
        if (rolePermissionRepo.exists(roleId, permissionKey)) {
            log.debug("Permission {} already granted to role {}", permissionKey, roleId);
            return new GrantResponse("ALREADY_GRANTED", roleId, permissionKey);
        }
        
        // Create grant
        RolePermission grant = new RolePermission();
        grant.setRoleId(roleId);
        grant.setPermissionKey(permissionKey);
        rolePermissionRepo.save(grant);
        
        // Audit
        auditService.log("PERMISSION_GRANTED", roleId, permissionKey);
        
        return new GrantResponse("GRANTED", roleId, permissionKey);
    }
    
    @Transactional
    public RevokeResponse revokePermission(UUID roleId, String permissionKey) {
        // Check if exists
        Optional<RolePermission> grant = rolePermissionRepo.find(roleId, permissionKey);
        
        if (grant.isEmpty()) {
            log.debug("Permission {} not granted to role {}, no-op", permissionKey, roleId);
            return new RevokeResponse("NOT_GRANTED", roleId, permissionKey);
        }
        
        // Revoke
        rolePermissionRepo.delete(grant.get());
        
        // Audit
        auditService.log("PERMISSION_REVOKED", roleId, permissionKey);
        
        return new RevokeResponse("REVOKED", roleId, permissionKey);
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no duplicate grants, revokes match existing grants
  - **Query example:**
    ```sql
    -- Find duplicate grants (should be zero)
    SELECT role_id, permission_key, COUNT(*) as count
    FROM role_permission
    GROUP BY role_id, permission_key
    HAVING COUNT(*) > 1;
    ```
  - **Expected outcome:** Zero duplicates
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add idempotency checks to grant/revoke logic
    2. Deploy with logging
    3. Monitor for duplicate attempts (info logs)
  - **No data migration needed**
- **Governance & owner recommendations:**
  - **Owner:** Security team
  - **Policy:** Provisioning scripts should use idempotent operations
  - **Documentation:** Document idempotent semantics in API guide

### DECISION-SECURITY-009 — Code-First Permission Registration

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-009)
- **Decision:** Permissions are registered at application startup via code manifests. Services declare their permissions as part of deployment. No UI for creating permissions. Registry is queryable for admin UIs but not editable.
- **Alternatives considered:**
  - **Option A (Chosen):** Code-first with startup registration
    - Pros: Version controlled, code-adjacent, deployment-time validation
    - Cons: Requires service restarts to add permissions
  - **Option B:** UI-driven permission creation
    - Pros: Flexible, no deployments needed
    - Cons: Permissions drift from code, typo risk, no version control
  - **Option C:** Database-driven with migration scripts
    - Pros: Explicit versioning
    - Cons: Separate from code, easy to forget updates
- **Reasoning and evidence:**
  - Permissions are tightly coupled to code (protect specific endpoints/operations)
  - Code changes that add new endpoints should register new permissions
  - Version control provides permission history and review process
  - Startup registration ensures permissions exist before first use
  - Industry pattern: Kubernetes RBAC uses code-defined permissions
- **Architectural implications:**
  - **Components affected:**
    - Service startup: Registers permissions
    - Permission registry: Stores and serves permissions
    - Admin UI: Displays permissions (read-only)
  - **Registration:**
    ```java
    @Configuration
    public class PermissionRegistration {
        @Autowired
        private PermissionRegistry permissionRegistry;
        
        @PostConstruct
        public void registerPermissions() {
            // Register pricing domain permissions
            permissionRegistry.register(new Permission(
                "pricing:price_book:edit",
                "Edit price book rules"
            ));
            permissionRegistry.register(new Permission(
                "pricing:price_override:approve",
                "Approve location price overrides"
            ));
            permissionRegistry.register(new Permission(
                "pricing:promotion:create",
                "Create promotion offers"
            ));
            
            log.info("Registered {} permissions for pricing domain", 3);
        }
    }
    ```
  - **Registry API:**
    ```java
    @RestController
    @RequestMapping("/api/v1/permissions")
    public class PermissionController {
        @GetMapping
        public List<Permission> listPermissions(
            @RequestParam(required = false) String domain) {
            
            if (domain != null) {
                return permissionRegistry.findByDomain(domain);
            }
            return permissionRegistry.findAll();
        }
        
        // No POST/PUT/DELETE endpoints (read-only)
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify permissions exist in code manifests
  - **Validation:**
    ```bash
    # Git history shows permission changes
    git log --oneline -- **/PermissionRegistration.java
    ```
  - **Expected outcome:** All permissions have code definitions
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create permission registration infrastructure
    2. Add registration code to all services
    3. Deploy services (permissions registered on startup)
    4. Verify registry is populated
  - **Backfill:**
    ```sql
    -- Identify permissions not in registry
    SELECT DISTINCT permission_key
    FROM role_permission
    WHERE permission_key NOT IN (SELECT key FROM permission);
    -- Register missing permissions manually (one-time)
    ```
- **Governance & owner recommendations:**
  - **Owner:** Each domain team registers their own permissions
  - **Policy:** New permissions require code review
  - **Documentation:** Maintain permission catalog by domain in docs

### DECISION-SECURITY-010 — Transactional Outbox for Event Reliability

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SECURITY-010)
- **Decision:** User provisioning and RBAC change events use transactional outbox pattern. Events are written to outbox table in same transaction as domain changes. Background worker publishes events to message bus. Guarantees event delivery without distributed transactions.
- **Alternatives considered:**
  - **Option A (Chosen):** Transactional outbox with background publisher
    - Pros: Reliable event delivery, no distributed transactions, eventual consistency
    - Cons: Slight delay for event delivery, outbox maintenance
  - **Option B:** Direct event bus publish in transaction
    - Pros: Immediate delivery
    - Cons: Requires distributed transaction (XA), brittle, slow
  - **Option C:** No events (synchronous calls only)
    - Pros: Simplest
    - Cons: Tight coupling, no async processing
- **Reasoning and evidence:**
  - User provisioning must be reliable (lost events = unlinked users)
  - Distributed transactions (2PC) are fragile and slow
  - Transactional outbox guarantees at-least-once delivery
  - Background publisher can retry failed publishes
  - Industry standard: reliable event publishing (Kafka Connect, Debezium)
- **Architectural implications:**
  - **Components affected:**
    - Domain service: Writes to outbox in transaction
    - Outbox publisher: Background job that publishes events
    - Message bus: Receives published events
  - **Database schema:**
    ```sql
    CREATE TABLE event_outbox (
      id UUID PRIMARY KEY,
      event_type VARCHAR(100) NOT NULL,
      aggregate_id UUID NOT NULL,
      payload JSONB NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      published_at TIMESTAMPTZ,
      publish_attempts INTEGER DEFAULT 0
    );
    
    CREATE INDEX idx_outbox_pending ON event_outbox(created_at)
    WHERE published_at IS NULL;
    ```
  - **Write to outbox:**
    ```java
    @Transactional
    public User provisionUser(ProvisionUserRequest request) {
        // Create user
        User user = new User();
        user.setEmail(request.getEmail());
        userRepo.save(user);
        
        // Write event to outbox (same transaction)
        EventOutbox outboxEntry = new EventOutbox();
        outboxEntry.setEventType("UserProvisioned");
        outboxEntry.setAggregateId(user.getId());
        outboxEntry.setPayload(serializeEvent(user, request));
        outboxRepo.save(outboxEntry);
        
        // Transaction commits: both user and outbox entry saved atomically
        return user;
    }
    ```
  - **Background publisher:**
    ```java
    @Scheduled(fixedDelay = 5000) // every 5 seconds
    public void publishPendingEvents() {
        List<EventOutbox> pending = outboxRepo.findPending(100);
        
        for (EventOutbox entry : pending) {
            try {
                // Publish to message bus
                eventBus.publish(entry.getEventType(), entry.getPayload());
                
                // Mark as published
                entry.setPublishedAt(Instant.now());
                outboxRepo.save(entry);
                
            } catch (Exception e) {
                // Increment attempt count
                entry.setPublishAttempts(entry.getPublishAttempts() + 1);
                outboxRepo.save(entry);
                
                log.error("Failed to publish event {}, attempt {}", 
                    entry.getId(), entry.getPublishAttempts(), e);
            }
        }
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all domain changes have corresponding outbox entries
  - **Query example:**
    ```sql
    -- Find unpublished events older than 5 minutes (alert threshold)
    SELECT id, event_type, aggregate_id, created_at, publish_attempts
    FROM event_outbox
    WHERE published_at IS NULL
      AND created_at < NOW() - INTERVAL '5 minutes'
    ORDER BY created_at;
    ```
  - **Expected outcome:** Zero aged unpublished events under normal operation
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create event_outbox table
    2. Update domain services to write to outbox
    3. Deploy outbox publisher job
    4. Monitor for publishing lag
  - **Cleanup:**
    ```sql
    -- Archive old published events (retain 90 days)
    DELETE FROM event_outbox
    WHERE published_at < NOW() - INTERVAL '90 days';
    ```
- **Governance & owner recommendations:**
  - **Owner:** Security team with Platform team for outbox infrastructure
  - **Monitoring:** Alert on unpublished events older than 1 minute
  - **Maintenance:** Weekly cleanup of old outbox entries

## End

End of document.
