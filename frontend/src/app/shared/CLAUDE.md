# shared/ CLAUDE.md

Reusable UI building blocks imported by any feature — no business logic, no HTTP calls.

## Contents
| Path | What it is |
|------|-----------|
| `components/` | `PagedListComponent`, `StatusBadgeComponent`, `LocaleSwitcherComponent`, `ConfirmDialogComponent`, list-paginator, etc. |
| `directives/` | `HasPermissionDirective` (structural), `AutofocusDirective` |
| `pipes/` | `IstDatePipe` — **use this instead of `| date:`** for all date display |
| `paged-list.ts` | `PagedList<T>` class with `next()` / `prev()` and query-param helpers |
| `lookup.api.ts` | Centralised lookup endpoints (`/api/lookups/**`) used by create/edit dialogs |

## IstDatePipe
```typescript
// Template usage
{{ item.createdAt | istDate:'dd MMM yyyy' }}
{{ item.scheduledDate | istDate:'shortDate' }}
```
Applies `Asia/Kolkata` (IST +05:30) from `AppConfigService.timezone` (default, overridable via `config.json`).
All 55+ components that display dates use this pipe — never `| date:` directly.

## StatusBadgeComponent
Displays a coloured chip for document/stock statuses.
- `role="img"` + `aria-label="{{ displayLabel }}"` on the badge (WCAG 2.1 AA).
- Decorative `mat-icon` inside gets `aria-hidden="true"`.
- Amber contrast: use `#92400e` on `#fff8e1` (5.9:1) — not the original `#f57f17`.

## Rules
- Components here must be **standalone** and import only Angular Material + other `shared/` items.
- No `HttpClient` calls — pass data in via `@Input()`.
- No feature-specific routing or permissions logic.
- Every component needs a `*.spec.ts`; specs for `StatusBadgeComponent` are the WCAG contract (`wcag-audit.spec.ts`).
