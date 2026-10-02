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

### Admin user

Seeded by `V1__iam_platform.sql`:

| Username | Default password |
|----------|-----------------|
| `admin` | `Admin@123` ← **change immediately** |

Change via **Settings → Users → admin → Reset password** or via the API.

---

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

## 6. Backup and restore

### Automated backup (recommended: daily pg_dump)

```bash
# Dump (run from the DB container or via pgdump service)
docker exec ims-db pg_dump -U ims -d ims -Fc -n ims > ims_$(date +%Y%m%d_%H%M%S).dump

# Verify dump is readable
pg_restore --list ims_<date>.dump | head -20
```

### Restore

```bash
# 1. Stop the application
docker compose stop backend

# 2. Drop and recreate the schema
docker exec ims-db psql -U ims -d ims -c "DROP SCHEMA ims CASCADE; CREATE SCHEMA ims;"

# 3. Restore
docker exec -i ims-db pg_restore -U ims -d ims -n ims < ims_<date>.dump

# 4. Start (Flyway will validate; it should find all migrations already applied)
docker compose start backend
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
