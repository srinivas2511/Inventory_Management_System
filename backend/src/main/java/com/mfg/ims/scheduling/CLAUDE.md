# scheduling/ CLAUDE.md

Production scheduling: forward and backward finite-capacity scheduling of production operations.

## Responsibilities
- `engine/` — scheduling algorithm: assigns start/end times to operations based on machine capacity, routing sequence, and the production calendar.
- `SchedulingService` — runs the schedule for a set of production orders; persists scheduled windows to `production_operations` (`schedule_window_start`, `schedule_window_end`, V33).
- `SchedulingController` — `POST /api/scheduling/run`; `GET /api/scheduling/gantt`; requires `PRODUCTION_VIEW`.
- `SchedulingRepository` — reads production operations and machine capacity.
- `SchedulingDtos` — Gantt chart DTO: order → operations → time windows.

## Notes
- Scheduling uses the `prodcalendar/ProductionCalendar` to skip non-working days.
- Scheduling is currently **not plant-scoped** (deferred — see `docs/decisions.md`).
- The Gantt chart data is read live (not a materialized view), so it reflects the latest schedule immediately after a run.

## IT coverage
- `SchedulingIT` (12 tests) — forward scheduling, backward scheduling, capacity conflict detection, Gantt output correctness.
