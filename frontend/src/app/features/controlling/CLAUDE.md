# features/controlling/ CLAUDE.md

Management accounting screens: cost centres, product costing, production variances, and budget-vs-actual.

## Screens
| Component | Purpose |
|-----------|---------|
| `CostCentreListComponent` | Cost centre and profit centre list and edit |
| `ProductCostingListComponent` | Product standard cost list |
| `ProductCostingDetailComponent` | BOM cost roll-up detail for one product |
| `VarianceListComponent` | Production variance list (standard vs actual) |
| `ControllingReportsComponent` | Period-end controlling reports |

## API
`controlling.api.ts` → `/api/controlling/**`; requires `CONTROLLING_VIEW` / `CONTROLLING_MANAGE`.

## Notes
- Budget-vs-actual report is in `ControllingReportsComponent`; budget lines are imported via CSV upload.
- Product costing detail shows the BOM explosion with cost per component — read-only.
- Variance list is also accessible from the production order detail screen as a tab.
- Hidden for plant-scoped users (company-level function).
