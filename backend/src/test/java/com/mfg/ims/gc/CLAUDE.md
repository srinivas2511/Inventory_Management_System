# test/gc/ CLAUDE.md

GC-1…GC-6 gap-rows integration tests (V55 features).

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `GcGapRowsIT` | 10 | GC-1: alt-UOM conversion factor applied on goods receipt and material issue; balance displayed in base UOM. GC-2: purchasing info on a material; approved-materials enforcement (`purchasing.enforce_approved_suppliers = true`); approved-products enforcement (`sales.enforce_approved_products = true`). GC-3: negative-stock override toggle (admin-only, reason required); negative posting succeeds with override, fails without. GC-5: customer tolerance override replaces standard limits during final inspection for a specific customer's order. |

## Notes
- GC-4 (dispatch PDF documents) is tested in `sales/DispatchDocumentRendererTest` and the e2e `dispatch-documents.spec.ts`.
- GC-6 (CSP header on every response) is tested in `system/SecurityHeadersTest`.
- `GcGapRowsIT` is non-transactional for the alt-UOM and negative-stock tests (they verify post-commit balances).
