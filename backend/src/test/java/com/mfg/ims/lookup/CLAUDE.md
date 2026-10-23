# test/lookup/ CLAUDE.md

Lookup endpoint IT.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `LookupIT` | 7 | Each key `/api/lookups/**` endpoint: correct items returned, correct permission required (transactional permission only — not master-data permission), `fg-stock` returns only `APPROVED`/`PARTIALLY_APPROVED` batches (never `HOLD` or `QUALITY_PENDING`), `goods-receipts/invoiceable` returns only POSTED GRs for the correct supplier, `material-stock` returns batches in earliest-expiry-first order |

## Notes
- The `fg-stock` test is the guard for D-38: a `HOLD` FG batch must not appear in the reservation picker. It seeds a HOLD batch alongside an APPROVED batch and asserts only the APPROVED one is returned.
- Each test logs in as the minimum-privilege user for that endpoint (e.g. `INVENTORY_ISSUE` for `material-stock`) to verify the transactional-permission-only rule.
