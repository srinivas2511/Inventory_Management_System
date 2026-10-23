# test/config/ CLAUDE.md

Tests for application configuration and demo data loading.

## Test classes
| Class | Type | What it covers |
|-------|------|----------------|
| `DemoDataLoaderIT` | IT (8 tests) | Verifies the demo loader runs to completion in `dev`/`demo` profile, creates all 14 DESIGN §11.2 role users, seeds the full demo workflow (PO → GR → production → dispatch → AR invoice), creates the three DESIGN 11.4 alert-trigger scenarios (no-stock material, late production run, waiting inspection), and calls `AlertSweepJob.runSweep()` directly (bypasses the ShedLock 5-minute hold) |
| `VerticalPacksTest` | Unit | Verifies that `VerticalPacks` correctly resolves which feature packs are compiled in from classpath markers; tests are profile-independent |

## Notes
- `DemoDataLoaderIT` must be updated whenever a new mandatory demo step is added (step numbers must stay sequential; gaps break later steps that depend on prior state).
- The demo loader is guarded by `@Profile({"dev","demo"})` in production code — `DemoDataLoaderIT` activates the `demo` test profile.
- `AlertSweepJob.runSweep()` is called directly by the loader because the scheduled ShedLock hold (5 minutes) would block the loader's own thread, causing INS-03/04/05 e2e tests to fail.
