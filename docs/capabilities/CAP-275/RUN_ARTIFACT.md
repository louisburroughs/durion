# CAP-275 Run Artifact — Auth Session Foundation

## Delivery Summary

- **Wave**: E
- **Branch**: cap/security-wave-e
- **Status**: Complete

## Implemented

### CAP-275: Session Wiring (Angular Frontend)

- `AuthService.logoutWithRedirect(returnUrl: string)` — clears JWT, redirects to `/login` with `returnUrl` and `sessionExpired=true` query params
- `AuthService.validateSessionOnResume(): Observable<boolean>` — calls `GET /v1/auth/validate`; on error calls logout
- `authInterceptor` — on refresh failure, uses Angular `Location.path()` as returnUrl and calls `logoutWithRedirect`
- `LoginComponent` — detects `?sessionExpired=true` query param, shows info banner using `.alert.alert-info`
- Return URL validation: `returnUrl` must start with `/` to prevent open redirect

## Tests

- `auth.service.spec.ts`: 6 tests covering logoutWithRedirect and validateSessionOnResume
- `auth.interceptor.spec.ts`: 5 tests (token attachment, 401→refresh→retry, refresh failure→redirect)
- `login.component.spec.ts`: 8 tests (session expired, banner visibility, form validation, redirect)
