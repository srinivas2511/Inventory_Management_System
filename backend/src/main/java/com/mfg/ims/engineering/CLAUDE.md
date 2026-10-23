# engineering/ CLAUDE.md

Product engineering: Bills of Materials (BOM) and production routings.

## Sub-packages
| Package | Responsibility |
|---------|---------------|
| `bom/` | `Bom`, `BomItem` entities; BOM CRUD; BOM explosion (up to 10 levels, guarded); costing roll-up |
| `routing/` | `ProductRouting`, routing steps; sequence numbers (10, 20, 30…); links operations to machines |

## BOM key points
- `bom.base_quantity` column — configurable per BOM; default from system setting `bom.default_base_quantity` (default 1).
- BOM explosion max depth: **10 levels** (MRP and production order release both enforce this).
- A released production order snapshots the BOM at release time; subsequent BOM changes do not affect in-flight orders.
- BOM documents linked via `BomDocumentLinkTarget`.
- `BomService` enforces no circular references before saving.

## Routing key points
- Routing steps use sequence numbers (multiples of 10). An operation cannot start until the previous sequence is `COMPLETED` (enforced by `ProductionOperationService`).
- Each step references a `masterdata/operation/` operation and optionally a `masterdata/machine/` machine.
- The first operation's input quantity is set at production order release (`V36` backfill fixed a zero-quantity bug).

## IT coverage
- `MrpIT` exercises BOM explosion indirectly (14 tests).
- `ProductionFlowIT` exercises routing sequence enforcement (6 tests).
