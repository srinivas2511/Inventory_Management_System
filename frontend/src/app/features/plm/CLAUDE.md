# features/plm/ CLAUDE.md

Product Lifecycle Management: Engineering Change Orders (ECOs) and product lifecycle events.

## Screens
| Component | Purpose |
|-----------|---------|
| `EcoListComponent` | ECO list with status filter |
| `EcoDetailComponent` | ECO detail: affected items, linked documents, approval actions |
| `LifecycleListComponent` | Product lifecycle event log |

## API
`plm.api.ts` → `/api/plm/**`; requires `PLM_VIEW` / `PLM_MANAGE`.

## Notes
- `EcoDetailComponent` embeds `DocumentAttachmentsComponent` (from `documents/`) for linked documents.
- Approve action is disabled for the ECO submitter (segregation of duties — server enforces).
- Hidden for plant-scoped users (company-level function).
