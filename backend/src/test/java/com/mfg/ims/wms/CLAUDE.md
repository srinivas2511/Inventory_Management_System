# test/wms/ CLAUDE.md

Warehouse Management System tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `WmsIT` | IT | 18 | Bin creation within a zone; transfer order lifecycle (DRAFT → CONFIRMED → IN_PROGRESS → COMPLETED); picking wave creation from approved sales orders; cycle count: count entry triggers variance if actual ≠ system qty, variance above threshold requires ADMIN override; bin-to-bin transfer moves stock through `InventoryPostingService`; a transfer to a bin in another plant's warehouse is rejected for plant-scoped users |
| `WmsSecurityMatrixTest` | ArchUnit | — | Every endpoint under `wms/` has `@PreAuthorize` with one of: `WMS_VIEW`, `WMS_MANAGE`, `WAREHOUSE_MANAGER`; no endpoint is left unannotated |
| `WmsRulesTest` | Unit | — | Cycle count variance threshold rule: variance ≤ threshold → auto-approved; > threshold → requires ADMIN override; bin capacity enforcement: over-capacity transfer is refused; zone type enforcement: finished goods cannot be placed in a raw-material zone |

## Notes
- WMS is **not** plant-scoped at the business-object level — warehouses belong to plants, but WMS transfer orders and waves are company-level operations. Plant-scoped users are restricted at the warehouse level (can only pick from their plant's warehouses), not at the WMS module level.
- `WmsIT` bin-to-bin transfer uses `InventoryPostingService` (non-negotiable rule #1); the test verifies the `inventory_transactions` row is written with correct location references.
- Cycle count threshold is configured via system setting `wms.cycle_count.variance_threshold` (default ₹500).
