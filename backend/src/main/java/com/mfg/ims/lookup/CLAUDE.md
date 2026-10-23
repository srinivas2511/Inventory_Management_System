# lookup/ CLAUDE.md

Lightweight read-only endpoints that feed dropdown lists and pickers in the Angular frontend.

## Responsibilities
- `LookupController` — all endpoints live under `/api/lookups/**`.
- `LookupService` — thin queries against master data and transactional tables; returns minimal DTOs (id + label only).
- `LookupDtos` — shared DTO records used across all lookup responses.

## Key endpoints (representative)
| Endpoint | Used by |
|----------|---------|
| `/api/lookups/materials` | BOM items, goods receipt, material issue pickers |
| `/api/lookups/products` | Sales order, FG batch pickers |
| `/api/lookups/suppliers` | PO and AP invoice create dialogs |
| `/api/lookups/fg-stock` | Reserve and dispatch dialogs — **APPROVED / PARTIALLY_APPROVED batches only** |
| `/api/lookups/goods-receipts/invoiceable` | AP invoice create dialog |
| `/api/lookups/sales-orders/approved` | Wave picking |
| `/api/lookups/material-stock` | Material issue dialog (earliest-expiry-first order) |

## Rules
- **Every lookup endpoint requires only the transactional permission** (e.g. `INVENTORY_ISSUE`), not the master-data read permission. This allows operators to use create dialogs without needing master-data access.
- Responses must be small — only id and label fields. Never return full entity DTOs from here.
- `fg-stock` must filter to `APPROVED` / `PARTIALLY_APPROVED` batches only — never `HOLD` or `QUALITY_PENDING` (D-38 fix).
- `LookupIT` (7 tests) — covers each key endpoint for correctness and permission requirements.
