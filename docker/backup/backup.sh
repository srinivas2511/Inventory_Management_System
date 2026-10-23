#!/usr/bin/env sh
# Daily pg_dump backup script — NFR-005 / R19-7.4
# Dumps the 'ims' schema in custom format (-Fc), retains KEEP_DAYS rolling backups.
#
# Environment (all optional — override in the compose service env section):
#   BACKUP_DIR   destination directory  (default: /backups)
#   DB_NAME      database name          (default: ims)
#   DB_USER      postgres role          (default: ims)
#   KEEP_DAYS    retention window       (default: 7)
#   PGHOST       postgres host          (default: postgres)
#   PGPASSWORD   postgres password      (must be set for unattended use)
set -eu

BACKUP_DIR="${BACKUP_DIR:-/backups}"
DB_NAME="${DB_NAME:-ims}"
DB_USER="${DB_USER:-ims}"
KEEP_DAYS="${KEEP_DAYS:-7}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTFILE="$BACKUP_DIR/ims_${TIMESTAMP}.dump"

mkdir -p "$BACKUP_DIR"

echo "[$(date -Iseconds)] Starting pg_dump → $OUTFILE"
pg_dump -U "$DB_USER" -d "$DB_NAME" -n ims -Fc -f "$OUTFILE"
SIZE=$(du -sh "$OUTFILE" | cut -f1)
echo "[$(date -Iseconds)] Dump complete: $SIZE"

# Rotate: remove backups older than KEEP_DAYS days
OLD=$(find "$BACKUP_DIR" -name "ims_*.dump" -mtime "+${KEEP_DAYS}" 2>/dev/null | wc -l)
find "$BACKUP_DIR" -name "ims_*.dump" -mtime "+${KEEP_DAYS}" -delete
echo "[$(date -Iseconds)] Rotation: removed $OLD file(s) older than ${KEEP_DAYS} days"
