# features/hrm/ CLAUDE.md

Human Resources: employees, shifts, attendance, leave, payroll, training renewals.

## Screens
| Component | Purpose |
|-----------|---------|
| `EmployeeListComponent` | Employee list |
| `EmployeeDetailComponent` | Employee profile, skills, assignment |
| `ShiftListComponent` | Shift definitions |
| `AttendancePageComponent` | Attendance entry and daily view |
| `LeavePageComponent` | Leave requests; balance display |
| `PayrollRunListComponent` | Payroll run list; initiate a new run |
| `PayrollRunDetailComponent` | Run detail with statutory deduction breakdown; Approve action |
| `PayslipComponent` | Individual payslip view/print |
| `TrainingRenewalsComponent` | Upcoming training renewal reminders |

## API
`hrm.api.ts` → `/api/hrm/**`; requires `HR_VIEW` / `HR_MANAGE` / `PAYROLL_RUN` / `PAYROLL_APPROVE`.

## Key rules
- **Segregation of duties**: Approve action on a payroll run is disabled for the user who initiated it (server enforces).
- Leave balances return an empty list (not an error) for a user with no employee record.
- Hidden for plant-scoped users (company-level function).
