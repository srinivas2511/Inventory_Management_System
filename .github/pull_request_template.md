## Task
<!-- PLAN.md task ID, e.g. P2-2.3 -->

## What changed
<!-- Short list of what was added/modified. One bullet per logical change. -->
- 

## Why
<!-- The business or technical reason (link to REQUIREMENTS/DESIGN section if useful). -->

## Definition of Done checklist
- [ ] Backend changes: controller → service → repository, all with DTOs/MapStruct
- [ ] Flyway migration added (new `V#__*.sql`), never edited an applied one
- [ ] `@PreAuthorize` on every new endpoint; role test added
- [ ] Stock changes go only through `InventoryPostingService`
- [ ] Business rules that changed have a test (REQUIREMENTS §6)
- [ ] Sensitive actions audited in the same transaction
- [ ] `mvn verify` passes (unit + ArchUnit + ITs if Docker available)
- [ ] `npm run lint && npm run format:check && npm run test:ci && npm run build:prod` passes
- [ ] DESIGN.md / ARCHITECTURE.md updated if behaviour changed
- [ ] Seed/demo data still loads cleanly on an empty DB (dev profile)

## Deviations from DESIGN.md
<!-- None, or describe and justify. -->
