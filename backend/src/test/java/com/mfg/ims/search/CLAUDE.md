# test/search/ CLAUDE.md

Search and pagination tests.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `SearchIT` | 8 | Full-text and filter-based search across materials, products, partners; search results are paginated; empty results return an empty page (not 404); special characters in search terms do not cause 500 |
| `PagedListOrderingIT` | — | Every paged `GET /api/**` endpoint returns results in a stable, documented order across pages (guard for `PagedEndpointOrderingTest` ArchUnit rule); verifies that adding a new item to page 1 does not cause a duplicate on page 2 |

## Notes
- `PagedListOrderingIT` is the integration guard for `PagedEndpointOrderingTest` (ArchUnit): the ArchUnit rule checks that every paged controller method declares a default sort at compile time; `PagedListOrderingIT` verifies the sort is actually stable at runtime.
- The ArchUnit + IT combination was added after QCI-01 failed intermittently because a new inspection fell off page 1 when results had no stable ordering (see `docs/decisions.md`).
