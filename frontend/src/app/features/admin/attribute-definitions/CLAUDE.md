# admin/attribute-definitions/ CLAUDE.md

Attribute definition management screen (product/material variant attributes).

## Spec files
| File | What it covers |
|------|---------------|
| `attribute-definitions.component.spec.ts` | `AttributeDefinitionsComponent`: lists attribute definitions via `GET /api/master/attribute-definitions`; create dialog POSTs with name, data type, and `active` flag; deactivating an attribute sets `active: false` (does not delete); `ATTRIBUTE_MANAGE` permission required to see create/deactivate controls |

## Notes
- Attribute definitions were renamed from `SpringAttributeDefinitionDto` → `AttributeDefinitionDto` during the branding cleanup (HEAD `2e0580d`); the spec asserts against the new DTO shape.
- The `active` flag (V35) allows hiding obsolete attributes without breaking existing product records that reference them.
