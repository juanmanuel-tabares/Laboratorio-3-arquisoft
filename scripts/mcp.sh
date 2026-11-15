#!/usr/bin/env bash
# Cliente MCP mínimo con curl (transporte Streamable HTTP).
# Uso:
#   ./scripts/mcp.sh init                        -> initialize + notifications/initialized (guarda la sesión)
#   ./scripts/mcp.sh list                        -> tools/list
#   ./scripts/mcp.sh call <tool> '<json-args>'   -> tools/call
# Variable opcional: MCP_URL (por defecto http://localhost:8088/mcp)
set -euo pipefail

MCP_URL="${MCP_URL:-http://localhost:8088/mcp}"
SID_FILE="${TMPDIR:-/tmp}/mcp-session-id"
HDRS=(-H "Content-Type: application/json" -H "Accept: application/json, text/event-stream")

# Las respuestas pueden venir como JSON plano o como evento SSE ("data: {...}")
parse() { sed -n 's/^data://p; /^{/p' | head -1; }

post() {
  local sid
  sid="$(cat "$SID_FILE" 2>/dev/null || true)"
  curl -s -X POST "$MCP_URL" "${HDRS[@]}" ${sid:+-H "Mcp-Session-Id: $sid"} -d "$1"
}

case "${1:-}" in
  init)
    hdr="$(mktemp)"
    curl -s -D "$hdr" -X POST "$MCP_URL" "${HDRS[@]}" \
      -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}' | parse
    echo
    grep -i '^mcp-session-id' "$hdr" | awk '{print $2}' | tr -d '\r' > "$SID_FILE"
    rm -f "$hdr"
    echo "Mcp-Session-Id: $(cat "$SID_FILE")"
    post '{"jsonrpc":"2.0","method":"notifications/initialized"}' >/dev/null
    ;;
  list)
    post '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | parse
    ;;
  call)
    tool="$2"; args="${3:-{\}}"
    post "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"$tool\",\"arguments\":$args}}" | parse
    ;;
  *)
    echo "Uso: $0 init | list | call <tool> '<json-args>'" >&2; exit 1 ;;
esac
