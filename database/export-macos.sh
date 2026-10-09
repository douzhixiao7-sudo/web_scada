#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_FILE="${1:-$SCRIPT_DIR/web_scada_mvp.sql.gz}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-$(command -v mysqldump || true)}"
MYSQL_USER="${MYSQL_USER:-root}"

if [[ -z "$MYSQLDUMP_BIN" ]]; then
  echo "mysqldump not found. Install MySQL first: brew install mysql" >&2
  exit 1
fi

read -r -s -p "MySQL password for $MYSQL_USER: " MYSQL_PASSWORD
echo
TEMP_CONFIG="$(mktemp "${TMPDIR:-/tmp}/web-scada-mysql.XXXXXX")"
chmod 600 "$TEMP_CONFIG"
trap 'rm -f "$TEMP_CONFIG"' EXIT

escape_option() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

cat > "$TEMP_CONFIG" <<EOF
[client]
host=127.0.0.1
port=3306
user="$(escape_option "$MYSQL_USER")"
password="$(escape_option "$MYSQL_PASSWORD")"
default-character-set=utf8mb4
EOF
unset MYSQL_PASSWORD

"$MYSQLDUMP_BIN" --defaults-extra-file="$TEMP_CONFIG" --databases web_scada \
  --single-transaction --quick --triggers --hex-blob --no-tablespaces \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 | gzip -9 > "$OUTPUT_FILE"

if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$OUTPUT_FILE" > "$OUTPUT_FILE.sha256"
fi
echo "Database export created: $OUTPUT_FILE"
