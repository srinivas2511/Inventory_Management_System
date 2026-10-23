# test/contract/ CLAUDE.md

API contract guards — keep the frontend and backend in sync at build time.

## Test classes
| Class | What it guards |
|-------|----------------|
| `ApiContractIT` | Scans `FrontendSources` for every HTTP call the Angular app makes and verifies each one resolves to a real backend handler. Fails if a frontend URL has no matching `@RequestMapping` (404 at runtime). Also checks that a request body is never tested against an arbitrary endpoint when the frontend URL has a variable segment that matches multiple handlers. |
| `FrontendSources` | Helper that reads the Angular `frontend/src/` source files and extracts all HTTP call URLs. Used by `ApiContractIT`. |
| `FrontendSourcesTest` | Unit test for `FrontendSources` parsing correctness. |
| `UatTraceabilityTest` | Reads `docs/UAT_CHECKLIST.md` and `frontend/e2e/uat-map.json`. Fails if any checklist ID has no entry in `uat-map.json` and no e2e spec that names it. Ensures every UAT acceptance criterion has a Playwright test. |

## `test/resources/contract/`
Contains static snapshot files used by `ApiContractIT` to detect URL regressions between builds.

## Maintenance rule
Whenever you:
- Add a new Angular HTTP call → the matching backend endpoint must exist (run `ApiContractIT`).
- Add a new `docs/UAT_CHECKLIST.md` ID → add an entry to `frontend/e2e/uat-map.json` or a new Playwright test that names the ID.
- Change a frontend URL → update both the Angular source and verify `ApiContractIT` still passes.
