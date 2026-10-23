# e2e/ CLAUDE.md

Playwright end-to-end test suite. 332 tests across all spec files.
**Release gate: two consecutive fully-green runs on a fresh database.**

## Running
```bash
# Full stack must be running first (see docker/CLAUDE.md)
cd frontend
npx playwright test                        # full suite
npx playwright test e2e/security/          # one folder
npx playwright test e2e/demo-workflow.spec.ts  # one file
npx playwright test --headed              # watch mode
npx playwright show-report                # open HTML report
```

## Environment variables (see support/env.ts)
| Variable | Default | Purpose |
|----------|---------|---------|
| `E2E_BASE_URL` | `http://localhost:4200` | Angular dev server or nginx |
| `E2E_API_URL` | `http://localhost:8090` | Backend API (scratch stack uses 8090) |
| `E2E_ADMIN_USER` | `admin` | Bootstrap admin username |
| `E2E_ADMIN_PASSWORD` | *(see env.ts)* | Bootstrap admin password |
| `E2E_ROLE_PASSWORD` | *(see env.ts)* | Shared password for all role users |

## Folder map
| Folder / File | What it covers |
|---------------|---------------|
| `support/` | Shared helpers: API client, typed flows, UI helpers, user list, fixtures |
| `global-setup.ts` | Checks stack health; creates DESIGN §11.2 role users (idempotent) |
| `demo-workflow.spec.ts` | Full happy-path workflow (29 tests) — the primary integration smoke test |
| `known-defects.spec.ts` | Defect regression specs (D-08, etc.) that verify accepted fixes stay fixed (9 tests) |
| `admin/` | User admin, role management, plant management (14 tests) |
| `auth/` | Login, session, password change, SoD on auth (9 tests) |
| `finance/` | Finance screens and R9-7 features (14 tests) |
| `inventory/` | Stock operations, adjustments, opening balance (15 tests) |
| `manufacturing/` | Production execution, My Work, Gantt (7 tests) |
| `negative/` | Business rule violations and 4xx error paths (9 tests) |
| `quality/` | Incoming inspection, FG final decision and hold (11 tests) |
| `reports/` | Alerts and report screens (7 tests) |
| `sales/` | Dispatch documents (PDF delivery note and packing list) (1 test) |
| `security/` | RBAC, role sweep, security headers, segregation of duties (22 tests) |
| `uat/` | UAT checklist gap coverage — IDs from docs/UAT_CHECKLIST.md (9 tests) |
| `ui/` | Dialog-typing regression and UI sweep (4 tests) |

## UAT traceability
`uat-map.json` — maps `docs/UAT_CHECKLIST.md` IDs to spec files or test names.
`UatTraceabilityTest` (backend IT) fails the build if any checklist ID has no mapping.

## Key rules
- Use `typeInto()` from `support/ui.ts` for every typed input inside a Material dialog (never bare `locator.fill()`). This waits for Angular Material's `afterNextRender` focus capture before typing (D-41 fix).
- Use `flow.ts` helpers for typed multi-step operations (e.g. `dispatchOnePiece()`) — they encapsulate lookup calls and avoid duplication.
- Specs must be **idempotent on a fresh database** — `global-setup.ts` creates role users; each spec creates its own data and does not rely on leftover state from another spec.
- Do not use `page.waitForTimeout()` — use `expect(...).toBeVisible()`, `waitForResponse()`, or the `typeInto()` / `waitForDialogFocus()` helpers.
