# test/common/ CLAUDE.md

Common / cross-cutting unit tests.

## Test classes
| Class | Type | What it covers |
|-------|------|----------------|
| `ProblemsTest` | Unit | RFC 7807 `ProblemDetail` response shape: every `@ExceptionHandler` in `GlobalExceptionHandler` returns the correct `type`, `title`, `status`, and at least one `detail` field; 400 errors carry field-level `errors` extension; 403 errors carry `code` (the `@PreAuthorize` authority slug); no stack traces in the response body |

## Notes
- `ProblemsTest` is a `@WebMvcTest` slice — it instantiates only `GlobalExceptionHandler` and a minimal controller stub that throws each mapped exception type; no database, no full Spring context.
- The `code` field on 403 responses (e.g. `PURCHASE_APPROVE`) is what the frontend uses to show a permission-specific message rather than a generic "Access Denied" string.
- Adding a new custom exception requires a corresponding case in `ProblemsTest` to keep the error-shape contract verified.
