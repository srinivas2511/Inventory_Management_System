# sales/ CLAUDE.md

Order-to-cash: sales orders, stock reservation, dispatch, and AR invoice generation.

## Responsibilities
- `domain/` — `SalesOrder`, `SalesOrderLine`, `StockReservation`, `Dispatch`, `DispatchLine`.
- `service/` — SO lifecycle (DRAFT → SUBMITTED → APPROVED → ALLOCATED → DISPATCHED); reservation; dispatch posting (via `InventoryPostingService`); AR invoice trigger.
- `event/` — `DispatchCreatedEvent` consumed by `ArService` (generates AR invoice) and `AccountingBridgeService` (COGS journal).
- `web/` — REST controllers: `/api/sales/**`; requires `SALES_VIEW` / `SALES_MANAGE` / `SALES_APPROVE`.

## Key rules
- **Segregation of duties**: a sales order cannot be approved by its creator or submitter (`SegregationOfDuties`, Business Rule 19; `sales_orders.submitted_by` column, V47).
- A fully reserved order automatically becomes `ALLOCATED`.
- Cancelling a sales order releases all its stock reservations.
- Reserve and dispatch dialogs use `/api/lookups/fg-stock` — only `APPROVED`/`PARTIALLY_APPROVED` FG batches are offered.
- Approved-product enforcement: only when `sales.enforce_approved_products = true` (default `false`).

## AR auto-invoicing
- `ArService.computeDispatchValue` = sum of `quantity × unit_price` per dispatch line (D-04 fix).
- Zero-value dispatches get a settled zero invoice without a GL journal.
- A failure raises `MISSING_AR_INVOICE` alert; retry via `POST /api/ar/reconciliation/missing-invoices/retry` (AR_RECONCILE).

## Dispatch documents (GC-4, V55)
`DispatchDocumentRenderer` generates a PDF delivery note and a PDF packing list (PDFBox). File names contain no path separators.

## IT coverage
- `SalesDispatchIT` (6 tests) — SO create, reserve, dispatch, AR invoice creation.
- `ReservationAllocationIT` (7 tests) — reservation, ALLOCATED status, cancellation release.
- `ArFromDispatchIT` (8 tests, in `finance/ar/`) — invoice value, zero-value dispatch, retry.
