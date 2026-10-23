# shared/components/ CLAUDE.md

Reusable standalone UI components shared across feature modules.

## Spec files
| File | What it covers |
|------|---------------|
| `locale-switcher.component.spec.ts` | `LocaleSwitcherComponent`: three buttons (EN / हिं / ಕನ); active locale detected from `window.location.pathname` (`/hi/` → Hindi active, `/kn/` → Kannada active, else English); clicking a button navigates to the locale's base URL; pure functions `activeLocale()` and `switchedUrl()` are exported and tested directly (R20-5.2) |
| `plant-field.component.spec.ts` | `PlantFieldComponent`: renders a plant selector for admin forms; shows all plants when the current user has no plant restriction; for plant-scoped users shows only their assigned plants; emits `plantChange` on selection |
| `wcag-audit.spec.ts` | `StatusBadgeComponent` WCAG contract (NFR-007 / R19-7.3): every badge has `role="img"` and `aria-label`; decorative `mat-icon` inside the badge has `aria-hidden="true"`; amber background uses the corrected high-contrast colour `#92400e` (≥ 4.5:1 on white); 5 test cases |

## Notes
- `wcag-audit.spec.ts` was added as part of the WCAG 2.1 AA audit (HEAD `a42ee06`). It is a contract test — any change to `StatusBadgeComponent` that removes `role="img"` or changes the amber colour back to `#f57f17` will fail this spec.
- All components in this folder are standalone — no `NgModule`, no barrel `index.ts`.
