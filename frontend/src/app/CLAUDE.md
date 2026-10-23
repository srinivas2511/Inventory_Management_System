# src/app/ CLAUDE.md

Angular application root. Bootstraps the app and wires together the `core/`, `shared/`, and `features/` layers.

## Spec files in this folder
| File | What it covers |
|------|---------------|
| `app.component.spec.ts` | Root `AppComponent`: renders without error; `RouterOutlet` is present; no layout chrome at the root level (shell lives in `core/layout/`) |

## Sub-folders
| Folder | CLAUDE.md |
|--------|-----------|
| `core/` | See `core/CLAUDE.md` — auth, config, HTTP interceptors, layout/shell |
| `shared/` | See `shared/CLAUDE.md` — pipes, shared components, paged list |
| `features/` | See `features/CLAUDE.md` — all domain feature modules |
