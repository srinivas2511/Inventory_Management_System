# test/production/ CLAUDE.md

Production execution ITs and unit tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `ProductionFlowIT` | IT | 6 | Full order flow: release (sets first-operation input qty, V36 fix), operation sequence enforcement (cannot start until previous is COMPLETED), output recording, pause/resume/complete, after-commit final inspection trigger |
| `ProductionIT` | IT | 5 | Production order CRUD, BOM snapshot at release, priority field |
| `OrderCloseIT` | IT | 7 | Material reconciliation: consumed/returned/written-off tracking; material return (`Dr 1401 / Cr 1501`); scrap write-off (`Dr 5202 / Cr 1501`); close with write-off reason; variance journal excludes already-relieved scrap |
| `ConversionAbsorptionIT` | IT | 7 | Absorption journal (`Dr 1501 / Cr 5303`) posted once, post-commit; failure captured and `MISSING_JOURNAL` alert raised; retry on next variance run; variance calculation |
| `OperatorAssignmentIT` | IT | 6 | Assign operator to one operation; assign to all open operations; assignment reflected on My Work query; `PRODUCTION_ASSIGN` required |
| `DowntimeCategoryTest` | Unit | — | Downtime category CHECK constraint values match the domain enum (no drift between Java enum and DB check) |
| `FinishedGoodsCostServiceTest` | Unit | — | `production/service/` — FG cost = actual material consumed + absorbed conversion; standard cost fallback; zero-material-cost edge case |
| `OrderMaterialServiceTest` | Unit | — | `production/service/` — consumed/returned/written-off quantity tracking per issue line; backflush quantity calculation |

## Notes
- `ProductionFlowIT` and `OrderCloseIT` are **non-transactional** — they test commit-boundary behaviour (balances after multiple separate transactions).
- `ConversionAbsorptionIT` is non-transactional — the absorption is fired post-commit as an `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- `FinishedGoodsCostingIT` (in `finance/`) complements `FinishedGoodsCostServiceTest` with the GL journal side.
