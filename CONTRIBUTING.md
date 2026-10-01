# Contributing

Read `docs/` first: `REQUIREMENTS.md` (what), `ARCHITECTURE.md` (structure), `DESIGN.md` (detail), `PLAN.md` (order and tasks).

## Workflow
1. Pick a task from `docs/PLAN.md` (IDs like `2.3`). Phases are finished in order.
2. Branch from `main`: `feature/P2-2.3-posting-service`.
3. Implement exactly what the task and the referenced DESIGN sections describe.
4. Run the checks below, open a pull request using the template, get a review, squash-merge.

## Commits
Conventional commits with the task id: `feat(P2-2.3): inventory posting service`, `fix(P3-3.3): ...`, `test:`, `docs:`, `chore:`.

## Rules (also in CLAUDE.md)
- Stock changes only through `InventoryPostingService`. The ledger is append-only; correct with reversals.
- Every endpoint has `@PreAuthorize`; the frontend never decides security.
- DTOs, not entities, across the API. Controller -> Service -> Repository.
- **Additive changes only.** Add new Flyway migrations (`V#__description.sql`); never edit an applied one.
- Tests run on real PostgreSQL (Testcontainers), not H2.
- No TODO or placeholder implementations inside a finished phase.

## Checks before a pull request
```bash
cd backend  && mvn verify                 # unit + integration tests, ArchUnit, coverage
cd frontend && npm run lint && npm run format:check && npm run test:ci && npm run build:prod
docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up --build   # smoke test
```

## Decisions
Significant technical decisions get an ADR in `docs/adr/` (copy the latest, increment the number). Business decisions awaiting confirmation live in `docs/decisions.md`.
