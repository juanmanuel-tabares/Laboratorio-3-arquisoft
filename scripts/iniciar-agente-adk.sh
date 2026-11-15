#!/usr/bin/env bash
# Arranca la interfaz web de Google ADK en http://localhost:8080 (requiere el MCP Server activo en 8088)
set -euo pipefail
LAB="$(cd "$(dirname "$0")/.." && pwd)"
set -a; source "$LAB/.env"; set +a
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
if [ -z "${GOOGLE_API_KEY:-}" ]; then
  echo "AVISO: GOOGLE_API_KEY no está definida en $LAB/.env; el agente cargará, pero Gemini no responderá." >&2
fi
cd "$LAB/agente-mcp" && ./mvnw -q clean install -DskipTests && exec ./mvnw exec:java@runAdkWebServer
