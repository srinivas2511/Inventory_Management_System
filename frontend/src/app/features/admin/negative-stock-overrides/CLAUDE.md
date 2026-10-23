# admin/negative-stock-overrides/ CLAUDE.md

Per-material/product negative-stock override administration screen (GC-3).

## Spec files
| File | What it covers |
|------|---------------|
| `negative-stock-overrides.component.spec.ts` | `NegativeStockOverridesComponent`: two tabs (Materials / Products); slide-toggle per row reflects the current override state; toggling opens `NegativeStockReasonDialogComponent` to collect a mandatory reason; the reason plus the new value are sent via `PUT /api/master/materials/{id}/negative-stock-override` or `/products/{id}`; cancelling the reason dialog leaves the toggle unchanged; `INVENTORY_NEGATIVE_OVERRIDE` permission required |

## Notes
- GC-3: negative-stock override requires a mandatory reason and is audited server-side; every negative posting against a material is also audited regardless of the override flag.
- The slide-toggle visual state must not flip until the dialog is confirmed — the spec asserts the toggle reverts on cancel.
