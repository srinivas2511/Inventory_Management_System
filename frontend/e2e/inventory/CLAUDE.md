# e2e/inventory/ CLAUDE.md

E2E tests for inventory operations.

## Spec file (15 tests)
`stock-operations.spec.ts`

| Test area | What it verifies |
|-----------|-----------------|
| Stock balance list | Material and product balances displayed; warehouse filter |
| Opening balance | Dialog opens, entry saved, stock appears in balance list (STK-01) |
| Stock adjustment | Positive and negative adjustment; quantity and reason captured |
| Admin threshold | Adjustment ≥ ₹10,000 refused for non-admin; succeeds for admin |
| Stock ledger | Ledger entries visible after posting |
| Batch list | Raw material batch list with expiry dates |
| Alt-UOM | Balance displays base UOM; alt-UOM label shown for reference |

## Notes
- STK-01 (opening balance) is a UAT checklist item mapped in `uat-map.json`.
- Admin-threshold test logs in as `storemgr1` (refused) then as `admin` (succeeds) for the same adjustment.
- Uses `storemgr1`, `storeop1`, and `admin` as test users.
