# e2e/auth/ CLAUDE.md

E2E tests for authentication flows.

## Spec file (9 tests)
`auth.spec.ts`

| Test area | What it verifies |
|-----------|-----------------|
| Login | Valid credentials → dashboard; wrong password → error message |
| Session restore | Page reload restores the session (access token re-fetched via HttpOnly refresh cookie) |
| Back button | Browser Back after logout does not restore a session |
| Concurrent tabs | Opening a second tab does not revoke the session (reuse grace window) |
| Forced password change | Logging in with a temporary password redirects to change-password before any other screen |
| Change password | Current + new password change succeeds; old sessions are invalidated |
| Account lockout | Repeated wrong passwords lock the account; admin unlock releases it |

## Notes
- Session restore test is the E2E guard for the `APP_INITIALIZER` + HttpOnly cookie flow (D-20 fix).
- Concurrent-tabs test validates the `ims.security.jwt.refresh-reuse-grace-seconds` window (D-20 fix).
- Uses `admin` and `operator1` as test users.
