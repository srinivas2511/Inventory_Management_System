# e2e/quality/ CLAUDE.md

E2E tests for quality management screens.

## Spec files (11 tests total)
| File | Tests | What it covers |
|------|-------|---------------|
| `incoming-quality.spec.ts` | 6 | Incoming inspection creation, parameter entry, pass → stock AVAILABLE, fail → REJECTED scrap; hold and reinspect flow (QCF-05) |
| `fg-final-decision.spec.ts` | 5 | FG batch final decision: approve → AVAILABLE; reject → scrap; hold batch not offered in reservation (QCF-05); hold released and then reservable |

## Notes
- QCF-05 (HOLD batch not offered in dispatch reservation and refused on direct API call) is a UAT checklist item mapped in `uat-map.json`.
- `incoming-quality.spec.ts` verifies the hold/reinspect stock transition: QUARANTINE → HOLD → QUARANTINE → AVAILABLE.
- `fg-final-decision.spec.ts` uses a Material dialog for all decisions — no `window.confirm()` or `window.prompt()` (D-39 fix).
- Uses `quality1`, `storemgr1`, and `admin` as test users.
