# [FRONTEND] [STORY] Security: Login & Token Handling (CAP-275, Issue #280)

## Purpose

Define the Angular frontend implementation scope for CAP-275: Login experience,
JWT token storage and lifecycle management, session resume validation, and an admin
configuration panel for enabling/disabling JWT assertion issuance (the `enable` operation)
as required by ADR-0011.

The login page component already exists (`src/app/features/auth/login.component.ts`).
This wireframe documents the complete interaction surface, unimplemented gaps, and the
new security admin panel that exposes the `enable` assertion toggle.

---

## Screens

### Screen 1 — Login Page (`/login`)

**Status:** Exists. Needs token validation wiring review.

**Layout:**

```
┌────────────────────────────────────────────────────────┐
│                                                        │
│              [  D  ]  Durion POS                      │
│                   Positivity Platform                  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  ⚠ Error banner (conditional)                   │  │
│  │  "Invalid username or password..."               │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  Username ─────────────────────────────────────────   │
│  [                                          ]         │
│  ↳ field-error: "Username required (min 2 chars)"    │
│                                                        │
│  Password ─────────────────────────────────────────   │
│  [                                          ]         │
│  ↳ field-error: "Password required (min 4 chars)"    │
│                                                        │
│  [ ──────────── Sign In ──────────── ]  (CTA btn)    │
│  [ ⠋ Signing in…                    ]  (loading)     │
│                                                        │
│  ───────────────────────────────────────────────────  │
│  [🌙 / ☀️]  (theme toggle — tertiary button)          │
└────────────────────────────────────────────────────────┘
```

**Token Flow (post-login):**

```
Browser                     Auth Service         Backend (/auth/login)
──────                      ────────────         ─────────────────────
Submit credentials   ──────►  login()      ──────►  POST /auth/login
                             ◄──────────────────── { accessToken, refreshToken, tokenType }
                             storeTokens()
                             scheduleExpiry()
                             ◄── navigate to /app (or returnUrl)
```

**Inputs/Outputs:**

| Element | Type | Validation | Notes |
| --- | --- | --- | --- |
| `username` | text input | required, minLength 2 | `autocomplete="username"` |
| `password` | password input | required, minLength 4 | `autocomplete="current-password"` |
| Sign In button | submit | form valid + not loading | gradient-filled primary CTA |
| Error banner | alert | conditional on `error()` signal | `role="alert"`, `aria-live="polite"` |

**Error Mapping:**

| HTTP Status | User Message |
| --- | --- |
| 401 / 403 | "Invalid username or password. Please try again." |
| 0 (network) | "Cannot reach the server. Check your network or try again later." |
| Other | "Login failed (status). Please try again." |

**Design tokens:**
- Card: `--brand-surface` on `--brand-background`; `mic-elevation-3` shadow
- Brand logo: `D` glyph in `--brand-primary` circle, `display-sm` headline
- CTA: gradient `--brand-primary` → `--brand-primary-soft` 135°, sm border-radius
- Inputs: `--input-background` fill, 2px bottom stroke `--input-border`; focus: `--input-focus-border`
- Error stroke: `--functional-error-red` bottom stroke

---

### Screen 2 — Session Expired / 401 Redirect Flow

**Status:** Not implemented. HTTP interceptor handles 401 but does not redirect consistently.

**Trigger:** Any authenticated request returns HTTP 401 (token expired or revoked).

**Behavior:**

```
HTTP Interceptor receives 401
        │
        ▼
AuthService.logout()   ── clear tokens from localStorage
        │
        ▼
Router.navigate(['/login'], { queryParams: { returnUrl: currentPath } })
```

**Login page on returnUrl param:**

```
┌────────────────────────────────────────────────────────┐
│              [  D  ]  Durion POS                      │
│                                                        │
│  ⚠ "Your session has expired. Please sign in again." │
│  (info-level banner, not error-red)                   │
│                                                        │
│  Username: [                              ]            │
│  Password: [                              ]            │
│  [ ────────────── Sign In ──────────────── ]          │
└────────────────────────────────────────────────────────┘
```

**Banner variant:** `alert-info` (uses `--functional-info-blue`), distinct from login failure `alert-error`.

---

### Screen 3 — Token Validation on Session Resume

**Status:** Not implemented. AuthService currently decodes JWT locally without calling `validateToken` endpoint.

**Trigger:** App bootstrap — `AppComponent.ngOnInit()` or `AuthGuard` activation when token is present in localStorage.

**Behavior:**

```
App init
  │
  ▼
AuthService.validateSessionOnResume()
  │
  ├─ if no token → skip (user is unauthenticated, guard will redirect)
  │
  ├─ if token present → GET /v1/auth/validate
  │         ├─ 200 OK → continue (token is backend-confirmed valid)
  │         └─ 401/403 → logout() → navigate /login
  │
  └─ if network error → allow local-JWT parse to proceed (graceful degradation)
```

**operationId:** `validateToken` (`GET /v1/auth/validate`)

**Note:** Validation call is non-blocking for UX — the app can show a loading skeleton while the call resolves. On failure, redirect replaces the skeleton.

---

### Screen 4 — Security Admin: JWT Assertion Configuration (`/app/security/assertions`)

**Status:** New screen. Not implemented.

**Purpose:** Allow a privileged admin to enable or disable JWT assertion issuance for the platform, and to view current assertion configuration (issuer, audience, token TTL). This is the Angular surface for the `enable` operationId.

**Required permission:** `security:assertions:manage` (or equivalent admin role).

**Layout:**

```
┌─────────────────────────────────────────────────────────────────────┐
│  Security Admin                                                      │
│  ─────────────────────────────────────────────────────────────────  │
│  Tabs: [ Roles ] [ Permissions ] [ Audit Log ] [ ▶ Assertions ]      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  JWT Assertion Issuance                                              │
│                                                                      │
│  Status:  ● ENABLED   [ Disable ]    ← when enabled                 │
│           ○ DISABLED  [ Enable  ]    ← when disabled                │
│                                                                      │
│  ─────────────────────────────────────────────────────────────────  │
│  Current Configuration              (read-only)                     │
│                                                                      │
│  Issuer          durion-positivity                                   │
│  Audience        pos-api-gateway                                     │
│  Token TTL       900 seconds (15 min)                               │
│  Algorithm       HS256                                               │
│                                                                      │
│  ─────────────────────────────────────────────────────────────────  │
│  ⚠  Confirmation modal on disable:                                  │
│     "Disabling assertion issuance will prevent all backend         │
│      service calls from being authenticated. Only continue if       │
│      you intend to update credentials."                              │
│     [ Cancel ]  [ Confirm Disable ]                                │
└─────────────────────────────────────────────────────────────────────┘
```

**States:**

| State | Indicator | CTA |
| --- | --- | --- |
| Enabled | Green dot `●` + "ENABLED" label | "Disable" button (ghost/secondary) |
| Disabled | Grey ring `○` + "DISABLED" label | "Enable" button (primary gradient) |
| Loading | Spinner | Buttons disabled |
| Error | Error banner with correlationId | Retry button |
| Forbidden (403) | Not-authorized state panel | None |

**API call — operationId `enable`:**

```
POST /v1/security/assertions/enable    ← enable assertion issuance
POST /v1/security/assertions/disable   ← disable assertion issuance
GET  /v1/security/assertions/config    ← read current config (issuer, aud, ttl)
```

**Note:** If the backend exposes a single `POST /enable` toggle with a body `{ enabled: boolean }`,
the Angular service should map both the enable and disable actions to that single call.
Inspect `pos-security-service/openapi.yaml` to confirm the exact path and payload before implementation.

**Design tokens:**
- Status indicator: `--functional-success` for enabled dot; `--durion-graphite-400` for disabled ring
- Config table: `surface-container-lowest` background; `body-md` Inter font; no dividers (spacing-4 separation)
- "Confirm Disable" button: `--functional-error-red` gradient (destructive CTA variant)
- Confirmation modal: `surface-container-highest` panel; `mic-elevation-3` float shadow

---

## Component Inventory

| Component | File | Status | Notes |
| --- | --- | --- | --- |
| `LoginComponent` | `features/auth/login.component.ts` | Exists | Missing session-expired banner variant |
| `LoginComponent` template | `features/auth/login.component.html` | Exists | Has error banner; needs info banner slot |
| `HttpAuthInterceptor` | `core/interceptors/auth.interceptor.ts` | Needs review | 401 redirect behavior to `/login?returnUrl` |
| `AuthService.validateSessionOnResume()` | `core/services/auth.service.ts` | Not implemented | Add `validateToken` call on bootstrap |
| `SecurityAssertionsComponent` | `features/security/pages/assertions/` | Not implemented | New page |
| `security.routes.ts` | `features/security/security.routes.ts` | Stub | Add `assertions` child route |

---

## Interaction Flow — Full Auth Lifecycle

```
1. User navigates to app root (/)
   │
   ├─ No token → redirect /login
   └─ Token present → AuthService.validateSessionOnResume()
         ├─ VALID → proceed to /app/dashboard
         └─ INVALID → redirect /login?sessionExpired=true

2. Login Page
   │
   ├─ sessionExpired=true in params → show info banner "Session expired"
   ├─ User submits credentials
   │     ├─ 200 → storeTokens → navigate returnUrl (or /app)
   │     ├─ 401/403 → error banner
   │     └─ Network error → error banner
   └─ Theme toggle → ThemeService.toggle()

3. Authenticated session
   │
   ├─ HTTP interceptor adds Authorization: Bearer <token> to every request
   ├─ Any 401 from API → AuthService.logout() → /login?returnUrl=current
   └─ Token near expiry (30s skew) → isAuthenticated() = false → guard redirects

4. Security Admin: JWT Assertions (/app/security/assertions)
   │
   ├─ Load current config (GET /v1/security/assertions/config)
   ├─ Toggle enable/disable → confirmation modal (if disabling)
   ├─ POST enable/disable → success toast
   └─ 403 → not-authorized state panel
```

---

## Accessibility Requirements

- Login form: all inputs have `id` + `<label for="">`, `aria-invalid`, `aria-describedby` for errors
- Error banners: `role="alert"` and `aria-live="polite"`
- Loading button: `aria-busy="true"` while spinner is visible, `disabled` attribute prevents double-submit
- Assertion toggle button: `aria-pressed="true|false"` for toggle semantics
- Confirmation modal: `role="dialog"`, `aria-labelledby`, focus trapped within modal while open

---

## Design Constraints

- No border lines for containment — use tonal surface shifts (`surface-container-low` vs `surface`)
- Login card uses `mic-elevation-3` (ambient shadow 32px, 4% opacity) — no 1px stroked border
- Assertion status row: tonal separation only; `--functional-success` dot is the only colored element
- Destructive confirm modal "Confirm Disable" uses error-red gradient CTA — the only place red is allowed on this screen

---

## Blockers / Open Questions

| Item | Owner | Resolution |
| --- | --- | --- |
| `enable` operation_id exact endpoint path | Inspect `pos-security-service/openapi.yaml` | Unresolved |
| `GET /v1/security/assertions/config` — does it exist? | Contract status is `draft` — pre-verify | Unresolved |
| `validateToken` endpoint availability | `BACKEND_CONTRACT_GUIDE.md` lists it — confirm live in dev | Unresolved |
| Session expired banner — add `sessionExpired` query param handling to `LoginComponent` | Frontend | Not implemented |
