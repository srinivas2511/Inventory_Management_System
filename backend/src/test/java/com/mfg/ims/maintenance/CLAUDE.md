# test/maintenance/ CLAUDE.md

Maintenance cost and spare-part unit tests. (Integration tests live in `advmfg/`.)

## Test classes
| Class | Type | What it covers |
|-------|------|----------------|
| `MaintenanceCostCalculatorTest` | Unit | Cost calculation: labour hours × rate + parts cost; rounding to NUMERIC(18,4); zero-cost edge case |
| `MaintenanceCostPosterTest` | Unit | `MaintenanceCostPoster` event handler: correct GL accounts selected by cost type (labour vs parts vs overhead); `MAINTENANCE_COST_UNPOSTED` alert raised on posting failure |
| `MaintenanceServiceTest` | Unit | Work order lifecycle state machine: OPEN → IN_PROGRESS → COMPLETED; invalid transitions throw; spare part availability check before issue |

## Notes
- Integration-level tests (OEE, scheduling, spare-part issue through `InventoryPostingService`) are in `advmfg/SparePartAndMaintenanceCostIT`.
- `MaintenanceCostPosterTest` uses a mock `InventoryPostingService` to verify the event handler dispatches correctly without running a full Spring context.
