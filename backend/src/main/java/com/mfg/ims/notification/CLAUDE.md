# notification/ CLAUDE.md

User notification system: outbox, templates, subscriptions, and delivery.

## Responsibilities
- `domain/` — `NotificationTemplate`, `NotificationSubscription`, `Notification` (outbox row).
- `service/` — `NotificationService`: create notification from template + context; mark read; list for the current user.
- `listener/` — Spring event listeners that translate domain events (alerts, document approvals, etc.) into notifications via templates.
- `web/` — REST controllers: `/api/notifications/**`; requires `NOTIFICATION_MANAGE` to manage templates/subscriptions; any authenticated user reads their own.

## Outbox model
- Notifications are written to the `notifications` table in the same transaction as the triggering event (transactional outbox).
- A delivery job pushes undelivered rows to subscribed channels (in-app, email — if configured).
- In-app notifications are polled by the Angular frontend `core/notification/NotificationService`.

## Templates (V31)
Templates are seeded by Flyway; each has a `type` key (matches an alert type or domain event), a subject template, and a body template (Mustache-style `{{variable}}` placeholders). `NotificationIT` verifies template rendering and delivery counts (18 tests).

## IT coverage
- `NotificationIT` (18 tests) — template rendering, subscription filtering, outbox delivery, company name in template context.
