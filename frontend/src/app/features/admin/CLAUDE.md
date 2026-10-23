# features/admin/ CLAUDE.md

Platform administration: users, roles, system settings, attribute definitions, negative-stock overrides, plants, audit log, and module activation.

## Sub-folders
| Folder | Screens |
|--------|---------|
| `user/` | User list, create/edit dialog, assign roles dialog, assign plants dialog, reset password dialog |
| `role/` | Role list, role detail/permission assignment dialog |
| `settings/` | System settings form (key-value pairs for all `system_settings` rows) |
| `attribute-definitions/` | Product attribute definition list; toggle `active` flag per definition |
| `negative-stock-overrides/` | Per-material negative-stock override toggle (Materials/Products tabs); reason dialog required before every change |
| `plants/` | Plant list and create/edit; user-plant access assignment |
| `audit/` | Audit log list with filters (entity type, actor, date range); read-only |
| `modules/` | ERP module activation toggle |

## Key permission requirements
| Screen | Permission |
|--------|-----------|
| User management | `IAM_VIEW` / `IAM_MANAGE` |
| Role assignment | `ROLE_MANAGE` |
| Password reset | `USER_PASSWORD_RESET` |
| System settings | `SETTINGS_MANAGE` |
| Negative-stock overrides | `INVENTORY_NEGATIVE_OVERRIDE` |
| Plant management | `SETTINGS_MANAGE` |
| Audit log | `AUDIT_VIEW` |
| Module activation | `SETTINGS_MANAGE` |

## Notes
- Negative-stock override requires a mandatory reason before toggling; `NegativeStockReasonDialogComponent` collects it.
- User creation flows through `UserFormDialogComponent`; role and plant assignment are separate dialogs opened from the user list row.
- `admin.api.ts` is the shared API service for this folder; `admin/plants/plant.api.ts` is plant-specific.
