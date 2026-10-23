# procurement/ CLAUDE.md

Procure-to-pay: purchase orders, goods receipt, and AP invoicing with three-way match.

## Responsibilities
- `domain/` — `PurchaseOrder`, `PurchaseOrderLine`, `GoodsReceipt`, `GoodsReceiptLine`.
- `service/` — PO lifecycle (DRAFT → SUBMITTED → APPROVED → CLOSED); goods receipt posting (via `InventoryPostingService`); AP invoice trigger on receipt.
- `event/` — `GoodsReceiptPostedEvent` consumed by `AccountingBridgeService` (Dr 1401 / Cr 2401).
- `web/` — REST controllers: `/api/procurement/**`; requires `PURCHASE_VIEW` / `PURCHASE_MANAGE` / `PURCHASE_APPROVE` / `GOODS_RECEIVE`.

## Three-way match (V40 — lives in `finance/ap/`)
PO → GR → AP invoice must align: same supplier, GR in POSTED status, invoice value within tolerance. See `finance/CLAUDE.md` for the full match rules.

## Key rules
- **Segregation of duties**: PO submitter cannot approve it (`ProcurementIT`, Business Rule per ARCHITECTURE §10.2a).
- Goods receipt posts stock to the receiving warehouse/location; stock status starts as `AVAILABLE` unless an incoming inspection is triggered (quality module).
- Alt-UOM conversions from `material/AltUom` are applied at receipt time (GC-1).
- Approved-supplier enforcement: only when `purchasing.enforce_approved_suppliers = true` (default `false`).

## IT coverage
- `ProcurementIT` (3 tests) — PO create, approval segregation, GR posting.
- `ThreeWayMatchIT` (14 tests, in `finance/ap/`) — match scenarios, tolerance, override.
