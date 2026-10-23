# masterdata/ CLAUDE.md

Production master data: machines and operations (the building blocks of routings).

## Sub-packages
| Package | Responsibility |
|---------|---------------|
| `machine/` | `Machine` entity — name, type, cost centre, capacity; `GET/POST/PUT /api/masterdata/machines`; requires `MASTER_MACHINE_VIEW` / `MASTER_MACHINE_MANAGE`; documents linked via `MachineDocumentLinkTarget` |
| `operation/` | `Operation` entity — name, standard time, skill requirement; `GET/POST/PUT /api/masterdata/operations`; requires `MASTER_OPERATION_VIEW` / `MASTER_OPERATION_MANAGE` |

## Notes
- Machines and operations are referenced by routing steps (`engineering/routing/`).
- Deleting a machine or operation that is referenced by an active routing is refused (FK constraint + service check).
- `machines.cost_centre_id` (V33) — links a machine to its controlling cost centre for maintenance cost roll-up.
