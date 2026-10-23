# hrm/ CLAUDE.md

Human Resource Management: employees, shifts, attendance, leave, training, and payroll.

## Responsibilities
- `domain/` — `Employee`, `Shift`, `Attendance`, `LeaveRequest`, `TrainingRecord`, `PayrollRun`, `PayrollSlip`.
- `service/` — employee lifecycle; payroll run (Indian statutory deductions); leave balance calculation.
- `web/` — REST controllers: `/api/hrm/**`; permissions: `HR_VIEW`, `HR_MANAGE`, `PAYROLL_RUN`, `PAYROLL_APPROVE`.

## Payroll rules (Indian statutory, V25)
- **PF**: 12% employer + 12% employee on basic up to ₹15,000/month.
- **ESI**: 3.25% employer + 0.75% employee on gross ≤ ₹21,000/month.
- Rates stored in `system_settings` (prefix `hrm.payroll.*`); `hrm.payroll.rule_pack` selects the rule set.
- `hrm.payroll.unrecorded_day_policy` — how unrecorded attendance days are treated (V26).
- **Segregation of duties**: a payroll run cannot be approved by its initiator (`SegregationOfDuties`, Business Rule 19, `HrmIT SOD-PAY-1`).

## Leave balances
- `GET /api/hrm/leave-balances` returns an empty list (not 404) for a user with no employee record.

## IT coverage
- `HrmIT` (19 tests, incl. `SOD-PAY-1`) — employee CRUD, payroll statutory deductions, leave balance edge cases.
