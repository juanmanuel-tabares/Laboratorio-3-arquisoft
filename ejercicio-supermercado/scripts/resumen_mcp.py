"""Resume en una línea la respuesta JSON-RPC de un tools/call (lee la respuesta por stdin)."""
import json
import sys


def pedido(p):
    items = [(i["producto"], i["cantidad"], i["subtotal"]) for i in p["items"]]
    linea = (f"pedido {p['id']} {p['estado']} | items={items}"
             f" | subtotal={p['subtotal']} domicilio={p['costoDomicilio']} total={p['total']}"
             f" | faltaMinimo={p['faltaParaPedidoMinimo']} faltaGratis={p['faltaParaDomicilioGratis']}")
    if p.get("metodoPago"):
        linea += f" | pago={p['metodoPago']} entrega={p['entregaEstimada']}"
    return linea


respuesta = json.load(sys.stdin)
resultado = respuesta.get("result", respuesta)
if "content" not in resultado:
    print(json.dumps(respuesta)[:600])
    sys.exit()

texto = resultado["content"][0]["text"]
if resultado.get("isError"):
    print("[isError]", texto)
    sys.exit()

try:
    datos = json.loads(texto)
except ValueError:
    print(texto)
    sys.exit()

if isinstance(datos, dict) and "items" in datos:
    print(pedido(datos))
elif isinstance(datos, list) and datos and isinstance(datos[0], dict) and "items" in datos[0]:
    for p in datos:
        print(pedido(p))
else:
    print(json.dumps(datos, ensure_ascii=False)[:700])
