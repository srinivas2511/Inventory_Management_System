# material/ CLAUDE.md

Raw material master: material definitions, UOM conversions, purchasing info, and negative-stock overrides.

## Responsibilities
- `domain/` — `Material` entity; `AltUom` (alternative units of measure with conversion factors to base UOM); `MaterialPurchasingInfo`; `NegativeStockOverride`.
- `service/` — material CRUD; alt-UOM conversion (applied on goods receipt and material issue, shown on balances); negative-stock override management (admin-only, audited with reason).
- `web/` — REST controllers: `/api/materials/**`; requires `MASTER_MATERIAL_VIEW` / `MASTER_MATERIAL_MANAGE`.

## Key design points (GC-1, GC-2, GC-3 — V55)
- **Alt UOMs**: `material_alt_uoms` table — each row is an alternative UOM + `factor_to_base`. Balances and ledger are always in base UOM; the factor is applied at receipt/issue time. Exposed via the "UOM Conversions" tab in the material edit dialog.
- **Purchasing info**: `material_purchasing_info` — purchase UOM, tax code, preferred supplier. Shown on the "Purchasing" tab. Not enforced unless `purchasing.enforce_approved_suppliers = true` (default `false`).
- **Negative-stock override**: per-material flag in `inventory_negative_overrides`; requires `INVENTORY_NEGATIVE_OVERRIDE` (ADMIN only); mandatory reason; every negative posting that uses the override is audited. Screen at `/admin/negative-stock-overrides`.
- `active` column on `AttributeDefinitionDto` (V35) controls whether a product attribute definition is visible in the UI.

## IT coverage
- `GcGapRowsIT` (10 tests) — alt-UOM conversion correctness, negative-stock override flow.
