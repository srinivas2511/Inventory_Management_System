# qualityplus/ CLAUDE.md

Advanced quality management: SPC charts, CAPA, First Article Inspections (FAI), and customer complaints.

## Responsibilities
- `domain/` — `SpcChart`, `SpcPoint`, `Capa`, `CustomerComplaint`, `FirstArticleInspection`.
- `service/` — SPC point evaluation (control limits, Western Electric rules); CAPA lifecycle; FAI workflow; complaint lifecycle.
- `web/` — REST controllers: `/api/quality/spc/**`, `/api/quality/capa/**`, `/api/quality/complaints/**`, `/api/quality/fai/**`.

## Permissions (V30)
| Permission | Purpose |
|-----------|---------|
| `SPC_MANAGE` | Manage SPC charts and record points |
| `CAPA_MANAGE` | Create and manage CAPAs |
| `CAPA_CLOSE` | Close a CAPA (separate from manage) |
| `COMPLAINT_MANAGE` | Manage customer complaints |

## SPC alert
When an SPC point triggers a control-limit or Western-Electric-rule violation, an `SPC` alert is raised via `insight/alert/`.

## Document linking
- `CapaDocumentLinkTarget` — CAPAs are document-linkable.
- `ComplaintDocumentLinkTarget` — customer complaints are document-linkable.

## FAI inspection type
FAI is a specialised inspection type seeded by V30 alongside the standard incoming/in-process/final types.

## IT coverage
- `AdvancedQualityIT` (21 tests) — SPC chart CRUD, point evaluation, control-limit violations, CAPA lifecycle, segregation on CAPA close, FAI workflow, customer complaint lifecycle.
