# docs/CLAUDE.md

Design and planning documentation for the ERP platform.

## Read order (always before a task)
1. **`REQUIREMENTS.md` v2.0** — what to build: 24+ modules, requirement IDs R1–R20, business rules BR-1–BR-19, NFRs.
2. **`ARCHITECTURE.md` v2.0** — layered architecture, security model, ADRs, segregation-of-duties matrix (§10.2a).
3. **`DESIGN.md` v2.0** — DB schemas, algorithms, API contracts, Angular screen specs, seed data, test matrix.
4. **`PLAN.md` v2.0** — release roadmap R1–R20, task IDs (e.g. R9-1.3), estimates, exit gates.
5. **`decisions.md`** — every non-obvious decision, open defect list, accepted limitations.

## Supporting docs
| File | Purpose |
|------|---------|
| `RUNBOOK.md` | Ops procedures: startup, backup/restore, migrations, alerting |
| `UAT_CHECKLIST.md` | UAT test IDs — every ID must have an e2e test or `uat-map.json` entry |
| `adr/` | Architecture Decision Records (ADR-001 … ADR-NNN) |
| `env.example` | Template for `.env` environment variables |

## Rule: docs and code must agree
If code and docs disagree, **stop and flag it** rather than guessing.
When a task adds new behaviour, update the relevant section of `decisions.md` with:
- What was decided and why
- Any open items or accepted limitations
- Test class and IT count if a new IT was added

## ADRs
Create `adr/ADR-NNN-title.md` for any architectural decision that affects multiple modules or that future contributors would reverse without context. Reference the ADR number in the commit message.
