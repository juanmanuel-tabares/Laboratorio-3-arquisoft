#!/usr/bin/env bash
# Agrega el servidor "tareas-mcp" (vía mcp-remote) a claude_desktop_config.json.
# IMPORTANTE: ejecútalo con Claude Desktop CERRADO (⌘Q). Si la app está abierta,
# reescribe el archivo con su copia en memoria y borra el cambio.
set -euo pipefail

if pgrep -f "Claude.app/Contents/MacOS/Claude" >/dev/null; then
  echo "Claude Desktop sigue abierto. Ciérralo por completo (⌘Q) y vuelve a ejecutar este script." >&2
  exit 1
fi

CFG="$HOME/Library/Application Support/Claude/claude_desktop_config.json"
NODEBIN="$(dirname "$(command -v npx 2>/dev/null || echo "$HOME/.nvm/versions/node/v22.20.0/bin/npx")")"

[ -f "$CFG" ] || echo '{}' > "$CFG"
cp -p "$CFG" "$CFG.bak-lab3-$(date +%Y%m%d%H%M%S)"

python3 - "$CFG" "$NODEBIN" <<'EOF'
import json, sys
path, nodebin = sys.argv[1], sys.argv[2]
cfg = json.load(open(path))
cfg.setdefault("mcpServers", {})["tareas-mcp"] = {
    "command": f"{nodebin}/npx",
    "args": ["-y", "mcp-remote@0.16.6", "http://localhost:8088/mcp"],
    "env": {"PATH": f"{nodebin}:/usr/local/bin:/usr/bin:/bin"},
}
json.dump(cfg, open(path, "w"), indent=2, ensure_ascii=False)
print("tareas-mcp agregado a", path)
print(json.dumps(cfg["mcpServers"]["tareas-mcp"], indent=2))
EOF
echo "Ahora arranca el servidor MCP (scripts/iniciar-servidor-mcp.sh) y vuelve a abrir Claude Desktop."
