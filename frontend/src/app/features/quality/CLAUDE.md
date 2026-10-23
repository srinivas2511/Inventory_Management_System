# features/quality/ CLAUDE.md

Quality management: inspections, FG batches, SPC charts, CAPA, First Article Inspections, customer complaints, and rejection dashboard.

## Screens
| Component | Purpose |
|-----------|---------|
| `InspectionListComponent` | All inspections with type/status filters |
| `InspectionDetailComponent` | Inspection result entry: parameters, tolerance preview, deviation approval, rejection reasons |
| `FgBatchListComponent` | Finished goods batch list with approval status |
| `FgFinalDecisionDialogComponent` | FG batch approval/rejection dialog (Material dialog — no native `prompt()`) |
| `FgHoldDecisionDialogComponent` | Place/release FG hold (Material dialog) |
| `SpcChartListComponent` | SPC chart list |
| `SpcChartDetailComponent` | SPC chart with control limits; `SpcChartSvgComponent` renders the SVG |
| `CapaListComponent` | CAPA list |
| `CapaDetailComponent` | CAPA detail: root cause, actions, close (requires `CAPA_CLOSE`) |
| `FaiListComponent` | First Article Inspection list |
| `FaiDetailComponent` | FAI detail and result entry |
| `ComplaintListComponent` | Customer complaint list |
| `ComplaintDetailComponent` | Complaint detail and resolution |
| `RejectionDashboardComponent` | Rejection rate trends by material/product |

## API files
`inspection.api.ts` — inspections and FG batches.
`quality-plus.api.ts` — SPC, CAPA, FAI, complaints.

## Permissions
`QUALITY_VIEW` / `QUALITY_MANAGE` / `QUALITY_APPROVE` for inspections; `SPC_MANAGE`, `CAPA_MANAGE`, `CAPA_CLOSE`, `COMPLAINT_MANAGE` for advanced quality.

## Key rules
- FG final decision and hold approval are **Material dialogs** — never `window.confirm()` or `window.prompt()`.
- `SpcChartSvgComponent` and `spc-chart.util.ts` contain pure rendering logic; unit-tested without Angular `TestBed`.
- `InspectionDetailComponent` shows customer tolerance overrides when the inspection is linked to a sales order with a customer product spec override.
