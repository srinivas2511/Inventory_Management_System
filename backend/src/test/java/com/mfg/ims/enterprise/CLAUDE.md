# test/enterprise/ CLAUDE.md

Multi-plant scoping IT.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `PlantScopeIT` | 9 | A user with plant assignments sees only those plants' purchase orders, production orders, sales orders, warehouses, and stock; a user with no assignments is unrestricted; `PLANT_ALL_ACCESS` lifts the restriction per role; `InventoryPostingService` refuses a warehouse outside the user's plants; company-level endpoints return `403 PLANT_SCOPE_COMPANY_LEVEL` for a plant-scoped user |

## Notes
- `PlantScopeIT` is non-transactional — each test commits data in one plant, then queries as a scoped user to verify isolation.
- The test covers the "not yet plant-scoped" list (quality inspections, WMS, etc.) by asserting they still return results for plant-scoped users (no accidental over-restriction).
