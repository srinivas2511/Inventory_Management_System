# enterprise/ CLAUDE.md

Multi-company and multi-plant scoping — the top of the organisational hierarchy.

## Responsibilities
- `domain/` — `Company`, `Plant` entities; `UserPlantAccess` join table (V54).
- `service/` — company and plant CRUD; plant-access assignment for users.
- `scope/PlantScope` — decides whether the current user is plant-scoped (has explicit plant assignments) or unrestricted.
- `web/` — REST controllers: `/api/enterprise/**`; requires `SETTINGS_MANAGE`.

## Plant scoping rules
- A user with rows in `user_plant_access` sees and creates only data belonging to those plants (purchase orders, goods receipts, production orders, sales orders, dispatches, warehouses, stock).
- A user with **no** plant assignments (and every existing user before V54) is unrestricted.
- `PLANT_ALL_ACCESS` permission lifts the restriction per role.
- Company-level functions (dashboards, reports, analytics, traceability, integration, finance, controlling, MRP, audit log, payroll) return `403 PLANT_SCOPE_COMPANY_LEVEL` for a plant-scoped user.
- **Not yet plant-scoped**: quality inspections, FG batches, WMS, maintenance, CRM, HR, PLM, documents, scheduling (see `docs/decisions.md`).

## Plant settings
Four plant-level settings override company → system defaults. Modules do not yet read these keys (deferred). Resolution order: plant → company → system default.

## IT coverage
- `PlantScopeIT` (9 tests) — plant assignment, scoped vs unscoped access, `PLANT_ALL_ACCESS`.
