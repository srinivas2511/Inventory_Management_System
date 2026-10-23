# test/auth/ CLAUDE.md

Authentication and session management ITs.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `AuthSessionIT` | 9 | Login; JWT access + refresh token flow; token rotation; reuse grace window (concurrent requests within `ims.security.jwt.refresh-reuse-grace-seconds` are not treated as token theft); logout invalidates the session; `PASSWORD_CHANGE_REQUIRED` 403 blocks all `/api/**` except `/api/auth/**` |
| `ForcedPasswordChangeIT` | 5 | First-login with a temporary password redirects to change-password; every non-auth endpoint returns 403 until the password is changed; changing the password ends the forced-change flag; all other sessions are invalidated |
| `StepUpIT` | — | Step-up authentication challenge/response for sensitive operations (e.g. period reopen) |

## Notes
- All three IT classes are **non-transactional** — they test real commit behaviour (account lockout, token reuse detection, session invalidation all depend on transactions committing).
- `AuthSessionIT` guards the fix for the login/refresh transaction bug: account lockout and refresh-token reuse detection had never worked before because the service method was rolling back (see `docs/decisions.md` "Auth hardening").
- The reuse grace window test sends two refresh requests within 10 seconds from different contexts and verifies neither is rejected as theft.
