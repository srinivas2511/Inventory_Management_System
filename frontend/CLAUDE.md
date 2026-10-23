# frontend/CLAUDE.md

Angular 18+ / Angular Material frontend for the ERP platform.
All components are **standalone**. No NgModules.

## Commands
```bash
npm ci                      # install
npm run lint                # ESLint
npm run format:check        # Prettier
npm run test:ci             # Karma/Jasmine headless (all unit tests)
npm run build:prod          # production build (en-IN locale, must stay < 1 MB initial bundle)
npm run build:hi            # Hindi locale build
npm run build:kn            # Kannada locale build
npm run build:localize      # all three locales
npm start                   # dev server :4200, proxies /api → :8080
```

## Layout
```
src/app/
  core/         App-wide singletons: auth, HTTP, layout, config, models, notification
  shared/       Reusable UI: components, directives, pipes (IstDatePipe, etc.)
  features/     One folder per domain module (lazy-loaded routes)
src/locale/
  messages.xlf       Source strings (en-IN)
  messages.hi.xlf    Hindi translations (118 trans-units)
  messages.kn.xlf    Kannada translations (118 trans-units)
e2e/            Playwright end-to-end tests (npm run e2e)
```

## Conventions
- **Standalone components** everywhere — no `NgModule` declarations.
- **Reactive forms** (`FormBuilder`, `FormGroup`) for all data-entry dialogs.
- **Angular Material** for all UI chrome (tables, dialogs, form fields, snackbars).
- **`IstDatePipe`** (not `| date:`) for every date display — applies IST +05:30.
- **`$localize\`@@id:text\`` tagged templates** for nav labels; `i18n="@@id"` attributes on static HTML strings.
- HTTP calls go through the `core/http/` interceptors (auth token, 401 refresh, error toast).
- Permission checks in templates/components are UX-only guards; the server is authoritative.
- Do **not** use `alert()`, `confirm()`, or `prompt()` — use Angular Material dialogs.
- Icon buttons must have `aria-label`; decorative icons inside labelled buttons get `aria-hidden="true"`.

## Key shared utilities
| Symbol | Location | Purpose |
|--------|----------|---------|
| `IstDatePipe` | `shared/pipes/ist-date.pipe.ts` | Date formatting in IST |
| `AppConfigService` | `core/config/` | Runtime config (`config.json`) |
| `AuthService` | `core/auth/` | Login, refresh, session restore |
| `PagedList<T>` | `shared/paged-list.ts` | Shared pagination helper |
| `LocaleSwitcherComponent` | `shared/components/locale-switcher/` | EN / हिं / ಕನ toggle |

## Bundle budget
- Initial bundle warning: **1 MB**, error: **1.5 MB** (raised for en-IN locale data).
- Hindi/Kannada builds: warning **1.5 MB**, error **2 MB**.

## Test conventions
- Every component has a `*.spec.ts` in the same folder.
- Specs use `TestBed` with the real Angular Material imports (no stubs for Material components).
- `ApiContractIT` on the backend validates frontend HTTP call URLs — keep `environment` base paths consistent.
- `FrontendPermissionCodesIT` — permission codes used in `*ngIf` / `hasPermission()` must match the seeded set.

## E2E (Playwright)
```bash
cd frontend && npx playwright test          # full suite (requires Docker stack running)
npx playwright test e2e/uat/               # UAT specs only
```
- 332 tests across fresh-database runs; two consecutive passes = release gate.
- Helpers: `e2e/support/flow.ts` (typed flows), `e2e/support/ui.ts` (`typeInto()` for dialogs).
- `UatTraceabilityTest` (backend) fails the build if a `docs/UAT_CHECKLIST.md` ID has no e2e mapping.
