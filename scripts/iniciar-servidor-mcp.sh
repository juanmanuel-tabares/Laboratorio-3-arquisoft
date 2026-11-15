#!/usr/bin/env bash
# Arranca MySQL (contenedor Docker) y el MCP Server Spring Boot en http://localhost:8088/mcp
set -euo pipefail
LAB="$(cd "$(dirname "$0")/.." && pwd)"
set -a; source "$LAB/.env"; set +a
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
docker start tareas-mysql >/dev/null && echo "MySQL (Docker) en puerto ${MYSQL_PORT}"
cd "$LAB/tarea-mcp" && exec ./mvnw spring-boot:run
