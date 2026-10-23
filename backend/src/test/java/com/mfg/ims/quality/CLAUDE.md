# test/quality/ CLAUDE.md

Core quality ITs.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `FinalInspectionIT` | 7 | Final inspection PASS → FG stock AVAILABLE; FAIL → REJECTED (scrap in-leg); FG batch status APPROVED/REJECTED; COGS journal deferred to dispatch (not on inspection) |
| `InspectionCommitPathIT` | 5 | Non-transactional; real stock status changes across transaction boundaries: hold (`QUARANTINE → HOLD`), reinspect (`HOLD → QUARANTINE`), pass (`QUARANTINE → AVAILABLE`); verifies `InventoryPostingService` is called in a separate transaction from the inspection decision |
| `FgRejectionAndTraceIT` | — | FG batch rejected after final inspection; forward trace from material batch reaches the rejected FG batch; backward trace from FG batch names the material batches, supplier, PO, and heat numbers |

## Notes
- `InspectionCommitPathIT` is **non-transactional** — it is the guard for the fix where accepting an incoming inspection was silently ignoring quantities and never moving stock (D-14 / D-06 era fixes).
- `FgRejectionAndTraceIT` exercises the `quality.trace_granularity = BATCH` path; the `ORDER` path is covered by `FgRejectionAndTraceIT` with a different system-setting value.
- Advanced quality (SPC, CAPA, FAI, complaints) is in `qualityplus/AdvancedQualityIT`.
