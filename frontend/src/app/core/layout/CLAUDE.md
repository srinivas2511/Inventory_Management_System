# core/layout/ CLAUDE.md

Shell chrome — toolbar, side-navigation, and locale switcher.

## Spec files
| File | What it covers |
|------|---------------|
| `nav-modules.spec.ts` | `NavConfig` / nav-module filtering: plant-scoped users see only plant-level nav groups (inventory, production, quality, procurement, sales, maintenance, WMS, scheduling); company-level groups (finance, controlling, MRP, analytics, HR, CRM, PLM, integration, admin) are hidden for plant-scoped users; `LocaleSwitcherComponent` is present in the toolbar |
| `shell.component.spec.ts` | `ShellComponent`: renders toolbar, sidenav and `<router-outlet>`; user display name shown in toolbar; `LocaleSwitcherComponent` activates correct locale URL on click; sidenav opens and closes; logout button calls `AuthService.logout()` |

## Notes
- `nav-modules.spec.ts` covers the plant-scoping UX requirement: a user in `user_plant_access` with no `PLANT_ALL_ACCESS` must not see company-level nav items (the server returns 403 `PLANT_SCOPE_COMPANY_LEVEL` regardless, but hiding them is the UX requirement from R9-7.1).
- `LocaleSwitcherComponent` is a standalone component from `shared/components/locale-switcher/`; its CLAUDE.md and unit tests live under `shared/components/`.
