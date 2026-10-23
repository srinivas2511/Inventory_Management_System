# docker/CLAUDE.md

Infrastructure files for the ERP platform stack.

## Compose files
| File | Purpose |
|------|---------|
| `docker-compose.yml` | Base services: `postgres` (port 5432), `backend` (:8080), `frontend` (:8081 via nginx) |
| `docker-compose.dev.yml` | Dev overrides: volume mounts, hot-reload, debug port 5005 |
| `docker-compose.prod.yml` | Production overrides: resource limits, restart policies |
| `docker-compose.backup.yml` | Backup overlay: `pgbackup` cron service + WAL archiving volumes |

## Ports (default)
| Service | Host port |
|---------|-----------|
| PostgreSQL | 5432 |
| Backend (Spring Boot) | 8080 |
| Frontend (nginx) | 8081 |

## Starting the stack
```bash
# Development (rebuild images)
docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up --build

# Production
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml up -d

# With backup service
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml \
               -f docker/docker-compose.backup.yml up -d
```

## nginx.conf
Serves the Angular SPA. Key location blocks:
- `/` — English build (root), SPA fallback `try_files … /index.html`
- `/hi/` — Hindi build, fallback `try_files … /hi/index.html`
- `/kn/` — Kannada build, fallback `try_files … /kn/index.html`
- `/api/` — proxy pass to backend `:8080`

## Backup (NFR-005)
`backup/backup.sh` — daily `pg_dump -n ims -Fc`, 7-day rolling retention (`KEEP_DAYS`).
`backup/restore.sh` — drops/recreates `ims` schema, runs `pg_restore`, validates ≥ 50 tables.
`backup/restore-test.sh` — monthly drill: disposable Postgres container, restore, validate, cleanup.
WAL archiving: `wal_level=replica`, `archive_mode=on`, `archive_timeout=3600` (RPO < 1 h).
Named volumes: `ims_backups`, `ims_wal_archive`.
See `docs/RUNBOOK.md §6` for enable-backup instructions and full restore procedure.

## Environment variables
Copy `docs/env.example` to `.env` at the repo root before first run.
Secrets (DB password, JWT secret, bootstrap admin credentials) come from `.env` only — never hardcoded.
