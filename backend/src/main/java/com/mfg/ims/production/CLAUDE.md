# production/ CLAUDE.md

Production execution: production orders, operations, material issue/return, operator assignment, and WIP costing.

## Responsibilities
- `domain/` — `ProductionOrder`, `ProductionOperation`, `ProductionMaterialIssue`, `ProductionMaterialReturn`, `ProductionOutput`.
- `service/` — order lifecycle (PLANNED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED); backflush; material issue/return; scrap write-off; conversion cost absorption; WIP costing.
- `event/` — `ProductionOrderCompletedEvent`, `ProductionOutputRecordedEvent` (consumed by `AccountingBridgeService`).
- `web/` — REST controllers: `/api/production-orders/**`, `/api/production/operations/**`; `GET /api/my-work` (operator tablet view).

## Order lifecycle & costing
| Step | Journal |
|------|---------|
| Material issue | `Dr 1501 WIP / Cr 1401 RM Stock` |
| Order complete (conversion absorption) | `Dr 1501 WIP / Cr 5303 Conversion Cost Absorbed` (once, post-commit, non-blocking) |
| FG receipt | `Dr 1410 FG Stock / Cr 1501 WIP` at released standard cost (or actual material + absorbed conversion) |
| Variance | `Dr/Cr 5xxx Variance / Cr/Dr 1501` |
| Scrap write-off (order close) | `Dr 5202 Scrap Expense / Cr 1501` (charged once; variance journal excludes already-relieved scrap) |
| Material return | `Dr 1401 RM Stock / Cr 1501 WIP` |

## Key rules
- **Backflush**: when `production.auto_backflush = true`, material is consumed automatically after the first operation's output is recorded.
- **Operation sequence**: an operation cannot start until the previous routing sequence is `COMPLETED` (`ProductionFlowIT` PRD-FLOW-6).
- **Conversion absorption failure**: raises `MISSING_JOURNAL` alert; retried when variance is calculated.
- `ProductionOperationDto` includes `finalOperation`, `productionOrderId`, `productionOrderNumber`, `productId`, `assignedOperatorName`.
- Operator assignment: `PUT /api/production/operations/{id}/assignment`; requires `PRODUCTION_ASSIGN`.
- Role gap: `STORE_MANAGER` has `INVENTORY_ISSUE` but not `PRODUCTION_VIEW`; `PRODUCTION_MANAGER` has the reverse — only ADMIN (or a user with both) can issue material from the production screen.

## IT coverage
- `ProductionFlowIT` (6 tests) — full order flow: release, operation sequence, output recording, completion.
- `OrderCloseIT` (7 tests) — material reconciliation, scrap, close with write-off.
- `FinishedGoodsCostingIT` (4 tests) — FG receipt at standard/actual cost, COGS journal.
- `ConversionAbsorptionIT` (7 tests) — absorption journal, retry, variance.
- `OperatorAssignmentIT` (6 tests) — assignment endpoints, permission.
