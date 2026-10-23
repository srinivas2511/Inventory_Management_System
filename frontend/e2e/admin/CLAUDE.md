# e2e/admin/ CLAUDE.md

E2E tests for platform administration screens.

## Spec files (14 tests total)
| File | Tests | What it covers |
|------|-------|---------------|
| `admin.spec.ts` | 6 | System settings edit, attribute definitions toggle, module activation, negative-stock override with reason |
| `user-admin.spec.ts` | 5 | Create user, assign roles, assign plants, reset password, deactivate user |
| `plants.spec.ts` | 3 | Create plant, assign user to plant, plant-scoped user sees filtered data |

## Notes
- `user-admin.spec.ts` verifies that the last ADMIN cannot be deactivated (last-admin guard).
- `plants.spec.ts` (plant-scoping E2E) verifies that a plant-assigned user's SO and production order lists are filtered.
- Negative-stock override test checks that the reason dialog is required — submitting without a reason is refused by the server.
