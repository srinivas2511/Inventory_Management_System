# test/engineering/ CLAUDE.md

BOM and routing tests.

## Test classes
| Class | Type | What it covers |
|-------|------|----------------|
| `BomServiceTest` | Unit | BOM explosion up to 10 levels; circular reference detection; `BomExplosionDepthException` thrown at depth > 10; `base_quantity` applied correctly per BOM level; costing roll-up arithmetic |

## Notes
- BOM explosion correctness at depth and with circular references is a pure unit test — no Testcontainers needed.
- Integration-level BOM explosion is covered by `MrpIT` (14 tests) and `ProductionFlowIT` (6 tests), which exercise BOM explosion as part of full business flows.
- Routing step sequence enforcement (operation cannot start before previous sequence is COMPLETED) is covered by `ProductionFlowIT`.
