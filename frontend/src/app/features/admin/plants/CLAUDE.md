# admin/plants/ CLAUDE.md

Plant master and user-plant access assignment (R9-7.1).

## Spec files
| File | What it covers |
|------|---------------|
| `plants.component.spec.ts` | `PlantsComponent`: lists plants via `GET /api/enterprise/plants`; create/edit dialog with name, code, timezone; `SETTINGS_MANAGE` permission required for create/edit; plant list shows user count per plant; `PlantFieldComponent` reuses this data for user-plant assignment dialogs |

## Notes
- Plants are the top-level scoping boundary for inventory, procurement, production, sales, dispatch, warehouses, and stock.
- Company-level functions (finance, controlling, analytics, etc.) are unrestricted by plant; a plant-scoped user gets 403 `PLANT_SCOPE_COMPANY_LEVEL` when they reach those routes.
- `PlantsComponent` is `SETTINGS_MANAGE` gated — not accessible to plant-scoped users.
