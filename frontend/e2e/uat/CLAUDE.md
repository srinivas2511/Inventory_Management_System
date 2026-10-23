# e2e/uat/ CLAUDE.md

UAT gap coverage — tests for `docs/UAT_CHECKLIST.md` IDs that are not covered by the main spec folders.

## Spec file (9 tests)
`uat-gaps.spec.ts`

Covers UAT IDs that are not mapped by any other spec file:

| UAT ID | What it verifies |
|--------|-----------------|
| QCF-05 | HOLD batch not offered in reservation; HOLD batch refused via direct API call |
| STK-04 | Stock after dispatch matches expected quantity |
| FIN-02 | Cost summary of a closed production order shows correct variance |
| PRC-04 | PO submitter cannot approve (UI disabled + API 403) |
| PRD-06 | Production order audit trail shows all state transitions |
| INS-02 | In-process inspection recorded and linked to production operation |
| SEC-08b | SO submitter cannot approve (UI disabled + API 403) |
| STK-01 | Opening balance dialog saves and shows in balance list |
| FIN-JNL-2 | Journal preparer cannot approve (UI disabled + API 403) |

## UAT traceability enforcement
`uat-map.json` (in `e2e/`) maps each `docs/UAT_CHECKLIST.md` ID to the spec file and/or test name that covers it. `UatTraceabilityTest` (backend IT) reads this file and fails the `mvn verify` build if any checklist ID has no entry. **Always update `uat-map.json` when adding a new UAT checklist item or a new spec that covers an existing ID.**
