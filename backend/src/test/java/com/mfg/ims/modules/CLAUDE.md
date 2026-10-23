# test/modules/ CLAUDE.md

Module activation IT.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `ModuleActivationIT` | — | Enable and disable a module via `PUT /api/modules/{key}`; a disabled module's endpoints return `403 MODULE_NOT_ACTIVE`; re-enabling restores access; default activation state matches the `VerticalPacks` configuration |

## Notes
- Module activation state is per-company; `ModuleActivationIT` uses the seeded test company.
- The interceptor check (`ModuleActivationInterceptor`) is tested here; the ArchUnit rule that all module-guarded controllers use `@RequiresModule` is in `architecture/ArchitectureTest`.
