# e2e/negative/ CLAUDE.md

E2E tests for business rule violations and expected 4xx error paths.

## Spec file (9 tests)
`business-rules.spec.ts`

| Test area | Business rule | What it verifies |
|-----------|--------------|-----------------|
| Duplicate customer GST number | BR-? | Server returns 409; UI shows field error |
| Negative stock (no override) | BR-3 | Issue refused when stock would go negative |
| Over-issue confirmation | BR-? | Over-issue shows warning; server decides |
| PO approval by submitter | BR-? | Approve disabled in UI; direct API call returns 403 |
| SO approval by submitter/creator | BR-19 / SEC-008 | Approve disabled; API returns 403 |
| Journal approval by preparer | BR-19 / SEC-008 | Approve disabled; API returns 403 |
| Dispatch a HOLD batch | D-38 | HOLD batch not offered in reservation dialog |
| Posting to closed period | BR-? | Post refused; Finance Manager override required |
| Stock adjustment without reason | BR-? | Server requires reason; form shows field error |

## Notes
- These tests intentionally exercise the unhappy path — they must produce errors, not successes.
- Some tests use direct API calls (`api.ts`) to bypass the UI and hit the 403/409 directly (PRC-04, SEC-08b patterns).
- Uses `purchase1`, `sales1`, `finance1`, `storemgr1`, and `admin` as test users.
