# plm/ CLAUDE.md

Product Lifecycle Management: engineering change orders (ECOs) and product lifecycle events.

## Responsibilities
- `domain/` — `Eco` (Engineering Change Order), `EcoAffectedItem`, `ProductLifecycleEvent`.
- `service/` — ECO lifecycle (DRAFT → SUBMITTED → APPROVED → IMPLEMENTED → CLOSED); affected-item management; lifecycle event recording.
- `web/` — REST controllers: `/api/plm/**`; requires `PLM_VIEW` / `PLM_MANAGE`.
- `EcoDocumentLinkTarget` — marks `Eco` as a document-linkable entity.

## Key design points (V28)
- An ECO references one or more `EcoAffectedItem` rows (products, BOMs, routings) that it changes.
- ECO approval follows the generic `approval/` workflow; segregation of duties applies (approver ≠ submitter).
- `products.lifecycle_status` column (V28) tracks whether a product is `ACTIVE`, `PHASE_OUT`, `DISCONTINUED`.
- The stray `bom_number` unique constraint was dropped in V28.
- `PLM_ENGINEER` role has `PLM_VIEW` + `PLM_MANAGE`.

## IT coverage
- `PlmIT` (11 tests) — ECO lifecycle, affected-item management, lifecycle status transitions, document linking.
