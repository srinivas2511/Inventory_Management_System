# document/ CLAUDE.md

Document management: categories, upload, versioning, approval workflow, and cross-entity linking.

## Responsibilities
- `domain/` — `DocumentCategory`, `Document`, `DocumentVersion`, `DocumentLink` (polymorphic link to any entity).
- `service/` — upload, new version, approve, archive; version history.
- `storage/` — file storage abstraction (local filesystem in dev, object store in prod).
- `web/` — REST controllers: `/api/documents/**`.

## Permissions
| Permission | Role |
|-----------|------|
| `DOCUMENT_VIEW` | read documents and versions |
| `DOCUMENT_UPLOAD` | upload new documents and versions |
| `DOCUMENT_APPROVE` | approve a document version |
| `DOCUMENT_MANAGE` | manage categories, archive |

## Cross-entity linking
Documents are linked to entities via `DocumentLink` (polymorphic). Each linkable entity implements the `DocumentLinkTarget` marker interface and declares a `*DocumentLinkTarget.java` in its own package (e.g. `BomDocumentLinkTarget`, `ProductDocumentLinkTarget`, `EcoDocumentLinkTarget`, `MachineDocumentLinkTarget`). Linking requires the entity-specific view permission.

## IT coverage
- `DocumentIT` (17 tests) — category CRUD, upload, versioning, approval, linking to BOM, product, ECO, machine.
