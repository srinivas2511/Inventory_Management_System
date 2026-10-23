# features/documents/ CLAUDE.md

Document management: list, upload, version history, approval, and attachment dialog.

## Screens
| Component | Purpose |
|-----------|---------|
| `DocumentListComponent` | All documents with category/status filters |
| `DocumentDetailComponent` | Version history, approve/archive actions |
| `DocumentUploadComponent` | Upload a new document or add a version |
| `DocumentAttachmentsComponent` | Inline attachment list used inside other feature screens (BOM, ECO, product, machine) |
| `DocumentsDialogComponent` | Dialog wrapper around `DocumentAttachmentsComponent` for embedding in edit dialogs |

## API
`documents.api.ts` → `/api/documents/**`.

## Permissions
| Permission | Action |
|-----------|--------|
| `DOCUMENT_VIEW` | View documents and version history |
| `DOCUMENT_UPLOAD` | Upload and add versions |
| `DOCUMENT_APPROVE` | Approve a version |
| `DOCUMENT_MANAGE` | Manage categories, archive |

## Notes
- `DocumentAttachmentsComponent` / `DocumentsDialogComponent` are imported into BOM, ECO, product, machine, and CAPA edit screens to show linked documents inline.
- Hidden for plant-scoped users (company-level function).
