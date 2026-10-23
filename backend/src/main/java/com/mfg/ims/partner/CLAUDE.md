# partner/ CLAUDE.md

Shared base for suppliers and customers (the "partner" concept).

## Responsibilities
- `domain/` — `Supplier` and `Customer` entities (or a shared `BusinessPartner` base if refactored). Includes `approved_materials` (supplier side, V55) and `approved_products` (customer side, V55).
- `service/` — partner CRUD; approved-materials and approved-products list management (add/remove, PUT replaces set).
- `web/` — REST controllers: `/api/suppliers/**`, `/api/customers/**`; requires `MASTER_SUPPLIER_*` / `MASTER_CUSTOMER_*`.
- `SupplierDocumentLinkTarget` — marks `Supplier` as a document-linkable entity.

## Approved-materials / approved-products (GC-2, V55)
- `supplier_approved_materials` — materials a supplier is approved to supply. Enforced only when `purchasing.enforce_approved_suppliers = true` (default `false`).
- `customer_approved_products` — products a customer is approved to order. Enforced only when `sales.enforce_approved_products = true` (default `false`).
- `gstNumber` may be blank — stored as `null`, not empty string (D-27 fix).

## Notes
- CRM (`crm/`) extends `Customer` with CRM-specific fields (contacts, activities).
- Purchasing info (`material/MaterialPurchasingInfo`) references `Supplier` for the preferred-supplier field.
