# features/mrp/ CLAUDE.md

Material Requirements Planning: run history, proposals, demand forecasts, and planning parameters.

## Screens
| Component | Purpose |
|-----------|---------|
| `MrpRunListComponent` | MRP run history; trigger a new run |
| `MrpProposalListComponent` | Planned order proposals from the latest run; convert to PO/production order |
| `DemandForecastListComponent` | Manual demand forecast entries |
| `PlanningParamsComponent` | Per-material lot sizing, lead time, safety stock overrides |

## API
`mrp.api.ts` → `/api/mrp/**`; requires `MRP_VIEW` / `MRP_RUN`.

## Notes
- MRP is a batch run — triggered manually from `MrpRunListComponent` or on a schedule.
- Proposals are read-only after a run; the user converts them to actual documents (PO or production order) via an action button.
- Hidden for plant-scoped users (company-level function).
