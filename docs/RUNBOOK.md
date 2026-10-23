# Runbook — Spring Manufacturing IMS

Operations guide for deploying, upgrading, rolling back, and recovering the IMS platform.

---

## Prerequisites

| Tool | Min version | Notes |
|------|-------------|-------|
| Docker | 24+ | with Compose v2 plugin |
| Java | 17 | for local backend builds only |
| Node.js | 20+ | for local frontend builds only |
| PostgreSQL client | 14+ | `psql` for manual DB access |

---

## 1. First-time deploy

```bash
# 1. Copy and fill in secrets
cp docs/env.example docker/.env
# Edit docker/.env with real DB password, JWT secret, etc.

# 2. Start the full stack (builds images, runs migrations, starts services)
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml up --build -d

# 3. Verify
docker compose ps
curl -f http://localhost:8080/actuator/health
```

The backend applies Flyway migrations on startup. The first start creates all tables and seeds
standard reference data (UOMs, roles, permissions, warehouses, operations, rejection reasons).

### Documents and e-mail (R17)

| Variable | Default | Purpose |
|----------|---------|---------|
| `DOCUMENTS_STORAGE_ROOT` | `/data/documents` in the compose files (`./data/documents` otherwise) | Directory that holds uploaded document content. The compose files mount the named volume `documents` here. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` | `localhost`, `1025`, empty, empty | Outgoing mail server. Dev compose points `SMTP_HOST` at the `mailhog` container. |
| `NOTIFICATION_FROM` | `noreply@ims.local` | From address of notification e-mails. |

`docker/.env.example` is not updated by this release (the file is outside the paths the release agent could edit);
add the variables above to your `docker/.env` when you deploy.
Settings (Administration → System Settings): `documents.max_size_mb` (default 25), `documents.allowed_extensions`,
`notification.email.max_attempts` (default 5), `notification.email.retry_base_seconds` (default 60) and
`notification.alert.email_min_severity` (default `HIGH`).

### First administrator

A fresh installation has **no users**; nothing is seeded by migrations and there is no default password. On the first
start, with an empty `users` table, the application creates the initial administrator from the environment:

| Variable | Meaning |
|----------|---------|
| `IMS_BOOTSTRAP_ADMIN_USERNAME` | username of the first administrator |
| `IMS_BOOTSTRAP_ADMIN_PASSWORD` | its password; must satisfy the password policy (length, upper and lower case, digit, special character, not containing the username) |
| `IMS_BOOTSTRAP_ADMIN_EMAIL` | optional; defaults to `<username>@localhost.localdomain` |

The password is stored as a BCrypt hash and never logged; the user holds the `ADMIN` role (every permission) and is
flagged must-change-password. If the variables are unset the application starts anyway and logs one line naming them.
Once any user exists the variables are ignored, so remove them from the environment after the first start.
`docker/.env.example` is not updated by this change (outside the paths the agent could edit); add the variables to your
`docker/.env` for the first start.

In the `dev`/`demo` profiles the demo loader (`ims.demo.load-data=true`) creates `admin` / `Admin@123` instead, plus the
DESIGN section 11.2 role users (`engineer1`, `prodmgr1`, `purchase1`, `storemgr1`, `quality1`, `sales1`, `finance1`, ...)
with the initial password `Demo@12345!` and a forced change at first sign-in. None of these accounts exist in production.

The first sign-in is held to the password-change screen: while a password is temporary the server refuses every call
except change-password, me, refresh and logout (403 `PASSWORD_CHANGE_REQUIRED`), so choose a new password first.

Reset another user's password later via **Administration → Users → (the user) → Reset password** (needs the
`USER_PASSWORD_RESET` permission, which only ADMIN holds). The system can generate a one-time password and shows it
once; the user must change it at their next sign-in and every session they had ends. You cannot reset your own
password this way: use **Change password** in the user menu. A locked account is released with **Unlock** on the same
screen. At least one active, unlocked administrator must always remain, so the last administrator cannot be
deactivated or have the Administrator role removed; keep a second administrator in case the first is locked out.

---

### Second approver (first-run step)

The system refuses self-approval: whoever submits a purchase order, creates or confirms a sales order, prepares a
journal, initiates a payroll run or enters an AP invoice that needs a variance approval cannot approve it, and holding
the permission (the Administrator role included) does not change that (see ARCHITECTURE section 10.2a for the full
matrix; a dispatch has no approval step). A one-administrator installation therefore cannot complete its first purchase
order, sales order, journal approval or payroll run.
After the first sign-in, create at least one more user under **Admin > Users** and give it the role that holds the
approval permission (for example `PURCHASE_MANAGER`, or a second `ADMIN`), then change roles as your organisation requires.
Keep a second administrator as well: failed sign-ins can lock the only one, and only an administrator can unlock.

## 2. Upgrade (rolling)

```bash
# 1. Pull new images / rebuild
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml pull

# 2. Apply (Flyway runs the new migration automatically on startup)
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml up -d --no-deps backend

# 3. Check logs for migration success
docker compose logs -f backend | grep -E "Flyway|Successfully applied|ERROR"
```

**Never edit an applied Flyway migration.** New schema changes always go in a new `V{n+1}__*.sql`.

---

## 3. Rollback

IMS uses a ledger-based inventory model (append-only). Schema rollbacks are risky. Prefer:

1. **Forward fix**: deploy a new migration that undoes the problematic schema change.
2. **Feature flag**: disable the broken feature via a system setting until fixed.
3. **Last resort — restore from backup** (see §6).

To roll back the application binary only (not the schema):

```bash
# Deploy the previous image tag
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml \
  up -d --no-deps backend --no-build
# (set IMAGE_TAG= in docker/.env to the previous tag before running)
```

---

## 4. Health checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Database connectivity check
docker exec ims-db psql -U ims -d ims -c "SELECT count(*) FROM ims.users;"

# ShedLock table (scheduler health)
docker exec ims-db psql -U ims -d ims -c "SELECT name, lock_until FROM ims.shedlock;"

# Check pending Flyway migrations
docker exec ims-backend java -jar /app/app.jar --spring.flyway.dry-run=true 2>&1 | grep "migrate"
```

---

## 5. Common operational tasks

### Refresh materialized views (insight dashboards)

Materialized views (`mv_production_monthly`, `mv_inventory_value_monthly`) are refreshed nightly
by `AlertSweepJob`. Force a manual refresh:

```sql
SET search_path TO ims;
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_production_monthly;
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_inventory_value_monthly;
```

### Clear stale ShedLock entries

If a node crashed while holding a lock:

```sql
SET search_path TO ims;
DELETE FROM shedlock WHERE lock_until < NOW() - INTERVAL '1 hour';
```

### Expire stale FG reservations manually

The `ReservationExpiryJob` runs hourly. To force immediate expiry:

```bash
curl -X POST http://localhost:8080/actuator/scheduledtasks
# (or trigger via the maintenance screen in the UI)
```

### View audit trail for a transaction

```sql
SET search_path TO ims;
SELECT action, entity_type, entity_id, old_value, new_value, created_at, created_by
FROM audit_logs
WHERE entity_type = 'PRODUCTION_ORDER'
  AND entity_id = '<id>'
ORDER BY created_at;
```

---

## 6. Backup and restore (automated — NFR-005 / R19-7.4)

### RTO / RPO targets

| Metric | Target | How met |
|--------|--------|---------|
| RPO (Recovery Point Objective) | **< 1 hour** | WAL archiving with `archive_timeout=3600` flushes a segment at most every hour; every committed transaction is captured. |
| RTO (Recovery Time Objective) | **< 4 hours** | Restore from latest pg_dump (~20 min for a typical IMS database) + WAL replay to point-in-time (~10 min) ≈ well under 4 hours. |

### Enabling automated backup

The backup overlay adds a `pgbackup` service (daily cron pg_dump at 02:00) and enables PostgreSQL WAL archiving:

```bash
# Start the full stack with automated backup:
docker compose \
  -f docker/docker-compose.yml \
  -f docker/docker-compose.backup.yml \
  [--env-file docker/.env] \
  up -d --build
```

This creates two extra Docker volumes:
- `ims_backups` — daily pg_dump files; 7-day rolling retention
- `ims_wal_archive` — WAL segment files; retain at least as long as the oldest backup you want to recover from

### What is backed up

| Item | Mechanism | Location |
|------|-----------|----------|
| Database schema + data | `pg_dump -n ims -Fc` (daily at 02:00) | `ims_backups` volume |
| WAL segments | PostgreSQL `archive_command` (every ≤ 1 hour) | `ims_wal_archive` volume |
| Document files | Manual `tar` of `ims_documents` volume (see below) | Separate backup store |

### Manual on-demand backup

```bash
# Trigger a backup immediately (without waiting for 02:00 cron):
docker compose exec pgbackup sh /usr/local/bin/backup.sh

# Or dump directly from the DB container:
docker exec ims-postgres pg_dump -U ims -d ims -n ims -Fc \
  > ims_$(date +%Y%m%d_%H%M%S).dump

# Verify dump is readable:
pg_restore --list ims_<date>.dump | head -20
```

### Restore procedure

```bash
# 1. Stop the application (keeps the DB container running)
docker compose stop backend

# 2. Restore from dump using the restore script:
docker compose run --rm \
  -e DB_NAME=ims -e DB_USER=ims -e PGPASSWORD=<password> \
  -e PGHOST=postgres \
  -v ims_backups:/backups \
  -v ./docker/backup/restore.sh:/restore.sh:ro \
  postgres:16-alpine \
  sh /restore.sh /backups/ims_<TIMESTAMP>.dump

# 3. (Optional) Apply WAL segments to recover transactions after the dump:
#    Copy WAL files from the wal_archive volume and use PostgreSQL point-in-time recovery.
#    See the PostgreSQL docs on 'recovery_target_time' in postgresql.conf.

# 4. Restart the application (Flyway validates; all migrations should already be present):
docker compose start backend

# 5. Verify:
curl -f http://localhost:8080/actuator/health/readiness
```

### Monthly restore drill

Run `docker/backup/restore-test.sh` monthly to validate that the latest backup is restorable. The script:
1. Finds the most recent `.dump` file
2. Spins up a disposable PostgreSQL container
3. Restores the dump and counts tables in the `ims` schema
4. Prints PASS / FAIL and cleans up the container

```bash
# Run from the Docker host, pointing at the backups volume mount:
BACKUP_DIR=/var/lib/docker/volumes/ims_backups/_data \
  docker/backup/restore-test.sh
```

A CI/CD equivalent is `BackupRestoreIT` in the backend test suite — it runs the pg_dump → pg_restore round-trip on the Testcontainers PostgreSQL on every `mvn verify` run, proving the mechanism works automatically.

### Document files (not in the database dump)

Document **content** is not stored in PostgreSQL. The database holds metadata and a storage key per version
(`document_versions.storage_key`); the bytes live in the `documents` volume (`DOCUMENTS_STORAGE_ROOT`). A database dump
alone cannot restore downloads: back the volume up together with the dump and restore both to the same point in time.
A key whose file is missing answers `404` on download.

```bash
# Back up the documents volume (files are written once and never modified — a consistent copy is safe while running):
docker run --rm -v ims_documents:/data -v "$PWD":/backup \
  alpine tar czf /backup/documents_$(date +%Y%m%d).tgz -C /data .

# Restore:
docker run --rm -v ims_documents:/data -v "$PWD":/backup \
  alpine tar xzf /backup/documents_<date>.tgz -C /data
```

---

## 7. Monitoring alerts

| Alert | Cause | Resolution |
|-------|-------|-----------|
| `LOW_STOCK` | Material balance ≤ reorder level | Raise a Purchase Requisition |
| `OVERDUE_PRODUCTION_ORDER` | Production order past expected completion date | Investigate delay; reassign or extend |
| `OVERDUE_PO` | PO past expected delivery date | Chase supplier; update delivery date or cancel |
| `QUALITY_HOLD` | FG batch placed on hold | Quality Manager must review and decide |
| `PENDING_INSPECTION_OVERDUE` | Incoming inspection open > 48 h | Assign inspector; escalate |

Alerts are visible in **Insight → Alerts** and polled every 15 minutes by `AlertSweepJob`.

---

## 8. Secrets rotation

1. Generate a new value (e.g., `openssl rand -hex 32` for JWT).
2. Update in your secrets manager / `docker/.env`.
3. Restart the backend: `docker compose restart backend`.
4. JWT: existing tokens expire at `access-token-minutes` (15 min default); no forced logout needed for short-lived tokens.
5. Database password: requires a PostgreSQL `ALTER ROLE` command and coordinated restart.

---

## 9. Troubleshooting quick reference

| Symptom | Check |
|---------|-------|
| 500 on all endpoints | `docker compose logs backend` — likely DB connection or migration failure |
| Flyway "checksum mismatch" | Someone edited an applied migration — restore the original or baseline |
| "Insufficient stock" on material issue | Check `inventory_balances` for AVAILABLE batches; may need inspection to pass |
| ShedLock job not running | Check if the lock row is stuck (`lock_until` in the past) — delete the row |
| JWT expired errors | Verify `JWT_SECRET` hasn't changed; check system clock sync |
| Notification e-mails not arriving | Administration → Notification Admin → Delivery log, filter `FAILED` or `PENDING`. `last_error` names the failure (no credentials are logged). A `PENDING` row is retried with back-off (60 s, 120 s, ... capped at 1 h) until `notification.email.max_attempts`, then becomes `FAILED` and is not retried by itself. Fix SMTP, then re-create the notification or set the row back to `PENDING` with `next_attempt_at = now()`. |
| Document download answers 404 | The file is missing from `DOCUMENTS_STORAGE_ROOT` (volume not mounted, or restored without the files). See "Document files" in section 6. |
| Upload answers 413 or 415 | 413: larger than `documents.max_size_mb`. 415: extension not in `documents.allowed_extensions`, or the content does not match the extension (a `.pdf` that is not a PDF). |

## 10. Advanced manufacturing (R18): settings and jobs

| Item | What it does | Where |
|---|---|---|
| `mfg.calendar.shift_codes` | Comma-separated shift codes (see HR shifts) that make up planned production time for OEE and the working windows of the scheduler. Default `GENERAL`. Weekly offs and the time zone come from `hrm.weekly_off_days` and `hrm.timezone`. | Admin settings |
| `mfg.schedule.default_horizon_days` | Planning horizon of an auto-schedule run when none is given (1 to 90, default 14). | Admin settings |
| `OeeViewRefreshJob` | Refreshes the materialized view `mv_oee_daily` every 15 minutes (ShedLock). The OEE trend chart reads it, so it can be up to 15 minutes behind; the other OEE figures are live. If a node restarts, the next run refreshes it. To refresh at once: `REFRESH MATERIALIZED VIEW CONCURRENTLY ims.mv_oee_daily;` | Scheduled job |
| Maintenance labour cost | When a maintenance request is completed its labour is posted to the GL after the completion commits. If posting fails (for example the fiscal period is closed) the request stays completed and a `MAINTENANCE_COST_UNPOSTED` alert is raised. Fix the cause, then `POST /api/maintenance/requests/{id}/post-cost` for one request or `POST /api/maintenance/costs/repost-unposted` for all (permission `MAINTENANCE_COST_POST`). `GET /api/maintenance/costs/unposted` lists them. | Alerts, API |
| Machine cost centre | Maintenance cost is charged to `machines.cost_centre_id`; machines created before R18 were set to `CC-PROD-01`. Set it with `POST /api/machines` (optional `costCentreId`). Without one, the cost centre in setting `controlling.production_cost_centre_id` is used. | API |
| Spare part stock | Spare parts are materials of type `SPARE_PART`. Receive them with a normal purchase order and goods receipt; the low-stock sweep and MRP safety stock use the minimum stock set on the part. | Purchasing |

Scheduling notes: running the scheduler (`POST /api/scheduling/run`) with `dryRun=true` shows the proposal without saving anything. A full run replans every pending operation of the chosen orders; an operation keeps the machine it already has. After deploying R18, existing released orders have no scheduled windows until the first run.

## Analytics and integration (R19)

New environment variables: `IMS_INTEGRATION_SECRET_KEY` (32 random bytes, base64; required to create webhooks; losing it makes stored webhook secrets unreadable, so rotate them), `IMS_AI_FORECAST_URL` and `IMS_AI_FORECAST_API_KEY` (optional).
Health: `/actuator/health/readiness` covers the database only; the `integration` indicator in `/actuator/health` shows the webhook backlog and never takes the application out of service. Alert an external monitor on readiness failures and on `WEBHOOK_DELIVERY_FAILED` alerts.
BI tools: create a login role that is a member of `ims_reporting` (`CREATE ROLE bi LOGIN PASSWORD '...' IN ROLE ims_reporting;`) and point the tool at schema `reporting`.

## Industry verticals (R20)

A default deployment is the spring manufacturer: seven spring product types and the spring operations and rejection reasons, nothing else. Other industries are opt-in packs.

- **Enable a vertical:** set `IMS_VERTICALS` (or `ims.verticals`) to a comma-separated list, for example `IMS_VERTICALS=automotive,furniture` or `IMS_VERTICALS=electronics`, then restart. Flyway applies that vertical's seed on startup: a product type, its attributes, sample operations and rejection reasons. Available packs: `automotive`, `furniture`, `electronics`. `docker/.env.example` does not list the variable yet, so add it to your own `.env` and pass it through the compose file's `environment` section.
- **Bad values stop the start:** an unknown or malformed name (for example `aerospace` or `../x`) fails startup with a message naming it, so a typo cannot silently enable nothing.
- **Disabling again:** remove the name and restart. The application still starts; the seeded rows stay (disable them in the admin screens, or deactivate the product type, rather than deleting rows that products may reference). Re-enabling is a no-op for rows that exist.
- **Before using a pack:** the operation standard times in each pack are illustrative. Set your own on the Operations screen. Rejection reasons and attributes can be edited or deactivated the same way; attribute definitions are managed under Admin, Attribute Definitions (permission `ATTRIBUTE_MANAGE`, ADMIN by default).
- **Backups:** the pack's rows live in the normal database, so the normal backup covers them.

## Payroll rule pack (R20)

Setting `hrm.payroll.rule_pack` (default `IN`) selects the statutory payroll rules. India is the only pack; an unknown value makes payroll calculation fail with a message listing the available packs instead of falling back silently. The pack used is recorded in the payroll-calculate audit entry.
