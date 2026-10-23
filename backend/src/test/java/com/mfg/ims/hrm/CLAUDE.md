# test/hrm/ CLAUDE.md

Human resources ITs and unit tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `HrmIT` | IT | 19 (incl. SOD-PAY-1) | Employee CRUD; shift and attendance entry; leave request and balance; payroll run with Indian statutory deductions (PF, ESI); `SOD-PAY-1`: payroll run initiator cannot approve (SoD); leave balance returns empty list (not 404) for a user with no employee record |
| `HrmSecurityMatrixTest` | ArchUnit | — | Every HRM endpoint has `HR_VIEW` / `HR_MANAGE` / `PAYROLL_RUN` / `PAYROLL_APPROVE` |
| `HrmStateMachineTest` | Unit | — | Leave request state machine: `PENDING → APPROVED / REJECTED`; valid and invalid transitions |
| `PayrollCalculatorTest` | Unit | — | Statutory deduction arithmetic: PF on basic ≤ ₹15,000; ESI on gross ≤ ₹21,000; edge cases at the cap boundaries; NUMERIC(18,4) precision |
| `PayrollRulePackTest` | Unit | — | Rule-pack selection from `hrm.payroll.rule_pack` system setting; correct deduction rates loaded for each pack |

## Notes
- `HrmIT` SOD-PAY-1 is non-transactional — it commits the payroll run as one user, then attempts approval as the same user and expects 403.
- `PayrollCalculatorTest` uses parameterised test cases covering the PF cap (₹15,000 basic), ESI cap (₹21,000 gross), and zero-wage edge cases.
