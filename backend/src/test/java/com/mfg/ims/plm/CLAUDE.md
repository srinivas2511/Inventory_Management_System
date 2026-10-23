# test/plm/ CLAUDE.md

Product Lifecycle Management tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `PlmIT` | IT | 11 | ECO lifecycle (DRAFT → SUBMITTED → APPROVED → IMPLEMENTED → CLOSED); affected-item management; document linking on an ECO; lifecycle status transition (ACTIVE → PHASE_OUT → DISCONTINUED); SoD: ECO submitter cannot approve |
| `PlmSecurityMatrixTest` | ArchUnit | — | PLM endpoints have `PLM_VIEW` / `PLM_MANAGE` |
| `PlmStateMachineTest` | Unit | — | ECO and product lifecycle state machines: valid transitions; invalid transitions throw; terminal states cannot be re-entered |

## Notes
- `PlmIT` is non-transactional for the SoD test (commits the ECO as one user, attempts approval as the same user).
- The `PlmIT` document-link test verifies `EcoDocumentLinkTarget` is resolvable (a new linkable entity type that is missing its `*DocumentLinkTarget` registration will fail `DocumentLinkTargetsTest` in `document/`).
