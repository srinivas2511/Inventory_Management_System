# test/crm/ CLAUDE.md

CRM tests: customer relationship management, pricing, and quotations.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `CrmIT` | IT | 23 | Lead/opportunity/contact/activity CRUD; approved-products enforcement (`sales.enforce_approved_products = true`); customer tolerance override application during inspection; pipeline report values; customer segments |
| `CrmSecurityMatrixTest` | ArchUnit | — | Every CRM endpoint has `CRM_VIEW` / `CRM_MANAGE` |
| `CrmStateMachineTest` | Unit | — | Lead and opportunity stage transitions; valid and invalid transition guards |
| `PriceSelectorTest` | Unit | — | `crm/service/` — price list selection logic: customer-specific price > product-type price > standard price; effective-date filtering |
| `QuotationPricingTest` | Unit | — | `crm/service/` — quotation line pricing: applies selected price, discounts, tax codes; total calculation |

## Notes
- `CrmIT` tolerance-override test seeds a `customer_product_spec_override` and runs a final inspection for an order made for that customer; asserts the override limits replace the standard limits.
- `CrmIT` approved-products test sets `sales.enforce_approved_products = true` via `SystemSettingService`, creates an order for a non-approved product, and expects a 422.
