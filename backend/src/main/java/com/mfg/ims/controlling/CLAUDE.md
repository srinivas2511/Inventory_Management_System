# controlling/ CLAUDE.md

Management accounting: cost centres, profit centres, journal dimension allocation, and variance reporting.

## Responsibilities
- `domain/` — `CostCentre`, `ProfitCentre` entities; `JournalDimension` (allocation of a journal line to a cost/profit centre).
- `service/` — cost-centre and profit-centre CRUD; dimension allocation logic.
- `report/` — periodic cost reports: actual vs budget, variance by cost centre.
- `web/` — REST controllers: `/api/controlling/**`; requires `CONTROLLING_VIEW` / `CONTROLLING_MANAGE`.

## Key design points
- Every journal line can optionally carry a `cost_centre_id` and `profit_centre_id` (V19 migration).
- Period-end clearing of conversion cost absorbed (account 5303) against real labour/overhead expense is a manual finance reconciliation step — not automated.
- Conversion cost absorption: when a production order completes, `Dr 1501 WIP / Cr 5303 Conversion Cost Absorbed` is posted once, after commit, by `AccountingBridgeService`. A failure raises `MISSING_JOURNAL` alert.
- Budget-vs-actual: budget lines imported via CSV (`R9-7.7`); actual pulled from the GL ledger.

## IT coverage
- `ControllingIT` (13 tests) — cost-centre CRUD, dimension allocation, variance report correctness.
- `ConversionAbsorptionIT` (7 tests) — absorption journal, retry on failure, variance calculation.
