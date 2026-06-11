# Authorization Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four concrete authorization defects: a fatal gateway/security-service version mismatch, 21 permissions missing from the gateway catalog, 23 "dark" permissions that can never appear in any JWT, and an unauthenticated token-issuance endpoint.

**Architecture:** The permission bitset pipeline runs security-service → JWT → gateway → downstream. The security service encodes permissions as bits using `PermissionCode` enum indices. The gateway decodes them using a parallel positional array `GatewayPermissionCatalog.AUTHORITY_BY_BIT`. Both sides must agree on the exact array length and version number. This plan syncs them in one combined version bump (6→8, skipping 7 as an intermediate state), then secures the open endpoint at both the HTTP filter chain and method-security layers.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, JUnit 5 + AssertJ, Maven

---

## Findings Summary

Four defects found in `AUTHORIZATION_MODEL.md §Known Drift`:

| # | Defect | Severity | File |
|---|--------|----------|------|
| 1 | `PermissionCode.CATALOG_VERSION=7`, `GatewayPermissionCatalog.CATALOG_VERSION=6` — gateway startup check throws `IllegalStateException` | Critical | `pos-api-gateway/.../GatewayPermissionCatalog.java` |
| 2 | Gateway `AUTHORITY_BY_BIT` has 241 entries (bits 0–240); `PermissionCode` defines 21 permissions at bits 241–261 — these bits are silently dropped during token decode | Critical | `pos-api-gateway/.../GatewayPermissionCatalog.java` |
| 3 | 23 permissions are referenced in `RoleAuthorityServiceImpl` but absent from `PermissionCode` — they are never encoded into any token, so downstream `@PreAuthorize` checks for them always fail | High | `pos-security-service/.../enums/PermissionCode.java` |
| 4 | `POST /v1/auth/token-pair` is `permitAll()` in both the HTTP filter chain (`SecurityConfig`) and `@PreAuthorize`; any caller who knows a valid username can obtain a token with arbitrary roles | High | `pos-security-service/.../config/SecurityConfig.java`, `pos-security-service/.../controller/JwtController.java` |

---

## File Map

| File | Change |
|------|--------|
| `pos-security-service/src/main/java/com/positivity/securityservice/internal/enums/PermissionCode.java` | Add 23 dark-permission entries at bits 262–284; bump `CATALOG_VERSION` to `8` |
| `pos-api-gateway/src/main/java/com/positivity/gateway/config/GatewayPermissionCatalog.java` | Append 44 `AUTHORITY_BY_BIT` entries (bits 241–284); bump `CATALOG_VERSION` to `8` |
| `pos-security-service/src/main/java/com/positivity/securityservice/internal/config/SecurityConfig.java` | Remove `/v1/auth/token-pair` from `permitAll()` matcher |
| `pos-security-service/src/main/java/com/positivity/securityservice/internal/controller/JwtController.java` | Replace `@PreAuthorize("permitAll()")` with `@PreAuthorize("hasAuthority('security:token:issue_internal')")` on `generateTokenPair` |
| `pos-security-service/src/test/java/com/positivity/securityservice/internal/enums/PermissionCodeTest.java` | Verify all dark permissions are now present and `CATALOG_VERSION` is `8` |
| `pos-api-gateway/src/test/java/com/positivity/gateway/config/SecurityGatewayConfigTest.java` | Verify all bits 0–284 round-trip correctly; verify `CATALOG_VERSION` is `8` |
| `pos-security-service/src/test/java/com/positivity/securityservice/ContractBehaviorIT.java` | Update token-pair test to expect `401` when unauthenticated; add authenticated call test |
| `docs/architecture/AUTHORIZATION_MODEL.md` (in `durion` repo) | Remove the four known-drift items now resolved |

---

## Task 1: Add Dark Permissions to `PermissionCode`

**Context:** 23 permissions appear in `RoleAuthorityServiceImpl` role expansions but have no entry in `PermissionCode`. Because `PermissionBitsetCodec` only encodes `PermissionCode` values, these permissions are silently discarded when tokens are issued. Affected roles include `AP_CLERK` (missing `accounting:ap:approve`, `accounting:ap:reject`), `ACCOUNTANT` (missing `accounting:je:reverse`, `accounting:coa:deactivate`, several accounting:mapping and posting_rules entries), `TECHNICIAN`/`LOCATION_MANAGER` (missing `timekeeping:work_session:*`, `timekeeping:overlap_override`, `workorder:start`), and `SERVICE_ADVISOR`/`LOCATION_MANAGER` (missing `workorder:estimate:submit`, `workorder:estimate:promote`, `workorder:workorder:assign-technician`, `workorder:workorder:generate_invoice`, `workorder:dashboard:view`).

**Files:**
- Modify: `pos-security-service/src/main/java/com/positivity/securityservice/internal/enums/PermissionCode.java:335`
- Test: `pos-security-service/src/test/java/com/positivity/securityservice/internal/enums/PermissionCodeTest.java`

- [ ] **Step 1.1: Write a failing test that asserts all dark permissions are present**

Open `pos-security-service/src/test/java/com/positivity/securityservice/internal/enums/PermissionCodeTest.java` and add this test. (Keep any existing tests; add the new one below them.)

```java
@Test
@DisplayName("all permissions referenced in RoleAuthorityServiceImpl exist in PermissionCode")
void allRoleAuthorityPermissionsExistInPermissionCode() {
    // These were dark permissions — absent from PermissionCode, silently
    // dropped from tokens, causing @PreAuthorize failures in downstream services.
    List<String> darkPermissions = List.of(
            "accounting:ap:approve",
            "accounting:ap:reject",
            "accounting:coa:deactivate",
            "accounting:je:reverse",
            "accounting:mapping:view",
            "accounting:mapping:create",
            "accounting:mapping:edit",
            "accounting:mapping:deactivate",
            "accounting:posting_rules:view",
            "accounting:posting_rules:create",
            "accounting:posting_rules:publish",
            "accounting:posting_rules:archive",
            "timekeeping:work_session:create",
            "timekeeping:work_session:stop",
            "timekeeping:work_session:break_start",
            "timekeeping:work_session:break_stop",
            "timekeeping:overlap_override",
            "workorder:dashboard:view",
            "workorder:estimate:submit",
            "workorder:estimate:promote",
            "workorder:workorder:assign-technician",
            "workorder:workorder:generate_invoice",
            "workorder:start"
    );

    Set<String> knownCodes = Arrays.stream(PermissionCode.values())
            .map(PermissionCode::code)
            .collect(Collectors.toSet());

    assertThat(darkPermissions)
            .allSatisfy(perm -> assertThat(knownCodes)
                    .as("PermissionCode missing entry for '%s'", perm)
                    .contains(perm));
}

@Test
@DisplayName("CATALOG_VERSION is 8")
void catalogVersionIsEight() {
    assertThat(PermissionCode.CATALOG_VERSION).isEqualTo(8);
}
```

Add imports if not already present:
```java
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 1.2: Run the new tests — confirm they fail**

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
mvn test -pl pos-security-service \
    -Dtest="PermissionCodeTest#allRoleAuthorityPermissionsExistInPermissionCode+catalogVersionIsEight" \
    -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: Both tests **FAIL**.

- [ ] **Step 1.3: Add 23 dark-permission entries to `PermissionCode`**

Open `pos-security-service/src/main/java/com/positivity/securityservice/internal/enums/PermissionCode.java`.

After line 335 (`WORKORDER__PARTS__CONSUME(261, "workorder:parts:consume");`), add the new batch. Remove the semicolon from the preceding line and append:

```java
    // ── Workorder (batch 2) ──────────────────────────────────────────────────
    WORKORDER__PARTS__CONSUME(261, "workorder:parts:consume"),

    // ── Accounting (batch 3) ─────────────────────────────────────────────────
    ACCOUNTING__AP__APPROVE(262, "accounting:ap:approve"),
    ACCOUNTING__AP__REJECT(263, "accounting:ap:reject"),
    ACCOUNTING__COA__DEACTIVATE(264, "accounting:coa:deactivate"),
    ACCOUNTING__JE__REVERSE(265, "accounting:je:reverse"),
    ACCOUNTING__MAPPING__VIEW(266, "accounting:mapping:view"),
    ACCOUNTING__MAPPING__CREATE(267, "accounting:mapping:create"),
    ACCOUNTING__MAPPING__EDIT(268, "accounting:mapping:edit"),
    ACCOUNTING__MAPPING__DEACTIVATE(269, "accounting:mapping:deactivate"),
    ACCOUNTING__POSTING_RULES__VIEW(270, "accounting:posting_rules:view"),
    ACCOUNTING__POSTING_RULES__CREATE(271, "accounting:posting_rules:create"),
    ACCOUNTING__POSTING_RULES__PUBLISH(272, "accounting:posting_rules:publish"),
    ACCOUNTING__POSTING_RULES__ARCHIVE(273, "accounting:posting_rules:archive"),

    // ── Timekeeping (batch 3) ─────────────────────────────────────────────────
    TIMEKEEPING__WORK_SESSION__CREATE(274, "timekeeping:work_session:create"),
    TIMEKEEPING__WORK_SESSION__STOP(275, "timekeeping:work_session:stop"),
    TIMEKEEPING__WORK_SESSION__BREAK_START(276, "timekeeping:work_session:break_start"),
    TIMEKEEPING__WORK_SESSION__BREAK_STOP(277, "timekeeping:work_session:break_stop"),
    TIMEKEEPING__OVERLAP_OVERRIDE(278, "timekeeping:overlap_override"),

    // ── Workorder (batch 3) ──────────────────────────────────────────────────
    WORKORDER__DASHBOARD__VIEW(279, "workorder:dashboard:view"),
    WORKORDER__ESTIMATE__SUBMIT(280, "workorder:estimate:submit"),
    WORKORDER__ESTIMATE__PROMOTE(281, "workorder:estimate:promote"),
    WORKORDER__WORKORDER__ASSIGN_TECHNICIAN(282, "workorder:workorder:assign-technician"),
    WORKORDER__WORKORDER__GENERATE_INVOICE(283, "workorder:workorder:generate_invoice"),
    WORKORDER__START(284, "workorder:start");
```

Then update `CATALOG_VERSION` on line 341:

```java
    public static final int CATALOG_VERSION = 8;
```

- [ ] **Step 1.4: Run the new tests — confirm they pass**

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
mvn test -pl pos-security-service \
    -Dtest="PermissionCodeTest#allRoleAuthorityPermissionsExistInPermissionCode+catalogVersionIsEight" \
    -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: Both tests **PASS**.

- [ ] **Step 1.5: Run full `PermissionCodeTest` suite**

```bash
mvn test -pl pos-security-service -Dtest="PermissionCodeTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: All `PermissionCodeTest` tests pass. Fix any failures before continuing.

- [ ] **Step 1.6: Commit**

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
git add pos-security-service/src/main/java/com/positivity/securityservice/internal/enums/PermissionCode.java \
        pos-security-service/src/test/java/com/positivity/securityservice/internal/enums/PermissionCodeTest.java
git commit -m "fix: add 23 dark permissions to PermissionCode and bump catalog to version 8

These permissions existed in RoleAuthorityServiceImpl role expansions but were
absent from PermissionCode, causing them to be silently dropped during token
issuance. Affected roles: AP_CLERK, ACCOUNTANT, TECHNICIAN, LOCATION_MANAGER,
SERVICE_ADVISOR."
```

---

## Task 2: Sync `GatewayPermissionCatalog` to Version 8

**Context:** The gateway decodes `perm_bits` using `GatewayPermissionCatalog.AUTHORITY_BY_BIT`, a positional array where `array[N]` is the Spring Security authority string for bit N. The current array has 241 entries (bits 0–240), so all permissions at bits 241–284 (including the entire batch-2 and the new batch-3) are silently dropped during decode. `PermissionVersionStartupCheck` will throw `IllegalStateException` at gateway startup if it can reach the security service before this fix, because version 6 ≠ 8. Fix both by appending 44 entries and bumping the version.

**Files:**
- Modify: `pos-api-gateway/src/main/java/com/positivity/gateway/config/GatewayPermissionCatalog.java:6,249`
- Test: `pos-api-gateway/src/test/java/com/positivity/gateway/config/SecurityGatewayConfigTest.java`

- [ ] **Step 2.1: Write failing tests for the new catalog entries**

Open `pos-api-gateway/src/test/java/com/positivity/gateway/config/SecurityGatewayConfigTest.java` and add these tests. Keep all existing tests.

```java
@Test
@DisplayName("CATALOG_VERSION is 8")
void catalogVersionIsEight() {
    assertThat(GatewayPermissionCatalog.CATALOG_VERSION).isEqualTo(8);
}

@Test
@DisplayName("AUTHORITY_BY_BIT covers all bits up to 284")
void authorityByBitCoversAllNewEntries() {
    // Spot-check batch-2 entries (previously missing — bits 241-261)
    assertThat(GatewayPermissionCatalog.authorityForBit(241)).isEqualTo("PERM_accounting:events:reprocess");
    assertThat(GatewayPermissionCatalog.authorityForBit(242)).isEqualTo("PERM_accounting:export:request");
    assertThat(GatewayPermissionCatalog.authorityForBit(243)).isEqualTo("PERM_accounting:export:view");
    assertThat(GatewayPermissionCatalog.authorityForBit(244)).isEqualTo("PERM_crm:billing_rules:edit");
    assertThat(GatewayPermissionCatalog.authorityForBit(258)).isEqualTo("PERM_inventory:shortage:resolve");
    assertThat(GatewayPermissionCatalog.authorityForBit(261)).isEqualTo("PERM_workorder:parts:consume");

    // Spot-check batch-3 entries (dark permissions — bits 262-284)
    assertThat(GatewayPermissionCatalog.authorityForBit(262)).isEqualTo("PERM_accounting:ap:approve");
    assertThat(GatewayPermissionCatalog.authorityForBit(263)).isEqualTo("PERM_accounting:ap:reject");
    assertThat(GatewayPermissionCatalog.authorityForBit(274)).isEqualTo("PERM_timekeeping:work_session:create");
    assertThat(GatewayPermissionCatalog.authorityForBit(278)).isEqualTo("PERM_timekeeping:overlap_override");
    assertThat(GatewayPermissionCatalog.authorityForBit(279)).isEqualTo("PERM_workorder:dashboard:view");
    assertThat(GatewayPermissionCatalog.authorityForBit(284)).isEqualTo("PERM_workorder:start");

    // authorityForBit must return null for indices beyond the array
    assertThat(GatewayPermissionCatalog.authorityForBit(285)).isNull();
}

@Test
@DisplayName("each PermissionCode bit index maps to the expected AUTHORITY_BY_BIT entry")
void permissionCodeBitIndicesMatchGatewayCatalog() {
    // Verify no index-off-by-one between the two catalogs for the full set.
    // This catches future regressions if someone adds to one side but not the other.
    for (PermissionCode pc : PermissionCode.values()) {
        String expected = "PERM_" + pc.code();
        String actual = GatewayPermissionCatalog.authorityForBit(pc.bitIndex());
        assertThat(actual)
                .as("bit %d (%s) not found in GatewayPermissionCatalog", pc.bitIndex(), pc.code())
                .isEqualTo(expected);
    }
}
```

Add imports if not already present:
```java
import com.positivity.securityservice.internal.enums.PermissionCode;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
```

> **Note:** `SecurityGatewayConfigTest` is a gateway-module test. `PermissionCode` lives in `pos-security-service`. Check the gateway's `pom.xml` for an existing dependency on `pos-security-service` (or `pos-security-common`) before adding the import. If it is not already a test dependency, add it. If the cross-module import is not available, replace the `PermissionCode` loop with an explicit list of all expected (bitIndex, authority) pairs instead.

- [ ] **Step 2.2: Run the new tests — confirm they fail**

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
mvn test -pl pos-api-gateway \
    -Dtest="SecurityGatewayConfigTest#catalogVersionIsEight+authorityByBitCoversAllNewEntries+permissionCodeBitIndicesMatchGatewayCatalog" \
    -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: All three tests **FAIL**.

- [ ] **Step 2.3: Append 44 entries to `AUTHORITY_BY_BIT` in `GatewayPermissionCatalog`**

Open `pos-api-gateway/src/main/java/com/positivity/gateway/config/GatewayPermissionCatalog.java`.

Find the last entry in `AUTHORITY_BY_BIT` (currently `"PERM_bulkImport:status:read"` — this is the entry at array index 240). Change its trailing comma if needed and append the following block immediately before the closing `};`:

```java
        // ── Batch-2: previously missing from gateway (bits 241-261) ──────────────
        "PERM_accounting:events:reprocess",        // 241
        "PERM_accounting:export:request",           // 242
        "PERM_accounting:export:view",              // 243
        "PERM_crm:billing_rules:edit",              // 244
        "PERM_pricing:base_price:create",           // 245
        "PERM_inventory:ledger:view",               // 246
        "PERM_inventory:location:admin",            // 247
        "PERM_inventory:location:view",             // 248
        "PERM_inventory:pick_list:create",          // 249
        "PERM_inventory:pick_list:execute",         // 250
        "PERM_inventory:pick_list:view",            // 251
        "PERM_inventory:putaway:claim",             // 252
        "PERM_inventory:putaway:execute",           // 253
        "PERM_inventory:putaway:generate",          // 254
        "PERM_inventory:putaway:view",              // 255
        "PERM_inventory:return:view",               // 256
        "PERM_inventory:return:write",              // 257
        "PERM_inventory:shortage:resolve",          // 258
        "PERM_inventory:shortage:view",             // 259
        "PERM_inventory:stock_movement:create",     // 260
        "PERM_workorder:parts:consume",             // 261

        // ── Batch-3: dark permissions now added to PermissionCode (bits 262-284) ─
        "PERM_accounting:ap:approve",              // 262
        "PERM_accounting:ap:reject",               // 263
        "PERM_accounting:coa:deactivate",          // 264
        "PERM_accounting:je:reverse",              // 265
        "PERM_accounting:mapping:view",            // 266
        "PERM_accounting:mapping:create",          // 267
        "PERM_accounting:mapping:edit",            // 268
        "PERM_accounting:mapping:deactivate",      // 269
        "PERM_accounting:posting_rules:view",      // 270
        "PERM_accounting:posting_rules:create",    // 271
        "PERM_accounting:posting_rules:publish",   // 272
        "PERM_accounting:posting_rules:archive",   // 273
        "PERM_timekeeping:work_session:create",    // 274
        "PERM_timekeeping:work_session:stop",      // 275
        "PERM_timekeeping:work_session:break_start", // 276
        "PERM_timekeeping:work_session:break_stop",  // 277
        "PERM_timekeeping:overlap_override",       // 278
        "PERM_workorder:dashboard:view",           // 279
        "PERM_workorder:estimate:submit",          // 280
        "PERM_workorder:estimate:promote",         // 281
        "PERM_workorder:workorder:assign-technician", // 282
        "PERM_workorder:workorder:generate_invoice",  // 283
        "PERM_workorder:start"                     // 284
```

Then update `CATALOG_VERSION` on line 6:

```java
    public static final int CATALOG_VERSION = 8;
```

- [ ] **Step 2.4: Run the three new tests — confirm they pass**

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
mvn test -pl pos-api-gateway \
    -Dtest="SecurityGatewayConfigTest#catalogVersionIsEight+authorityByBitCoversAllNewEntries+permissionCodeBitIndicesMatchGatewayCatalog" \
    -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: All three tests **PASS**.

- [ ] **Step 2.5: Run the full gateway test suite**

```bash
mvn test -pl pos-api-gateway
```

Expected: All tests pass. Fix any failures before continuing.

- [ ] **Step 2.6: Commit**

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
git add pos-api-gateway/src/main/java/com/positivity/gateway/config/GatewayPermissionCatalog.java \
        pos-api-gateway/src/test/java/com/positivity/gateway/config/SecurityGatewayConfigTest.java
git commit -m "fix: sync GatewayPermissionCatalog to PermissionCode version 8

Appends 44 entries to AUTHORITY_BY_BIT covering:
- Bits 241-261: batch-2 permissions that were in PermissionCode version 7
  but beyond the array length — silently dropped at decode
- Bits 262-284: batch-3 dark permissions added in Task 1

Bumps CATALOG_VERSION from 6 to 8. PermissionVersionStartupCheck will now
pass on startup instead of throwing IllegalStateException."
```

---

## Task 3: Secure `POST /v1/auth/token-pair`

**Context:** The endpoint at `POST /v1/auth/token-pair` accepts any valid username plus an optional role list and issues a full token pair. It is explicitly listed as `permitAll()` in `SecurityConfig.authSecurityFilterChain` (line 61) and also annotated `@PreAuthorize("permitAll()")` on the controller method (line 157 of `JwtController`). Any unauthenticated caller who can reach the security service and knows a valid username can obtain a token with any role. The `POST /v1/auth/internal/token` endpoint already handles privileged single-token issuance behind `security:token:issue_internal`. Apply the same constraint here.

**Files:**
- Modify: `pos-security-service/src/main/java/com/positivity/securityservice/internal/config/SecurityConfig.java:61`
- Modify: `pos-security-service/src/main/java/com/positivity/securityservice/internal/controller/JwtController.java:157`
- Test: `pos-security-service/src/test/java/com/positivity/securityservice/ContractBehaviorIT.java`

- [ ] **Step 3.1: Write a failing test — unauthenticated token-pair call returns 401**

Find the existing token-pair test in `ContractBehaviorIT.java` (search for `token-pair` or `TokenPairRequest`). Add this test near it:

```java
@Test
@DisplayName("POST /v1/auth/token-pair requires authentication — unauthenticated call returns 401")
void tokenPairRequiresAuthentication() throws Exception {
    String body = """
            {"subject":"john.doe"}
            """;

    mockMvc.perform(post("/v1/auth/token-pair")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isUnauthorized());
}
```

> If the existing token-pair test calls the endpoint without authentication and expects 200, it will also start failing after the fix. Update it in Step 3.3 after the fix is in place.

- [ ] **Step 3.2: Run the new test — confirm it fails**

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
mvn test -pl pos-security-service \
    -Dtest="ContractBehaviorIT#tokenPairRequiresAuthentication" \
    -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: Test **FAILS** (actual status is 200, not 401).

- [ ] **Step 3.3: Remove `/v1/auth/token-pair` from `permitAll()` in `SecurityConfig`**

Open `pos-security-service/src/main/java/com/positivity/securityservice/internal/config/SecurityConfig.java`.

Find the `authorizeHttpRequests` block that lists `permitAll()` paths (lines 60–67). Remove `/v1/auth/token-pair` from it:

```java
// BEFORE
.authorizeHttpRequests(auth -> auth.requestMatchers(
                "/v1/auth/token-pair",
                "/v1/auth/refresh",
                "/v1/auth/validate",
                V1_AUTH_LOGIN,
                V1_AUTH_SELF_REGISTER)
        .permitAll()
        .anyRequest()
        .authenticated())

// AFTER
.authorizeHttpRequests(auth -> auth.requestMatchers(
                "/v1/auth/refresh",
                "/v1/auth/validate",
                V1_AUTH_LOGIN,
                V1_AUTH_SELF_REGISTER)
        .permitAll()
        .anyRequest()
        .authenticated())
```

- [ ] **Step 3.4: Replace `@PreAuthorize` on `generateTokenPair` in `JwtController`**

Open `pos-security-service/src/main/java/com/positivity/securityservice/internal/controller/JwtController.java`.

On line 157, change:

```java
// BEFORE
@PreAuthorize("permitAll()")
@PostMapping("/token-pair")

// AFTER
@PreAuthorize("hasAuthority('security:token:issue_internal')")
@PostMapping("/token-pair")
```

Also update the `@Operation` description on the preceding annotation to note the security requirement:

```java
@Operation(
        summary = "Issue JWT token pair (access + refresh)",
        description = "Issue both access token (1-hour) and refresh token (7-day) for a given subject. "
                + "Requires 'security:token:issue_internal' authority. "
                + "Prefer POST /v1/auth/login for interactive user authentication.")
```

- [ ] **Step 3.5: Update the existing token-pair test(s) in `ContractBehaviorIT`**

Find any existing test that calls `POST /v1/auth/token-pair` without authentication and expects a `2xx` response. Update it to call the endpoint using an authenticated admin token (follow the pattern used by other authenticated tests in the file — typically by calling `/v1/auth/login` first or using a pre-seeded admin token via `jwtService.generateToken`):

```java
// Example — adapt to the test's actual setup pattern:
String adminToken = jwtService.generateToken(
        "admin.user", adminUserId, Set.of("ADMIN"));

mockMvc.perform(post("/v1/auth/token-pair")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + adminToken)
                .content("""
                        {"subject":"john.doe","roles":["MANAGER"]}
                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty());
```

- [ ] **Step 3.6: Run the token-pair tests**

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
mvn test -pl pos-security-service \
    -Dtest="ContractBehaviorIT#tokenPairRequiresAuthentication" \
    -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: The new `tokenPairRequiresAuthentication` test **PASSES**.

- [ ] **Step 3.7: Run the full security-service test suite**

```bash
mvn test -pl pos-security-service
```

Expected: All tests pass. Fix any failures (there may be other token-pair tests you missed in Step 3.5).

- [ ] **Step 3.8: Commit**

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
git add pos-security-service/src/main/java/com/positivity/securityservice/internal/config/SecurityConfig.java \
        pos-security-service/src/main/java/com/positivity/securityservice/internal/controller/JwtController.java \
        pos-security-service/src/test/java/com/positivity/securityservice/ContractBehaviorIT.java
git commit -m "fix: require security:token:issue_internal on POST /v1/auth/token-pair

Removes the endpoint from the permitAll() HTTP matcher and replaces
@PreAuthorize(\"permitAll()\") with hasAuthority('security:token:issue_internal').
Unauthenticated callers now receive 401 instead of a freely-issued token pair."
```

---

## Task 4: Update `AUTHORIZATION_MODEL.md` Known-Drift Section

**Context:** The document explicitly tracks these four items as open risks in §Known Drift. Tasks 1–3 remediate issues 1–3 from that list (item 4, about older docs describing the `authorities` claim, is separately tracked and already being addressed in the working branch). Remove the now-resolved items and leave item 4 until its own fix is complete.

**Files:**
- Modify: `docs/architecture/AUTHORIZATION_MODEL.md` (in the `durion` repo at `/home/louis-burroughs/IdeaProjects/durion/docs/architecture/AUTHORIZATION_MODEL.md`)

- [ ] **Step 4.1: Update the Known Drift section**

Open the file. Replace the entire §Known Drift block:

```markdown
## Known Drift And Open Risks

The following mismatches are real as of 2026-06-07 and must be considered part of the current contract state:

1. `PermissionCode.CATALOG_VERSION` is `7` while `GatewayPermissionCatalog.CATALOG_VERSION` is `6`.
2. `POST /v1/auth/token-pair` is `permitAll()` and accepts `subject` plus optional `roles`, which does not match the normal login flow.
3. Some older docs still describe access tokens as carrying an `authorities` claim rather than `perm_bits` plus `perm_ver`.
4. Some older docs describe the authorization model as fully data-driven even though token permission emission still depends on `RoleAuthorityServiceImpl`.

Until these are remediated, this document should be treated as the authoritative description of current behavior, not as proof that the model is fully stabilized.
```

With:

```markdown
## Known Drift And Open Risks

The items below were open as of 2026-06-07. Items 1 and 2 are now resolved.
Items 3 and 4 remain open.

### Resolved

1. ~~`PermissionCode.CATALOG_VERSION` was `7` while `GatewayPermissionCatalog.CATALOG_VERSION` was `6`.~~ Fixed: both are now `8`.
2. ~~`POST /v1/auth/token-pair` was `permitAll()`.~~ Fixed: endpoint now requires `security:token:issue_internal`.

### Open

3. Some older docs still describe access tokens as carrying an `authorities` claim rather than `perm_bits` plus `perm_ver`. See `AUTH_TOKEN_USAGE_GUIDE.md` for the current contract.
4. Some older docs describe the authorization model as fully data-driven even though token permission emission still depends on `RoleAuthorityServiceImpl`. A migration from the hardcoded expansion to persisted `role_permissions` data has not yet begun.

Until items 3 and 4 are remediated, treat this document as authoritative on runtime behavior, not as proof of a fully stabilized model.
```

- [ ] **Step 4.2: Commit**

```bash
cd /home/louis-burroughs/IdeaProjects/durion
git add docs/architecture/AUTHORIZATION_MODEL.md
git commit -m "docs: mark resolved known-drift items in AUTHORIZATION_MODEL.md"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] Defect 1 (version mismatch) → Task 2 bumps `GatewayPermissionCatalog.CATALOG_VERSION` to 8 and adds missing bits
- [x] Defect 2 (bits 241–261 silently dropped) → Task 2 appends 21 entries at those positions
- [x] Defect 3 (23 dark permissions) → Task 1 adds them to `PermissionCode` at bits 262–284; Task 2 adds them to the gateway catalog at those same positions
- [x] Defect 4 (`token-pair` is `permitAll()`) → Task 3 removes from HTTP matcher and adds `@PreAuthorize`
- [x] Documentation → Task 4 updates `AUTHORIZATION_MODEL.md`

**Placeholder scan:** No TBDs, no "implement later", no "similar to Task N" shortcuts. All code blocks are complete.

**Type consistency:** `authorityForBit`, `AUTHORITY_BY_BIT`, `CATALOG_VERSION` names are used consistently from their definitions in `GatewayPermissionCatalog`. `PermissionCode::code`, `PermissionCode::bitIndex` match the enum's actual method names.

**Deployment order note:** The security service (Task 1) must deploy before the gateway (Task 2). The gateway's `PermissionVersionStartupCheck` polls the security service's `/v1/permissions/catalog-version` endpoint on startup. After Task 1 deploys, the security service reports version 8. The gateway must then be at version 8 before it starts; deploying Task 2 achieves that. A rolling deploy with the old gateway and the new security service will cause `PermissionVersionStartupCheck` failures — plan for a coordinated deploy window or temporarily disable the startup check if a zero-downtime rolling upgrade is required.
