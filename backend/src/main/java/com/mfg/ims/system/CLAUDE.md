# system/ CLAUDE.md

System-level endpoint: health ping and system info.

## Responsibilities
- `SystemController` — `GET /api/system/ping` (public, no auth) and `GET /api/system/info` (requires `SETTINGS_VIEW`).
- Used by the frontend to check server reachability on startup and by monitoring tools.

## Notes
- This package is intentionally minimal. All system settings management lives in `common/settings/`.
- Spring Boot Actuator endpoints (`/actuator/health`, `/actuator/metrics`, etc.) are configured in `config/SecurityConfig`. Health and info are public; metrics/prometheus require `SYSTEM_MONITOR` (ADMIN only, V46). Swagger/OpenAPI is only available in the `dev` profile.
- `ActuatorExposureIT` (4 tests) guards the actuator exposure rules: anonymous → 401, non-admin → 403, admin → 200, Swagger outside dev → 404.
