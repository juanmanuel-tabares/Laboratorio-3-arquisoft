#!/usr/bin/env bash
# Arranca el MCP Server del supermercado en http://localhost:8090/mcp
set -euo pipefail
EJ="$(cd "$(dirname "$0")/.." && pwd)"
set -a; source "$EJ/../.env"; set +a
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
docker start tareas-mysql >/dev/null 2>&1 && echo "MySQL (Docker) en el puerto ${MYSQL_PORT:-3306}" || true
cd "$EJ/supermercado-mcp" && exec ./mvnw spring-boot:run
