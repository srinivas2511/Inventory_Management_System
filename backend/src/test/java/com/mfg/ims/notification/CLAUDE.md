# test/notification/ CLAUDE.md

Notification system tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `NotificationIT` | IT | 18 | Template rendering with context variables (including company name); subscription filtering (only subscribed users receive a notification); outbox delivery; mark-read and mark-all-read; unread count; `NOTIFICATION_MANAGE` required for template admin |
| `NotificationServiceTest` | Unit | — | Service logic: template lookup by type, context variable substitution, subscription check |
| `NotificationDeliveryServiceTest` | Unit | — | Delivery service: polls outbox, marks delivered or schedules retry; channel routing (in-app vs email) |
| `TemplateRendererTest` | Unit | — | Mustache-style `{{variable}}` rendering; missing variable → empty string (not exception); HTML escaping for email channel |
| `NotificationSecurityMatrixTest` | ArchUnit | — | Notification admin endpoints require `NOTIFICATION_MANAGE`; user-facing endpoints require any authenticated user |

## Notes
- `NotificationIT` includes a regression for the `NotificationIT` company-name assertion: the seeded company was renamed from "Spring Manufacturing Co." to "Manufacturing Co." in V56; this test verifies the template renders the current name.
- The outbox delivery test uses `TestClock` to advance time past the retry delay.
