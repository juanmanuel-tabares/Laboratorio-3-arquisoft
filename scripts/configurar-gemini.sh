#!/usr/bin/env bash
# Guarda GOOGLE_API_KEY en lab3/.env sin mostrarla en pantalla ni en el historial.
set -euo pipefail
ENV_FILE="$(cd "$(dirname "$0")/.." && pwd)/.env"

read -r -s -p "Pega tu GOOGLE_API_KEY (no se mostrará) y presiona Enter: " KEY
echo
if [ -z "$KEY" ]; then
  echo "No se ingresó ninguna clave." >&2
  exit 1
fi

touch "$ENV_FILE"
grep -v '^GOOGLE_API_KEY=' "$ENV_FILE" > "$ENV_FILE.tmp" || true
printf 'GOOGLE_API_KEY=%s\n' "$KEY" >> "$ENV_FILE.tmp"
mv "$ENV_FILE.tmp" "$ENV_FILE"
chmod 600 "$ENV_FILE"
echo "Listo: GOOGLE_API_KEY guardada en $ENV_FILE"
