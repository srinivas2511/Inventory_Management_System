# test/vertical/ CLAUDE.md

Industry vertical / multi-tenancy pack tests.

## Test classes
| Class | Type | What it covers |
|-------|------|----------------|
| `IndustryVerticalIT` | IT | Vertical pack loading: a pack declared in system_settings activates the correct attribute definitions and BOM templates; switching packs migrates existing materials without data loss; unknown pack names are rejected; the `demo` profile loads the manufacturing vertical by default |

## Notes
- The vertical pack system is the mechanism that makes this a *configurable* ERP platform rather than a single-industry product. Each pack adds attribute definitions, default routing templates, and costing parameters without touching core tables.
- `IndustryVerticalIT` is a full Spring-context IT backed by Testcontainers; it uses `@DirtiesContext` between pack-switch tests to avoid shared-state pollution.
