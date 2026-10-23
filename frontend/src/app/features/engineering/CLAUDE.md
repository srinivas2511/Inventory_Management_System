# features/engineering/ CLAUDE.md

Product engineering: Bill of Materials (BOM) list/form and production routing editor.

## Screens
| Component | Purpose |
|-----------|---------|
| `BomListComponent` | BOM list per product |
| `BomFormComponent` | BOM create/edit with line items; includes "UOM Conversions" tab (alt UOMs per material) |
| `RoutingEditorComponent` | Routing step list for a product; add/remove/reorder operations and machine assignments |

## Notes
- BOM form opens as a dialog from the product screen or the BOM list.
- `BomFormComponent` has a "UOM Conversions" tab that shows and edits alternative UOMs with conversion factors (GC-1). Each alt-UOM row is saved immediately on add/remove (PUT/DELETE per `altUom`).
- Routing steps use sequence numbers (10, 20, 30…); the editor enforces that steps are numbered in multiples of 10.
- Create mode shows a single flat form; the tabs (including UOM Conversions) appear only in edit mode.
- Requires `MASTER_BOM_VIEW` / `MASTER_BOM_MANAGE`; routing requires `MASTER_ROUTING_VIEW` / `MASTER_ROUTING_MANAGE`.
