#!/usr/bin/env bash
# Recorre un pedido completo contra el MCP Server del supermercado usando solo curl (JSON-RPC sobre
# Streamable HTTP), incluyendo los casos de error. Requiere el servidor activo en :8090.
# Uso: ./scripts/probar-servidor-mcp.sh [telefono-cliente-nuevo]
set -uo pipefail
EJ="$(cd "$(dirname "$0")/.." && pwd)"
LAB="$(cd "$EJ/.." && pwd)"
MCP="$LAB/scripts/mcp.sh"
export MCP_URL="http://localhost:8090/mcp"
TEL="${1:-3157778899}"

set -a; source "$LAB/.env"; set +a
export MYSQL_PWD="$PWD_UDEA"
my() { /usr/local/mysql/bin/mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P "$MYSQL_PORT" -u root "$@"; }

# Resume la respuesta de una herramienta en una línea legible
show() { python3 "$EJ/scripts/resumen_mcp.py"; }
call() { "$MCP" call "$@" | show; }
pid_de() { python3 -c 'import sys,json; print(json.loads(json.load(sys.stdin)["result"]["content"][0]["text"])["id"])'; }

echo "## Pruebas del MCP Server del supermercado con curl ($(date '+%Y-%m-%d %H:%M'))"
echo; echo "### initialize + tools/list"
"$MCP" init | head -1 | cut -c1-200
"$MCP" list | python3 -c 'import sys,json; t=json.load(sys.stdin)["result"]["tools"]; print("TOTAL", len(t), sorted(x["name"] for x in t))'

echo; echo "### listarCategorias";                       call listarCategorias '{}'
echo; echo "### buscarProductos texto='limon' (sin tilde)"; call buscarProductos '{"texto":"limon"}'
echo; echo "### buscarProductos categoria='Bebidas'";     call buscarProductos '{"categoria":"Bebidas"}'
echo; echo "### buscarCliente 3001234567 (registrada)";   call buscarCliente '{"telefono":"3001234567"}'
echo; echo "### buscarCliente $TEL (nuevo)";               call buscarCliente "{\"telefono\":\"$TEL\"}"
echo; echo "### registrarCliente con teléfono inválido";  call registrarCliente '{"nombre":"Carlos Ruiz","telefono":"12345","direccion":"Carrera 70 # 44-10, Medellín"}'
echo; echo "### registrarCliente";                         call registrarCliente "{\"nombre\":\"Carlos Ruiz\",\"telefono\":\"$TEL\",\"direccion\":\"Carrera 70 # 44-10, Medellín\"}"

echo; echo "### crearPedido"
PED=$("$MCP" call crearPedido "{\"telefono\":\"$TEL\"}"); echo "$PED" | show
PID=$(echo "$PED" | pid_de)
echo; echo "### crearPedido otra vez (reutiliza el carrito $PID)"; call crearPedido "{\"telefono\":\"$TEL\"}"

echo; echo "### agregarProducto: 2 Arroz (20), 1 Aceite (23), 1 Huevos (10)"
"$MCP" call agregarProducto "{\"pedidoId\":$PID,\"telefono\":\"$TEL\",\"productoId\":20,\"cantidad\":2}" >/dev/null
"$MCP" call agregarProducto "{\"pedidoId\":$PID,\"telefono\":\"$TEL\",\"productoId\":23,\"cantidad\":1}" >/dev/null
call agregarProducto "{\"pedidoId\":$PID,\"telefono\":\"$TEL\",\"productoId\":10,\"cantidad\":1}"
echo; echo "### agregarProducto Crema dental (agotada)";        call agregarProducto "{\"pedidoId\":$PID,\"telefono\":\"$TEL\",\"productoId\":35,\"cantidad\":1}"
echo; echo "### agregarProducto 16 Lomo de cerdo (stock 15)";  call agregarProducto "{\"pedidoId\":$PID,\"telefono\":\"$TEL\",\"productoId\":16,\"cantidad\":16}"
echo; echo "### actualizarCantidad Arroz -> 3";                call actualizarCantidad "{\"pedidoId\":$PID,\"telefono\":\"$TEL\",\"productoId\":20,\"cantidad\":3}"
echo; echo "### confirmarPedido con 'tarjeta de crédito'";     call confirmarPedido "{\"pedidoId\":$PID,\"telefono\":\"$TEL\",\"metodoPago\":\"tarjeta de crédito\"}"
echo; echo "### confirmarPedido EFECTIVO";                     call confirmarPedido "{\"pedidoId\":$PID,\"telefono\":\"$TEL\",\"metodoPago\":\"EFECTIVO\"}"
echo; echo "### agregarProducto a un pedido confirmado";       call agregarProducto "{\"pedidoId\":$PID,\"telefono\":\"$TEL\",\"productoId\":9,\"cantidad\":1}"

echo; echo "### IDOR: verPedido $PID con el celular de otro cliente";  call verPedido "{\"pedidoId\":$PID,\"telefono\":\"3001234567\"}"
echo; echo "### IDOR: agregarProducto al pedido $PID con el celular de otro cliente"; call agregarProducto "{\"pedidoId\":$PID,\"telefono\":\"3001234567\",\"productoId\":9,\"cantidad\":1}"

echo; echo "### MySQL tras confirmar (stock: Arroz -3, Aceite -1, Huevos -1)"
my -t -e "SELECT id, nombre, stock FROM supermercado.producto WHERE id IN (10,20,23);
          SELECT id, estado, metodo_pago, subtotal, costo_domicilio, total, entrega_estimada FROM supermercado.pedido WHERE id=$PID;
          SELECT producto_id, cantidad, precio_unitario, subtotal FROM supermercado.detalle_pedido WHERE pedido_id=$PID;"

echo; echo "### listarPedidosCliente 3001234567 (pedido histórico de Ana)"; call listarPedidosCliente '{"telefono":"3001234567"}'
echo; echo "### cancelarPedido con el teléfono de otro cliente"; call cancelarPedido "{\"pedidoId\":$PID,\"telefono\":\"3001234567\"}"
echo; echo "### cancelarPedido (dueño)";                        call cancelarPedido "{\"pedidoId\":$PID,\"telefono\":\"$TEL\"}"
echo; echo "### MySQL: stock repuesto tras cancelar"
my -t -e "SELECT id, nombre, stock FROM supermercado.producto WHERE id IN (10,20,23);"

echo; echo "### Exposición en red: POST a la IP de la LAN"
IP=$(ipconfig getifaddr en0 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}')
curl -s -o /dev/null -w "http://<IP-LAN>:8090/mcp -> HTTP %{http_code} (000 = conexión rechazada)\n" --max-time 3 \
  -X POST "http://$IP:8090/mcp" -H "Content-Type: application/json" -d '{}'
