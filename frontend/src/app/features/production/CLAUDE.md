# features/production/ CLAUDE.md

Production execution: production orders, My Work (operator tablet), material issue/return, operator assignment, scheduling Gantt, and OEE dashboard.

## Screens
| Component | Purpose |
|-----------|---------|
| `ProductionOrderListComponent` | Order list; create, issue material, return material, close order |
| `ProductionOrderFormComponent` | Production order create dialog |
| `IssueMaterialDialogComponent` | Issue material per order: warehouse/location picker, per-material qty pre-filled (planned − issued), batch picker (earliest-expiry-first) |
| `ReturnMaterialDialogComponent` | Return leftover material after completion |
| `CloseOrderDialogComponent` | Close with write-off reason when material is left over |
| `AssignOperatorDialogComponent` | Assign operator to one or all open operations |
| `MyWorkComponent` | Operator tablet screen: shows assigned operations; Start/Pause/Resume/Record Output/Complete; `LiveAnnouncer` for accessibility |
| `ScheduleGanttComponent` | Gantt chart of scheduled operations; `gantt-geometry.ts` handles coordinate math |
| `OeeDashboardComponent` | OEE metrics and trend charts per machine |

## API files
`production-execution.api.ts` — production order and operation actions.
`scheduling.api.ts` — Gantt data.
`oee.api.ts` — OEE metrics.

## Key rules
- `IssueMaterialDialogComponent`: over-issue is warned but the server decides; batch picker uses earliest-expiry-first order.
- `MyWorkComponent`: no `alert()`/`confirm()` — all confirmations use inline Material expansion or a dialog. `LiveAnnouncer` announces every state change for screen-reader accessibility (NFR-007 / WCAG 2.1 AA).
- **Segregation of duties**: not applicable to production operations (no approval step — system postings only).
- `gantt-geometry.spec.ts` and `chart-math.spec.ts` are pure unit tests for layout math — no Angular dependencies.
- `production-forms.spec.ts` tests create-dialog form validations.
- `production-execution.spec.ts` tests the API service.
