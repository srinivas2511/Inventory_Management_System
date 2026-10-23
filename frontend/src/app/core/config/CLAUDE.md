# core/config/ CLAUDE.md

Runtime configuration service loaded at startup.

## Spec files
| File | What it covers |
|------|---------------|
| `app-config.service.spec.ts` | `AppConfigService`: `APP_INITIALIZER` fetches `assets/config.json` before routes activate; `appName` defaults to `"ERP System"` when the key is absent; `timezone` defaults to `"Asia/Kolkata"`; `IstDatePipe` reads timezone from this service (not hardcoded) |

## Notes
- `config.json` is the runtime override file — baked into Docker images at deploy time to set environment-specific values without a rebuild.
- `appName: "ERP System"` was the D-04 / `cfa906d` fix: the e2e suite was asserting against `"ERP System"` but the seed had the old name.
- `timezone` default `"Asia/Kolkata"` drives `IstDatePipe`; overriding it in `config.json` shifts all displayed dates.
