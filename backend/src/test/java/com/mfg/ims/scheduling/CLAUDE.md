# test/scheduling/ CLAUDE.md

Scheduling engine unit test. (Integration scheduling tests are in `advmfg/SchedulingIT`.)

## Test classes
| Class | Type | What it covers |
|-------|------|----------------|
| `ScheduleEngineTest` | Unit | Forward scheduling: assigns start/end times in routing-sequence order; backward scheduling: works backward from a due date; calendar integration: non-working days are skipped; capacity conflict: two orders competing for the same machine on the same day; Gantt DTO structure: each operation has a `windowStart`, `windowEnd`, machine reference |

## Notes
- `ScheduleEngineTest` is a pure unit test — no Spring context, no Testcontainers. The engine's calendar dependency is injected as a mock.
- Integration-level scheduling (persistence, DB queries, full Spring context) is in `advmfg/SchedulingIT` (12 tests).
