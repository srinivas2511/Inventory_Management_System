# test/oee/ CLAUDE.md

OEE calculation unit test. (Integration OEE tests are in `advmfg/OeeIT`.)

## Test classes
| Class | Type | What it covers |
|-------|------|----------------|
| `OeeCalculatorTest` | Unit | OEE = Availability × Performance × Quality: formula correctness; zero run-time edge case (returns 0, not divide-by-zero); 100% quality edge case; values clamped to [0, 1]; intermediate component values available separately |

## Notes
- `OeeCalculatorTest` is a pure unit test — no Spring context, no Testcontainers.
- The integration side (writing raw events, refreshing `mv_oee_daily`, asserting the materialized view values) is in `advmfg/OeeIT`.
