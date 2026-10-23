# features/procurement/ CLAUDE.md

Procure-to-pay screens: purchase requisitions, purchase orders, and goods receipts.

## Screens
| Component | Purpose |
|-----------|---------|
| `PurchaseRequisitionListComponent` | PR list |
| `PurchaseRequisitionFormComponent` | PR create/edit dialog |
| `PurchaseOrderListComponent` | PO list; Approve action (disabled for submitter — SoD) |
| `PurchaseOrderFormComponent` | PO create/edit dialog fed by lookup selects |
| `GoodsReceiptListComponent` | GR list |
| `GoodsReceiptFormComponent` | GR create dialog: select PO, enter received quantities per line |

## API
`/api/procurement/**` via `core/http/` interceptors; no separate `procurement.api.ts` (calls are inline in components or via shared `lookup.api.ts`).

## Key rules
- **Segregation of duties**: Approve button on the PO list is disabled for the user who submitted the order (tooltip shown; server enforces with 403).
- GR create dialog uses `/api/lookups/purchase-orders/approved` to offer only approvable POs.
- `procurement-forms.spec.ts` tests all create dialog form validations.
