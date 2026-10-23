# test/mrp/ CLAUDE.md

MRP engine and forecast tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `MrpIT` | IT | 14 | Multi-level BOM explosion (up to 10 levels); demand netting against on-hand stock and confirmed supply; lot sizing: EXACT, MIN_LOT, FIXED; lead-time offsetting using the production calendar; pegging: planned order → demand source; safety stock and reorder point |
| `MrpEngineTest` | Unit | — | Pure MRP calculation: netting, lot sizing, explosion without DB; parameterised test cases |
| `MrpSecurityMatrixTest` | ArchUnit | — | MRP endpoints have `MRP_VIEW` / `MRP_RUN` |
| `ForecastIT` | IT | — | `mrp/forecast/` — demand forecast CRUD; MRP run uses forecast as a demand source when no confirmed sales orders exist |
| `AiForecastAdapterTest` | Unit | — | `mrp/forecast/` — AI forecast adapter: response parsing, confidence thresholds, fallback to historical average when AI is unavailable |

## Notes
- `MrpIT` uses `TestClock` to fix `now` so lead-time calendar offsets are deterministic.
- `MrpEngineTest` is the guard that BOM depth > 10 throws `BomExplosionDepthException` (same rule tested at service level in `engineering/BomServiceTest`).
- `AiForecastAdapterTest` mocks the AI HTTP client — it does not call an external service.
