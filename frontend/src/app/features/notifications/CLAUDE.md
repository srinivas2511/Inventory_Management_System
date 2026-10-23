# features/notifications/ CLAUDE.md

User notifications: bell icon with badge, inbox, subscription preferences, and admin template management.

## Screens
| Component | Purpose |
|-----------|---------|
| `NotificationBellComponent` | Toolbar bell icon with unread count badge; opens dropdown or routes to inbox |
| `NotificationInboxComponent` | Full notification inbox list; mark read / mark all read |
| `NotificationPreferencesComponent` | Per-user subscription toggle for each notification type |
| `NotificationAdminComponent` | Admin management of notification templates (requires `NOTIFICATION_MANAGE`) |

## API
`notifications.api.ts` → `/api/notifications/**`.

## Notes
- `NotificationBellComponent` is imported by `core/layout/ShellComponent` and lives in the toolbar.
- Unread count is polled on a short interval by `core/notification/NotificationService`.
- Any authenticated user reads their own notifications; `NOTIFICATION_MANAGE` is needed for template administration only.
