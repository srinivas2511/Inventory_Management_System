# crm/ CLAUDE.md

Customer Relationship Management: customers, contacts, activities, and approved-products enforcement.

## Responsibilities
- `domain/` — `Customer`, `Contact`, `Activity`, `CustomerProductSpec` (tolerance override per customer per product).
- `service/` — CRUD; approved-products enforcement (when `sales.enforce_approved_products = true`); customer product-spec override application during final and in-process inspection.
- `report/` — customer activity summary, pipeline.
- `web/` — REST controllers: `/api/crm/**`; requires `CRM_VIEW` / `CRM_MANAGE`.

## Key design points
- **Approved products**: if `sales.enforce_approved_products = true`, only products on a customer's approved list (`customer_approved_products`, V55) can be ordered. Default: `false`.
- **Customer tolerance overrides** (GC-5, V55): a customer can have per-product-parameter tolerance overrides stored in `customer_product_spec_overrides`. During final and in-process inspection for a sales order made for that customer, the override replaces the standard limits.
- Customers and suppliers share the `partner/` base entity; CRM extends partner with CRM-specific fields.
- `Customer.gstNumber` may be blank — stored as `null`, not empty string (V45 fix).

## IT coverage
- `CrmIT` (23 tests) — CRUD, approved-products enforcement, tolerance override application.
