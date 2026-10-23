# test/document/ CLAUDE.md

Document management tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `DocumentIT` | IT | 17 | Category CRUD; upload new document; add version; approve version; archive; link to BOM, product, ECO, machine; version history ordering |
| `DocumentLinkTargetsTest` | Unit | — | Verifies all `*DocumentLinkTarget` marker classes are registered and resolvable (no missing registration for a new linkable entity) |
| `DocumentSecurityMatrixTest` | ArchUnit | — | Every document endpoint has the correct `DOCUMENT_*` permission |
| `DocumentStatusTest` | Unit | — | Document status state machine: valid transitions (DRAFT → REVIEW → APPROVED → ARCHIVED); invalid transitions throw |
| `LocalFileDocumentStorageTest` | Unit | — | `document/storage/` — local filesystem storage adapter: stores, retrieves, and deletes files; path traversal attempt is rejected |
| `UploadPolicyTest` | Unit | — | Max file size, allowed MIME types, filename sanitisation |

## Notes
- `DocumentIT` is non-transactional for tests that verify version history (each version is a separate committed row).
- `LocalFileDocumentStorageTest` is the guard for the path-traversal security fix: a filename containing `../` must be rejected before the file is written.
