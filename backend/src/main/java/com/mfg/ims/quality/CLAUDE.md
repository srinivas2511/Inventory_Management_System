# quality/ CLAUDE.md

Core quality management: incoming, in-process, and final inspections; FG batch management; traceability.

## Responsibilities
- `domain/` — `QualityInspection`, `InspectionResult`, `FgBatch`, `InspectionParameter`.
- `service/` — inspection lifecycle; tolerance evaluation (standard limits, overridden by customer product specs from `crm/`); stock status transitions via `InventoryPostingService`; FG batch approval workflow.
- `web/` — REST controllers: `/api/quality/**`; requires `QUALITY_VIEW` / `QUALITY_MANAGE` / `QUALITY_APPROVE`.

## Inspection types and stock transitions
| Type | Trigger | Pass → stock | Fail → stock |
|------|---------|-------------|-------------|
| Incoming | GR posted | `AVAILABLE` | `REJECTED` (scrap) |
| In-process | Production operation | — (informational) | Hold order |
| Final | Last production operation | `AVAILABLE` (FG) | `REJECTED` (scrap) |

- `POST /api/quality/inspections/{id}/hold` → stock `QUARANTINE → HOLD`
- `POST /api/quality/inspections/{id}/reinspect` → stock `HOLD → QUARANTINE`
- Final inspection leaves FG stock as `AVAILABLE` — dispatch and reserve read `AVAILABLE` FG (via `fg-stock` lookup, APPROVED/PARTIALLY_APPROVED batches only).

## Customer tolerance overrides (GC-5, V55)
During final and in-process inspection for an order made for a specific customer, `customer_product_spec_overrides` replace the standard parameter limits.

## Traceability
Forward trace: material batch → production order → FG batch → dispatch → customer.
Backward trace: FG batch → material batches → supplier → PO → heat numbers.
Granularity controlled by `quality.trace_granularity` system setting (`ORDER` / `BATCH`).

## IT coverage
- `FinalInspectionIT` (7 tests) — pass/fail, stock transitions, FG batch status.
- `InspectionCommitPathIT` (5 tests, non-transactional) — hold, reinspect, real stock status changes.
- `AdvancedQualityIT` (21 tests, in `qualityplus/`) — SPC, CAPA, FAI, customer complaints.
