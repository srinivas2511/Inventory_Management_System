# features/master/ CLAUDE.md

Core master data: materials, products, customers, and suppliers — each in its own sub-folder.

## Sub-folders
| Folder | Screens |
|--------|---------|
| `material/` | Material list + edit dialog; tabs in edit mode: General, UOM Conversions (alt UOMs + factors), Purchasing (purchase UOM, tax code, preferred supplier) |
| `product/` | Product list + edit dialog; `DynamicSpecFormComponent` renders attribute fields by product type |
| `customer/` | Customer list + edit dialog; tabs: General, Approved Products (chip-list), Product Spec Overrides (customer tolerance overrides) |
| `supplier/` | Supplier list + edit dialog; tabs: General, Approved Materials (chip-list) |

## API files
`material/material.api.ts`, `product/product.api.ts`, `customer/customer.api.ts`, `supplier/supplier.api.ts`.

## Tabs convention (GC-1, GC-2, GC-5)
- Create mode: single flat form, no tabs.
- Edit mode: tabbed layout. Each new capability (UOM conversions, purchasing info, approved materials/products, spec overrides) is an additional tab — never mixed into the General tab.
- Chip-list tabs (Approved Materials / Approved Products): add from a lookup select; remove via chip × button; PUT replaces the full set on each change.
- UOM Conversions: inline add/remove, immediate PUT/DELETE per row (not a bulk save).

## Permissions
`MASTER_MATERIAL_VIEW` / `_MANAGE`, `MASTER_PRODUCT_VIEW` / `_MANAGE`, `MASTER_CUSTOMER_VIEW` / `_MANAGE`, `MASTER_SUPPLIER_VIEW` / `_MANAGE`.
