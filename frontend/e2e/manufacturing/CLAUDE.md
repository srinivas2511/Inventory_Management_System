# e2e/manufacturing/ CLAUDE.md

E2E tests for production execution.

## Spec file (7 tests)
`execution.spec.ts`

| Test area | What it verifies |
|-----------|-----------------|
| Production order list | Orders visible; create dialog works |
| Material issue | Issue dialog pre-fills planned − issued quantity; batch picker shows earliest-expiry-first; over-issue warned |
| Operator assignment | Assign operator to an operation; assignment reflected on My Work screen |
| My Work | Operator sees assigned operation; Start → Pause → Resume → Record Output → Complete cycle |
| Operation sequence | Cannot start an operation until previous sequence is COMPLETED (PRD-06 audit trail) |
| Gantt | Schedule Gantt loads and shows operation bars |
| OEE dashboard | OEE tiles load for a machine with recorded downtime |

## Notes
- PRD-06 (audit trail) is a UAT checklist item mapped in `uat-map.json`.
- My Work test runs as `operator1`; the production order is created as `prodmgr1`.
- Uses `prodmgr1`, `operator1`, `storemgr1`, and `admin` as test users.
