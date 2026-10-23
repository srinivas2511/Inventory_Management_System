# e2e/finance/ CLAUDE.md

E2E tests for financial accounting screens.

## Spec files (14 tests total)
| File | Tests | What it covers |
|------|-------|---------------|
| `finance-screens.spec.ts` | 8 | Journal entry, submit/approve/post cycle, journal reversal, AP invoice with match result, AR receipt, period close with reason, AR reconciliation retry |
| `finance-r97.spec.ts` | 6 | R9-7 features: GST tax admin, exchange rates, credit notes, budget import (CSV), settlement history, cost summary of a closed order (FIN-02) |

## Notes
- `finance-screens.spec.ts` verifies that the Approve action on a journal is disabled for the preparer (SoD — FIN-JNL-2).
- AP invoice test verifies the three-way match result display (match status + matched GR reference).
- Period close test verifies the mandatory reason dialog (cannot finalize without a reason).
- `finance-r97.spec.ts` FIN-02 verifies that the cost summary tab on a closed production order shows actual vs standard variance.
- Uses `finance1` and `admin` as test users.
