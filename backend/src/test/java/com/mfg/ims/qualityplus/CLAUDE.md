# test/qualityplus/ CLAUDE.md

Advanced quality tests: SPC, CAPA, FAI, and customer complaints.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `AdvancedQualityIT` | IT | 21 | SPC chart CRUD; SPC point recording; control-limit violation → `SPC` alert; Western Electric rule violations; CAPA lifecycle (OPEN → IN_PROGRESS → CLOSED); SoD on CAPA close (closer ≠ opener); FAI inspection type and result entry; customer complaint lifecycle (OPEN → INVESTIGATION → RESOLVED → CLOSED); document linking on CAPA and complaint |
| `QualityPlusSecurityMatrixTest` | ArchUnit | — | `qualityplus/` endpoints have `SPC_MANAGE`, `CAPA_MANAGE`, `CAPA_CLOSE`, `COMPLAINT_MANAGE` |
| `QualityPlusStateMachineTest` | Unit | — | CAPA and complaint state machines: valid transitions; invalid transitions throw; `CAPA_CLOSE` required permission enforced in the state machine guard |
| `SpcMathTest` | Unit | — | SPC control limit calculation (mean ± 3σ); Western Electric rules: one point > 3σ, two of three > 2σ, four of five > 1σ, eight consecutive same side; exact boundary cases |

## Notes
- `AdvancedQualityIT` SoD test is non-transactional: opens a CAPA as one user, attempts close as the same user, expects 403.
- `SpcMathTest` uses parameterised test cases with pre-calculated expected values to guard against floating-point drift in control limit computation.
