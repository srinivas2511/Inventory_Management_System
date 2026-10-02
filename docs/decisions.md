# Decision log (business decisions awaiting confirmation)

Defaults are in force so work never stalls. Change the status when the product owner confirms or overrides.
Source: DESIGN.md §14, PLAN.md §14.

| # | Decision | Default in force | Needed before | Status | Owner |
|---|---|---|---|---|---|
| 11 | Attribute set for Belleville and Custom springs | Belleville: outer/inner Ø, thickness, free height, load, stack; Custom: free-form definitions | Phase 1 (1.9) | Default | Engineering |
| 2 | Stock-adjustment Admin threshold | variance **value**, ₹10,000 (`inventory.adjustment.admin_threshold`) | Phase 2 (2.7) | Default | Management / Stores |
| 3 | Who approves purchase orders | any user holding `PURCHASE_APPROVE` (Admin in demo) | Phase 3 (3.1) | Default | Management |
| 6 | Tax / currency / invoicing | INR, GST % per PO line, invoice number is a text reference only | Phase 3 | Default | Finance |
| 1 | BOM quantity basis | 0.045 kg per piece (requirement example "0.45 kg" read as per 10 pieces); `base_quantity` supported | Phase 4 (4.2) | Default | Engineering |
| 7 | Traceability granularity | order-level; batch-level optional (`quality.trace_granularity`) | Phase 4 | Default | Quality |
| 9 | Hold quantity in final inspection | HOLD stock status until decision | Phase 5 | Default | Quality |
| 4 | Production target source | single monthly setting (`production.monthly_target`) | Phase 7 | Default | Production |
| 8 | Customer complaint register / CAPA | `CUSTOMER_RETURN` + remarks only | Phase 7 | Default | Quality |
| 10 | UI languages | English, i18n-ready | Phase 8 | Default | Management |
| 12 | Barcode labels | not in v1 (batch/location codes are scan-ready) | Phase 8 | Default | Stores |
| 13 | Hosting target, backup policy, e-mail provider | Docker Compose on a Linux VM, nightly backups | Phase 8 | Open | IT |

## Phase 0 technical notes
- Java: the project targets Java 17 (`<release>17`); it also builds on newer JDKs.
- Fonts and icons are self-hosted from npm (`@fontsource/roboto`, `material-icons`) so builds and the running app work without internet access (ADR-15).

## Phase 1 — role/permission seed interpretation (task 1.2)
`V3_1__reference_seed.sql` expands ARCHITECTURE §10.3 into concrete permission codes from DESIGN §7.1 (75 codes, 13 roles, 142 grants). Where §10.3 is silent or abbreviated, the seed follows REQUIREMENTS §3.3. Roles can be changed from the roles screen (`PUT /api/roles/{id}/permissions`); the test `ReferenceSeedIT` holds the expected matrix.

| Point | What the seed does | Why |
|---|---|---|
| ADMIN `USER_*`, "all `*_VIEW`" | all four USER codes and every `*_VIEW`, `INVENTORY_VIEW_ALL`, `REPORT_VIEW`, `DASHBOARD_VIEW`, `TRACEABILITY_VIEW`, `AUDIT_VIEW` | literal reading |
| ADMIN approvals | also holds `PURCHASE_APPROVE` (decision #3, demo), `DISPATCH_APPROVE`, `ADJUST_APPROVE` (besides `ADJUST_APPROVE_ADMIN`) | `DISPATCH_APPROVE` would otherwise have no holder; Admin must be able to approve small adjustments too. Requester ≠ approver is enforced in services |
| ADMIN master data | all `*_MANAGE` master-data codes (`SUPPLIER_`, `CUSTOMER_`, `LOCATION_`, `OPERATION_`, `REJECTION_REASON_`) | §10.3 lists only some, but REQUIREMENTS §3.3 gives Admin all master data |
| MANAGEMENT `*_VIEW` | every `*_VIEW` **except** `USER_VIEW` and `AUDIT_VIEW`; plus `EXPORT_DATA` | management is read-only on business data, not on people or the audit log |
| `INVENTORY_VIEW_ALL` | held by every role with `INVENTORY_VIEW` except STORE_OPERATOR | Store Operator is the only warehouse-scoped role (§10.2) |
| Extra view codes | `MATERIAL_VIEW`, `PRODUCT_VIEW`, `BOM_VIEW`, `QUALITY_VIEW`, `SALES_VIEW`, `DISPATCH_VIEW`, `MAINTENANCE_VIEW`, `VALUATION_VIEW`, `TRACEABILITY_VIEW` given to roles whose REQUIREMENTS §3.3 duties need them | §10.3 omits them |
| `PRODUCTION_VIEW` | PRODUCTION_MANAGER, ADMIN, MANAGEMENT only | DESIGN §4.10: holders see **all** orders; Supervisor and Operator are scoped to assigned orders |
| Other catalogue codes | `BOM_REVIEW`→ENGINEER; `PRODUCTION_APPROVE`, `PRODUCTION_REOPEN`→PRODUCTION_MANAGER; `INVENTORY_RETURN`→STORE_MANAGER, STORE_OPERATOR; `PROBLEM_REPORT`→SUPERVISOR too | in DESIGN §7.1 but not in §10.3 |
| Not seeded | `PURCHASE_APPROVE` for PURCHASE_MANAGER | §10.3: "only if granted" |

Additive settings: `security.lockout.minutes` = 15 (lockout duration; DESIGN §10.1 lists only the attempt count). Token lifetimes stay application properties (`access-ttl` 15m, `refresh-ttl` 7d). The `MAIN-WH` warehouse and locations (DESIGN §11.1) are not in this seed: warehouses arrive with Phase 2, and spring attribute definitions with task 1.9.

## Phase 1 — authentication decisions (task 1.3)
Choices made where DESIGN/ARCHITECTURE left room; change by editing the setting or the named property.

| Point | Behaviour | Where to change |
|---|---|---|
| Password minimum | 12 characters (also upper, lower, digit, symbol; at most 72 bytes because BCrypt truncates beyond that) | `security.password.min_length` |
| Lockout | 5 failed logins ⇒ 15-minute fixed lock, answered with 423 `ACCOUNT_LOCKED` (DESIGN §6.2) rather than a generic 401, so the user knows to wait | `security.lockout.attempts`, `security.lockout.minutes` |
| First login | no token is issued until the password is changed (DESIGN §5.1) | — |
| Token lifetimes | access 15 min; refresh 7 days **absolute** from sign-in (rotation does not extend it) | `ims.security.access-ttl`, `refresh-ttl` |
| Refresh reuse | strict: replaying a rotated token revokes the family. Two tabs refreshing with the same cookie at the same instant can therefore sign the user out; the SPA must refresh single-flight (DESIGN §8.5 `AuthInterceptor`) | — |
| Reset link | 30 minutes, single use, hashed; e-mail sent after commit and off the request thread | `ims.security.reset-token-ttl`, `ims.mail.*`, `APP_BASE_URL` |
| Audit | every outcome is published as an `AuthEvent`; persisting them to `audit_logs` is task 1.6 | — |
| Not in 1.3 | bootstrap Admin account and demo users (need the user service, task 1.5); bearer-token filter and permission cache (task 1.4) | — |
| Secrets | `JWT_SECRET` (≥ 32 characters) has no default: the application will not start without it (dev and test profiles carry throw-away values) | environment |

## Phase 1 — authorization plumbing decisions (task 1.4)
| Point | Behaviour |
|---|---|
| 401 codes | genuine but expired token, or `pv` older than the user's current `permission_version` ⇒ `TOKEN_EXPIRED` (the client refreshes silently); malformed, tampered, unknown or inactive user ⇒ `UNAUTHENTICATED` |
| Stale token on public endpoints | login, refresh, ping etc. ignore an expired or broken `Authorization` header instead of failing, so a client holding a stale token can still sign in |
| Forced password change | a user flagged `must_change_password` is refused even with a valid token (e.g. after an admin reset) |
| Cache | permissions are cached 60 s (`ims.security.access-cache-ttl`); task 1.5's admin APIs must evict (see DESIGN §7.4) so role changes and deactivation apply at once |
| Auditing columns | `created_by`/`updated_by` are now filled from the signed-in user; empty for login, scheduled and seed work |
| Matrix coverage | `PermissionMatrixIT` is generic but only the test probe and the six public auth endpoints exist today, so its value grows as 1.5–1.11 add protected endpoints |

## Phase 1 — administration decisions (task 1.5)
| Point | Behaviour |
|---|---|
| Package | `admin` (users, roles, permissions controllers and services) sits above `iam` (entities) and `auth`; avoids a package cycle |
| Reset password | not named in PLAN 1.5 but added: without it an Admin cannot recover a user who forgot their password when e-mail is unavailable |
| Deactivate | `DELETE /api/users/{id}` (the ARCHITECTURE §12.1 convention) plus `POST .../activate`; both bump `permission_version` and end sessions/evict the cache after commit |
| Role permissions | the permission catalogue is read-only over the API; `PERMISSION_MANAGE` therefore only gates reading it (together with `ROLE_MANAGE`) |
| ADMIN safeguards | the last active Admin cannot be demoted or deactivated; the ADMIN role must keep `ROLE_MANAGE`, `USER_VIEW`, `USER_UPDATE` |
| Users cannot deactivate themselves? | allowed unless they are the last Admin (no extra rule invented) |
| Reasons | optional on role changes, permission changes, password reset and deactivation; stored in the audit record |
| Audit | services publish `AuditCommand` events in their transaction; the persisting listener and `GET /api/audit-logs` are task 1.6. Until then these events (and the 1.3 `AuthEvent`s) are produced but not stored |
| First Admin | `IMS_BOOTSTRAP_ADMIN_EMAIL` + `IMS_BOOTSTRAP_ADMIN_PASSWORD` create it only on an empty `users` table. If every Admin is ever lost, recover with SQL (assign the ADMIN role) rather than an environment variable that could silently re-create accounts |
| Demo users | 14 users from DESIGN §11.2, created by `DemoUserLoader` (dev profile); `storeop1`'s MAIN-WH restriction arrives with warehouses in Phase 2 |
| Paging | `size` is capped at 100 for every list endpoint (`spring.data.web.pageable.max-page-size`); sort properties are whitelisted per endpoint |

## Phase 1 — audit decisions (task 1.6)
| Point | Behaviour |
|---|---|
| Two entry points | `@Audited` + `Auditable` for one-method-one-change; `AuditCommand`/`AuthEvent` events otherwise. Both end in `AuditService.record`, in the caller's transaction |
| Fail closed | if the audit insert fails, the operation fails (also for failed sign-ins: the response becomes a 500 rather than an unaudited 401) |
| Unknown usernames | stored only if they look like a username; otherwise `(invalid)` (could be a password typed in the wrong field). A password made only of letters, digits, dot, underscore and hyphen would still be stored, which the policy's mandatory symbol makes unlikely but not impossible |
| Masking | by key name (see DESIGN §4.11), not by value; do not put secrets in free-text `reason` |
| Truncation | `reason` 500, `username` 50, `roles` 200 characters etc.; never an error |
| Read API | plain SQL with bound parameters; counts scan every partition unless `from`/`to` narrow it; add keyset paging if the log grows into tens of millions of rows |
| Export | not in 1.6: ARCHITECTURE §12.2 lists `GET /api/audit-logs/export`; it belongs with the report/export work and must itself be audited |
| Retention | rows are kept permanently (REQUIREMENTS §7); no purge exists or is planned |
| ShedLock | not added; single instance today (DESIGN §4.12 note) |
| Gaps | master-data edits (1.8, 1.9) and setting changes (1.7) must use `@Audited`/`AuditCommand` when those tasks are built |

## Phase 1 — common services decisions (task 1.7)
| Point | Behaviour |
|---|---|
| Document numbers | taken with an atomic upsert in the caller's transaction (`MANDATORY`). Same-type creators serialise until commit; fine at this volume, revisit if a document type becomes hot (a gap-free guarantee and unlimited concurrency are incompatible) |
| Business year | numbering periods follow `ims.business-zone` (Asia/Kolkata), not UTC, so a document made at 00:30 on 1 January IST belongs to the new year |
| Settings package | `settings` (not `common/settings` as drawn in ARCHITECTURE §3): it depends on `audit` and `auth`, and `common` must not |
| Settings API | not named in PLAN 1.7 but added (`GET /api/settings`, `PUT /api/settings/{key}`, `SETTINGS_MANAGE`): the permission and the audit rule already existed and changes need a safe path. No settings screen is planned in Phase 1's frontend tasks |
| Unknown settings | cannot be changed through the API (no code reads them) but are listed read-only |
| Idempotency scope | success-only: a 4xx/5xx releases the key, so the client may fix and retry with it. Chosen over Stripe-style "store every response" because business-rule failures (insufficient stock) are state-dependent |
| Idempotency and permissions | see DESIGN §6.1: replay returns the caller's own earlier success even after losing a permission |
| Idempotent bodies | JSON up to 1 MiB; form posts and downloads are not supported (stream consumed to fingerprint it) |
| Filter order | corrected to rate limit -> JWT -> idempotency (task 1.4 had registered JWT first) |
| Spec builder | `Specs` covers search, equals, in, range and collection-element filters. Dotted paths through associations are deliberately not supported yet: add them with the first entity that needs one (task 1.8/1.9) |
| ShedLock | still not added (single instance); the purge job is idempotent so a double run is harmless |

## Phase 1 — master data decisions (task 1.8)
| Point | Behaviour |
|---|---|
| Codes | supplied by the user, upper case, immutable (they are referenced everywhere); not generated by `DocumentNumberService` because DESIGN §1.3 defines no number format for them |
| Material in use | decided by pluggable `MaterialUsageCheck` beans so master data never depends on inventory, purchasing or BOM modules. Today only "primary material of an active product" exists; **Phases 2, 3 and 4 must each register their check** (stock on hand, open POs, active BOMs) and Phase 2 must also report a unit-change blocker once ledger rows exist |
| Force flag | `?force=true` on `DELETE` confirms deactivation of an in-use material; no extra permission is required beyond `MASTERDATA_MANAGE` (Admin only in the seed) |
| Supplier/customer deactivation | never blocked (DESIGN §5.2: blocks new documents only); *new* documents must refuse inactive partners (Phases 3 and 5) |
| Supplier/customer read access | no `*_VIEW` permission exists for them, so reads use `SUPPLIER_MANAGE` or `PURCHASE_VIEW`, `CUSTOMER_MANAGE` or `SALES_VIEW`. Store Manager and Store Operator cannot read suppliers, which goods receipt (Phase 3) will need: grant `PURCHASE_VIEW` then |
| Who writes materials | only `MASTERDATA_MANAGE` (Admin) per ARCHITECTURE §10.3. Engineers and Store Managers see materials but cannot create them; revisit with the business if that is too strict |
| `status` column | suppliers and customers keep both `status` (DESIGN §2.3) and `active` (standard master-data column); the service keeps them equal |
| Customer-specific product specs | `customer_product_specs` (table exists) is not part of PLAN task 1.9 and is not built; add it when sales and dispatch need customer part numbers (Phase 5) |
| List performance | materials load the preferred supplier in the list query (entity graph); other lists have no associations |

## Phase 1 — product master decisions (task 1.9)
| Point | Behaviour |
|---|---|
| DESIGN §6.3 conflict | the create example put `endType` both as a column and in `specifications`, while the product form (§8.3) shows spring rate, max load and end type as catalogue-driven fields. Resolved: **the catalogue describes every attribute; where its code is a product column the value lives in the column (`storage: CORE`), otherwise in `specifications`**. The example is corrected |
| Draft vs active | required attributes are enforced at activation and when editing an ACTIVE product, not on create, so "Save draft" works. Everything else (types, ranges, enums, unknown keys, references) is checked on every save |
| Attribute sets assumed | Compression/Extension/Torsion come from REQUIREMENTS §4.2. Belleville uses decision #11's default (outer/inner diameter, thickness, free height, load at flat, stack count and arrangement). **Conical and Wire form are not specified anywhere and were assumed** (conical adds a small-end diameter; wire form has developed length, bends and a description): please have Engineering confirm. They are plain catalogue rows, so changing them needs a migration only |
| Custom springs | free-form scalar attributes (see DESIGN §6.3); definitions added later for CUSTOM still apply |
| Editing the catalogue | adding or changing definition rows is done with SQL or a migration; there is no admin API or screen for it yet (not in PLAN). Definitions are read on every product write and list, never cached, so a change applies at once |
| Number fidelity | type-specific numbers are stored in JSONB as the exact decimals sent (not binary floats); they are read back as JSON numbers |
| Relabelling | a definition may relabel a column for its type (extension: "Body outer diameter"; Belleville: "Free height" for `freeLength`) |
| Status | `DRAFT → ACTIVE ↔ OBSOLETE` through `activate` and `DELETE` only. Obsoleting does not yet check BOMs or open orders: Phase 4 must add a product-usage check like `MaterialUsageCheck` |
| Who | only `ENGINEER` holds `PRODUCT_CREATE`/`PRODUCT_UPDATE`; Admin can view but not edit products (ARCHITECTURE §10.3) |
| Frontend note (task 1.11) | the product form needs a customer dropdown, but Engineers hold neither `CUSTOMER_MANAGE` nor `SALES_VIEW`, so `GET /api/customers` is 403 for them. Decide in 1.11: grant Engineers `SALES_VIEW`, or add a lookup endpoint for active customers |
| Lists | products load material and customer in the list query; the summary omits the specifications |

## Phase 1 — frontend core decisions (task 1.10)
| Point | Behaviour |
|---|---|
| Directive name | `*appHasPermission` (the lint rule requires the `app` prefix) instead of DESIGN's `*hasPermission`; DESIGN §8.4 updated. Modes `any` (default) and `all` |
| Token refresh | reactive only: a 401 on a non-session call triggers one shared refresh and a retry; if it fails the user is sent to `/login?returnUrl=…` with one "session ended" toast. No proactive timer and no idle-timeout warning (the 7-day refresh cookie is absolute; add a warning later if wanted) |
| Session start | `APP_INITIALIZER` loads config, then tries the refresh cookie, so a page reload keeps the user signed in; the access token itself is memory-only |
| Forced password change | a second step on the login page (the server issues no session until the password is changed); username and current password are kept in memory only for that step |
| Return URL | only same-site paths are followed (`/x`, never `//x`); default `/dashboard` |
| Route guards | `authGuard`, `guestGuard`, `permissionGuard` (route `data.permission`, optional `permissionMode`); denied users land on `/forbidden`. UX only, the API still answers 403 |
| Menu | an item shows when enabled and the user holds its permission; groups with no visible items vanish. Users needs `USER_VIEW`, Roles needs `ROLE_MANAGE` |
| Admin screens | plain `mat-table` with server-side paging, sort and debounced search. The reusable `DataTable`, `FilterBar` and `StatusBadge` arrive in task 1.11 and these screens can adopt them then |
| Verification | lint, format, 55 unit tests and the production build pass. The stack was not run in a browser in this session |
