#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INPUT_FILE="${1:-$SCRIPT_DIR/web_scada_mvp.sql.gz}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
MYSQL_USER="${MYSQL_USER:-root}"

if [[ -z "$MYSQL_BIN" ]]; then
  echo "mysql client not found. Install MySQL first: brew install mysql" >&2
  exit 1
fi
if [[ ! -f "$INPUT_FILE" ]]; then
  echo "Database snapshot not found: $INPUT_FILE" >&2
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

if [[ "$INPUT_FILE" == *.gz ]]; then
  gzip -dc "$INPUT_FILE" | "$MYSQL_BIN" --defaults-extra-file="$TEMP_CONFIG" --show-warnings
else
  "$MYSQL_BIN" --defaults-extra-file="$TEMP_CONFIG" --show-warnings < "$INPUT_FILE"
fi

echo "Database web_scada imported successfully."
