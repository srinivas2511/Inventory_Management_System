# e2e/sales/ CLAUDE.md

E2E tests for sales dispatch documents.

## Spec file (1 test)
`dispatch-documents.spec.ts`

| Test area | What it verifies |
|-----------|-----------------|
| PDF dispatch documents | Completing a dispatch generates a PDF delivery note and a PDF packing list; both download without error; file names contain no path separators (GC-4 fix) |

## Notes
- This test covers GC-4: `DispatchDocumentRenderer` (PDFBox) output — it checks the HTTP response is a valid PDF (Content-Type `application/pdf`) and that the `Content-Disposition` filename is safe.
- The test creates a full sales order → reserve → dispatch flow using `flow.ts` helpers before asserting the PDF endpoints.
- Uses `sales1`, `dispatch1`, and `admin` as test users.
