# product/ CLAUDE.md

Finished-goods product master: product types, attribute definitions, product variants, and approved-customer lists.

## Responsibilities
- `domain/` — `Product`, `ProductType`, `AttributeDefinition`, `ProductAttribute` (values per product), `ProductVariant`.
- `service/` — product CRUD; attribute validation against definitions; lifecycle status management.
- `validation/` — attribute value validators (numeric range, enum, regex).
- `web/` — REST controllers: `/api/products/**`; requires `MASTER_PRODUCT_VIEW` / `MASTER_PRODUCT_MANAGE`.
- `ProductDocumentLinkTarget` — marks `Product` as a document-linkable entity.

## Key design points
- **Product types** (`product_types` lookup, V35): groups products (e.g. Spring, Custom); drives which attribute definitions apply.
- **Attribute definitions** (`attribute_definitions`, V35): configurable per product type; `active` column controls visibility. Formerly `SpringAttributeDefinitionDto` → renamed `AttributeDefinitionDto` (branding cleanup).
- **Product variants** (`ProductVariant`): formerly `SpringType` — renamed in the branding cleanup.
- **Lifecycle status** (`products.lifecycle_status`, V28): `ACTIVE`, `PHASE_OUT`, `DISCONTINUED` — managed via PLM ECOs.
- Approved-customer products are managed on the customer side (`partner/`).

## IT coverage
- Product attributes and variants are exercised indirectly through `SearchIT` (8 tests) and the E2E suite.
