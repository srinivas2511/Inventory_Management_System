# test/sales/ CLAUDE.md

Sales and dispatch ITs and unit tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `SalesDispatchIT` | IT | 6 | SO create and submit; reserve AVAILABLE FG stock; dispatch reduces `qtyOnHand`; AR invoice auto-generated; dispatch posts `Dr 5001 COGS / Cr 1410 FG Stock` |
| `ReservationAllocationIT` | IT | 7 | Fully reserving an order sets status `ALLOCATED`; cancelling releases all reservations; partial reservation leaves order `APPROVED`; a HOLD batch is refused in reservation (D-38 guard) |
| `SalesOrderAuditIT` | IT | — | SO audit trail: every state transition recorded with actor, old status, new status, and timestamp; `createdBy` and `submittedBy` set correctly (V47 columns) |
| `DispatchDocumentRendererTest` | Unit | — | PDFBox-generated delivery note and packing list: valid PDF bytes returned; file name contains no path separators (GC-4 fix); line items rendered correctly |

## Notes
- `ReservationAllocationIT` is the guard for D-08: a fully reserved order must automatically become `ALLOCATED`, and cancelling must release reservations.
- `DispatchDocumentRendererTest` is a unit test — it calls `DispatchDocumentRenderer` directly without a Spring context and asserts on the PDF byte content.
- SoD on sales order approval (approver ≠ creator/submitter) is tested in `security/SegregationOfDutiesIT`, not here.
