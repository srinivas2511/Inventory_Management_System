# test/partner/ CLAUDE.md

Partner (supplier/customer) IT.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `PartnerGstIT` | — | GST number handling: blank GST stored as `null` (not empty string); duplicate GST number rejected with 409; valid GST format accepted; customer and supplier forms both enforce the same rules |

## Notes
- `PartnerGstIT` is the guard for the D-27 fix (blank GST causing a 500 on save).
- Approved-materials and approved-products list management is tested in `gc/GcGapRowsIT`.
