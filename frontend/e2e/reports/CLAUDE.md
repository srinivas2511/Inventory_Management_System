# e2e/reports/ CLAUDE.md

E2E tests for alerts and operational reports.

## Spec file (7 tests)
`insight.spec.ts`

| Test area | UAT ID | What it verifies |
|-----------|--------|-----------------|
| Alert list loads | INS-01 | Alert list visible with at least one seeded alert |
| Low stock alert | INS-03 | A no-stock material triggers a low-stock alert after `AlertSweepJob` runs |
| Late production alert | INS-04 | A late production run triggers an alert |
| Waiting inspection alert | INS-05 | A pending inspection triggers an alert |
| Alert count on dashboard | INS-06 | Dashboard alert badge count matches alert list |
| Standard report runs | — | Purchase orders, quality inspections reports load without 5xx |
| Traceability | — | Material batch trace returns forward and backward chain |

## Notes
- INS-03/04/05/06 depend on the `DemoDataLoader` seeding the specific demo data items (a no-stock material, a late production run, a waiting inspection). These are seeded in `dev`/`demo` profiles only.
- `AlertSweepJob.runSweep()` is called directly by the demo loader (bypasses the ShedLock 5-minute hold).
- Uses `admin` and `quality1` as test users.
