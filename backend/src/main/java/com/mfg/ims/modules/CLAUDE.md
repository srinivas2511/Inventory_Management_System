# modules/ CLAUDE.md

ERP module activation — controls which feature modules are enabled per company.

## Responsibilities
- `ModuleActivation` entity — maps a module key (e.g. `WMS`, `PLM`, `ADVANCED_QUALITY`) to enabled/disabled per company.
- `ModuleService` — read and update module activation state; requires `SETTINGS_MANAGE`.
- `ModuleActivationInterceptor` — intercepts requests to module-guarded endpoints; returns `403 MODULE_NOT_ACTIVE` if the module is disabled.
- `ModuleController` — `GET/PUT /api/modules`; exposes activation state to the frontend for menu hiding.

## Usage
Modules that can be deactivated should annotate their controllers (or specific endpoints) with `@RequiresModule("MODULE_KEY")`. The interceptor checks this annotation before the controller method runs.

The frontend reads the module activation list at login and hides nav entries for inactive modules. The server is still the authority — a disabled module returns 403 even if the UI somehow shows the link.
