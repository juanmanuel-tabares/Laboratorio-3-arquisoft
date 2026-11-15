#!/usr/bin/env bash
# Arranca el ChatBot en http://localhost:8091 (requiere el MCP Server activo en :8090 y GOOGLE_API_KEY en .env)
# Opcional: GEMINI_MODEL=otro-modelo ./scripts/iniciar-chatbot.sh
set -euo pipefail
EJ="$(cd "$(dirname "$0")/.." && pwd)"
set -a; source "$EJ/../.env"; set +a
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
if [ -z "${GOOGLE_API_KEY:-}" ]; then
  echo "Falta GOOGLE_API_KEY en $EJ/../.env (usa ../scripts/configurar-gemini.sh)" >&2
  exit 1
fi
if ! curl -s -o /dev/null --max-time 3 -X POST http://localhost:8090/mcp -H 'Content-Type: application/json' -d '{}'; then
  echo "El MCP Server no responde en :8090. Arráncalo primero con scripts/iniciar-supermercado-mcp.sh" >&2
  exit 1
fi
cd "$EJ/chatbot-supermercado" && exec ./mvnw spring-boot:run
