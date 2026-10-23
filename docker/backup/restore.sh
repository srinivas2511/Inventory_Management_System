#!/usr/bin/env sh
# Restore an IMS database from a pg_dump backup — NFR-005 / R19-7.4
#
# Usage:
#   restore.sh <dump_file>
#
# Environment:
#   DB_NAME     target database name  (default: ims)
#   DB_USER     postgres role         (default: ims)
#   PGHOST      postgres host         (default: postgres)
#   PGPASSWORD  postgres password
#
# WARNING: drops the 'ims' schema in the target database before restoring.
#          Always test on a non-production database first (see restore-test.sh).
set -eu

DUMP_FILE="${1:-}"
DB_NAME="${DB_NAME:-ims}"
DB_USER="${DB_USER:-ims}"

if [ -z "$DUMP_FILE" ]; then
    echo "Usage: $0 <dump_file>"
    exit 1
fi
if [ ! -f "$DUMP_FILE" ]; then
    echo "ERROR: dump file not found: $DUMP_FILE"
    exit 1
fi

echo "[$(date -Iseconds)] Restoring $(basename "$DUMP_FILE") into database '$DB_NAME' on ${PGHOST:-localhost}"

# Drop and recreate the ims schema so the restore starts from a clean state
psql -U "$DB_USER" -d "$DB_NAME" \
    -c "DROP SCHEMA IF EXISTS ims CASCADE; CREATE SCHEMA ims;"

# Restore; --no-owner and --no-acl make the restore owner-independent
pg_restore -U "$DB_USER" -d "$DB_NAME" --no-owner --no-acl "$DUMP_FILE"

# Sanity check: the ims schema must contain at least 50 tables
TABLE_COUNT=$(psql -U "$DB_USER" -d "$DB_NAME" -t -A \
    -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'ims';")
echo "[$(date -Iseconds)] Restored $TABLE_COUNT table(s) in schema 'ims'"

if [ "$TABLE_COUNT" -lt 50 ]; then
    echo "ERROR: Restore validation failed — expected >= 50 tables, got $TABLE_COUNT"
    exit 2
fi
echo "[$(date -Iseconds)] Restore validation PASSED."
