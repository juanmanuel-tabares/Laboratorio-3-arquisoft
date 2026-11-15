## Pruebas del MCP Server del supermercado con curl (2026-11-13 02:09)

### initialize + tools/list
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{"completions":{},"logging":{},"prompts":{"listChanged":true},"resources":{"subscribe":false,"listChanged":true},"tools"
TOTAL 11 ['actualizarCantidad', 'agregarProducto', 'buscarCliente', 'buscarProductos', 'cancelarPedido', 'confirmarPedido', 'crearPedido', 'listarCategorias', 'listarPedidosCliente', 'registrarCliente', 'verPedido']

### listarCategorias
["Aseo y hogar", "Bebidas", "Carnes y pollo", "Despensa", "Frutas y verduras", "Lácteos y huevos", "Panadería"]

### buscarProductos texto='limon' (sin tilde)
[{"id": 3, "nombre": "Limón Tahití", "categoria": "Frutas y verduras", "presentacion": "1 kg", "precio": 5200, "stock": 60, "disponible": true}]

### buscarProductos categoria='Bebidas'
[{"id": 29, "nombre": "Agua sin gas", "categoria": "Bebidas", "presentacion": "Botella 600 ml", "precio": 2200, "stock": 100, "disponible": true}, {"id": 30, "nombre": "Gaseosa cola", "categoria": "Bebidas", "presentacion": "Botella 1,5 L", "precio": 6800, "stock": 60, "disponible": true}, {"id": 31, "nombre": "Jugo de naranja", "categoria": "Bebidas", "presentacion": "Botella 1 L", "precio": 7900, "stock": 30, "disponible": true}]

### buscarCliente 3001234567 (registrada)
{"registrado": true, "cliente": {"id": 1, "nombre": "Ana Gómez", "telefono": "3001234567", "direccion": "Calle 50 # 40-20, Medellín"}, "mensaje": "Cliente registrado"}

### buscarCliente 3151112233 (nuevo)
{"registrado": false, "cliente": null, "mensaje": "No hay un cliente con el teléfono 3151112233. Pide su nombre y dirección para registrarlo."}

### registrarCliente con teléfono inválido
[isError] El teléfono '12345' no es válido: debe ser un celular colombiano de 10 dígitos que empiece por 3

### registrarCliente
{"id": 4, "nombre": "Carlos Ruiz", "telefono": "3151112233", "direccion": "Carrera 70 # 44-10, Medellín"}

### crearPedido
pedido 5 BORRADOR | items=[] | subtotal=0 domicilio=0 total=0 | faltaMinimo=20000 faltaGratis=100000

### crearPedido otra vez (reutiliza el carrito 5)
pedido 5 BORRADOR | items=[] | subtotal=0 domicilio=0 total=0 | faltaMinimo=20000 faltaGratis=100000

### agregarProducto: 2 Arroz (20), 1 Aceite (23), 1 Huevos (10)
pedido 5 BORRADOR | items=[('Arroz blanco', 2, 10800), ('Aceite de girasol', 1, 12900), ('Huevos AA', 1, 19900)] | subtotal=43600 domicilio=6000 total=49600 | faltaMinimo=0 faltaGratis=56400

### agregarProducto Crema dental (agotada)
[isError] Crema dental está agotado

### agregarProducto 16 Lomo de cerdo (stock 15)
[isError] Solo quedan 15 unidades de Lomo de cerdo

### actualizarCantidad Arroz -> 3
pedido 5 BORRADOR | items=[('Arroz blanco', 3, 16200), ('Aceite de girasol', 1, 12900), ('Huevos AA', 1, 19900)] | subtotal=49000 domicilio=6000 total=55000 | faltaMinimo=0 faltaGratis=51000

### confirmarPedido con 'tarjeta de crédito'
[isError] Método de pago no válido: 'tarjeta de crédito'. Opciones (contra entrega): [EFECTIVO, DATAFONO, NEQUI]

### confirmarPedido EFECTIVO
pedido 5 CONFIRMADO | items=[('Arroz blanco', 3, 16200), ('Aceite de girasol', 1, 12900), ('Huevos AA', 1, 19900)] | subtotal=49000 domicilio=6000 total=55000 | faltaMinimo=0 faltaGratis=51000 | pago=EFECTIVO entrega=2026-11-13 03:09

### agregarProducto a un pedido confirmado
[isError] El pedido 5 está CONFIRMADO y ya no se puede modificar

### IDOR: verPedido 5 con el celular de otro cliente
[isError] El pedido 5 no pertenece al teléfono 3001234567

### IDOR: agregarProducto al pedido 5 con el celular de otro cliente
[isError] El pedido 5 no pertenece al teléfono 3001234567

### MySQL tras confirmar (stock: Arroz -3, Aceite -1, Huevos -1)
+----+-------------------+-------+
| id | nombre            | stock |
+----+-------------------+-------+
| 10 | Huevos AA         |    28 |
| 20 | Arroz blanco      |    97 |
| 23 | Aceite de girasol |    34 |
+----+-------------------+-------+
+----+------------+-------------+----------+-----------------+-------+----------------------------+
| id | estado     | metodo_pago | subtotal | costo_domicilio | total | entrega_estimada           |
+----+------------+-------------+----------+-----------------+-------+----------------------------+
|  5 | CONFIRMADO | EFECTIVO    |    49000 |            6000 | 55000 | 2026-11-13 03:09:28.454725 |
+----+------------+-------------+----------+-----------------+-------+----------------------------+
+-------------+----------+-----------------+----------+
| producto_id | cantidad | precio_unitario | subtotal |
+-------------+----------+-----------------+----------+
|          10 |        1 |           19900 |    19900 |
|          20 |        3 |            5400 |    16200 |
|          23 |        1 |           12900 |    12900 |
+-------------+----------+-----------------+----------+

### listarPedidosCliente 3001234567 (pedido histórico de Ana)
pedido 4 BORRADOR | items=[('Café molido', 3, 55500), ('Pan tajado blanco', 1, 7800)] | subtotal=63300 domicilio=6000 total=69300 | faltaMinimo=0 faltaGratis=36700
pedido 1 ENTREGADO | items=[('Leche entera', 2, 9200), ('Huevos AA', 1, 19900)] | subtotal=29100 domicilio=6000 total=35100 | faltaMinimo=0 faltaGratis=70900 | pago=EFECTIVO entrega=2026-11-09 16:58

### cancelarPedido con el teléfono de otro cliente
[isError] El pedido 5 no pertenece al teléfono 3001234567

### cancelarPedido (dueño)
pedido 5 CANCELADO | items=[('Arroz blanco', 3, 16200), ('Aceite de girasol', 1, 12900), ('Huevos AA', 1, 19900)] | subtotal=49000 domicilio=6000 total=55000 | faltaMinimo=0 faltaGratis=51000 | pago=EFECTIVO entrega=None

### MySQL: stock repuesto tras cancelar
+----+-------------------+-------+
| id | nombre            | stock |
+----+-------------------+-------+
| 10 | Huevos AA         |    29 |
| 20 | Arroz blanco      |   100 |
| 23 | Aceite de girasol |    35 |
+----+-------------------+-------+

### Exposición en red: POST a la IP de la LAN
http://<IP-LAN>:8090/mcp -> HTTP 000 (000 = conexión rechazada)
