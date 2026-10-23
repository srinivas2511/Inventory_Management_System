# prodcalendar/ CLAUDE.md

Production calendar: defines working days, shifts, and public holidays used by scheduling and MRP lead-time calculations.

## Responsibilities
- `ProductionCalendar` — entity (or service) that answers "is this date a working day?" and "how many working days between two dates?".
- Used by `scheduling/` for forward/backward scheduling and by `mrp/` for lead-time offsetting.

## Notes
- This package is intentionally thin. All scheduling logic lives in `scheduling/`.
- Calendar rules are company/plant-level settings; shift definitions are in `hrm/`.
