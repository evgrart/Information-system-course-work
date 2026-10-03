#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
PGHOST=${PGHOST:-pg}
PGDATABASE=${PGDATABASE:-studs}
DB_SCHEMA=${DB_SCHEMA:-s465826}
export PGHOST PGDATABASE
case "${1:-}" in
 create|seed|test|drop|explain) script="database/$1.sql" ;;
 *) echo 'Usage: sh scripts/db.sh create|seed|test|explain|drop' >&2; exit 2 ;;
esac
exec psql -X -w -v ON_ERROR_STOP=1 -v "schema=$DB_SCHEMA" -f "$script"
