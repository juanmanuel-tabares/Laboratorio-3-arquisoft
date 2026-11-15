#!/usr/bin/env bash
# (Re)crea la base "supermercado" con el esquema y el catálogo. Borra los pedidos existentes.
set -euo pipefail
EJ="$(cd "$(dirname "$0")/.." && pwd)"
set -a; source "$EJ/../.env"; set +a
export MYSQL_PWD="$PWD_UDEA"
MYSQL="${MYSQL_BIN:-$(command -v mysql || echo /usr/local/mysql/bin/mysql)}"
# --default-character-set=utf8mb4: sin esto los acentos del catálogo quedan corruptos.
"$MYSQL" --default-character-set=utf8mb4 -h 127.0.0.1 -P "${MYSQL_PORT:-3306}" -u root \
  < "$EJ/supermercado-mcp/src/main/resources/schema.sql"
echo "Base 'supermercado' recreada."
