# e2e/ui/ CLAUDE.md

E2E tests for UI-level regressions: dialog typing and broad screen-load sweep.

## Spec files (4 tests total)
| File | Tests | What it covers |
|------|-------|---------------|
| `dialog-typing.spec.ts` | 1 | Regression guard for D-41: typing into a Material dialog field lands in the correct field (not the dialog's initial-focus field). Fails on the old bare `fill()` approach; passes only with `typeInto()` from `support/ui.ts` |
| `ui-sweep.spec.ts` | 3 | Every main nav route loads without a console error or uncaught exception; no 5xx responses during navigation |

## Notes
- `dialog-typing.spec.ts` is the regression guard that keeps the D-41 fix in place. If `typeInto()` is ever replaced with bare `fill()`, this test must fail.
- `ui-sweep.spec.ts` runs as `admin` (who has access to all routes) and simply navigates to each route — it does not interact with the page. Its purpose is to catch broken lazy-loaded routes and missing API calls at startup.
