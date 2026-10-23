# test/procurement/ CLAUDE.md

Procurement ITs.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `ProcurementIT` | 3 | PO create and submit; SoD: PO submitter cannot approve (expects 403); GR posting moves stock to AVAILABLE and posts `Dr 1401 / Cr 2401` |
| `RequisitionListIT` | — | Purchase requisition list returns results (guard for D-32: a null status caused `status IS NULL` and always returned empty) |

## Notes
- Three-way match (AP invoice against GR) is tested in `finance/ThreeWayMatchIT` (14 tests), not here.
- `RequisitionListIT` is the regression guard for D-32: the requisition query must not use a null-equality predicate that silently filters all rows.
