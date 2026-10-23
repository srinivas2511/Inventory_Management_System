# shared/pipes/ CLAUDE.md

Angular pipes available across the whole application.

## Spec files
| File | What it covers |
|------|---------------|
| `ist-date.pipe.spec.ts` | `IstDatePipe`: formats a UTC `Date` or ISO string in IST (+05:30); handles `null` / `undefined` gracefully (returns `''`); timezone is read from `AppConfigService.timezone` so it is overridable; 5 test cases covering standard format strings (`'dd MMM yyyy'`, `'shortDate'`, `'HH:mm'`) and the midnight boundary where IST and UTC fall on different calendar days (R19-7.2) |

## Notes
- `IstDatePipe` is the **only** pipe for date display in this project — never use Angular's built-in `| date:` directly.
- The midnight boundary test (`23:30 UTC = 05:00 IST next day`) is the key regression case: before R19-7.2 the built-in `| date:` would show the UTC date, which is one day behind for Indian users.
