# Ejercicio propuesto — ChatBot de pedidos a domicilio para un supermercado con MCP

**Asignatura:** Arquitectura de Software · Laboratorio 3  
**Autor:** Juan Manuel Tabares Uribe  
**Entrega:** 15 de noviembre de 2026

> **Enunciado:** realice un cliente de tipo ChatBot e intégrelo con un servidor MCP que conecte a una BD con MySQL, PostgreSQL u otro motor de su elección, para realizar pedidos de productos de un supermercado a domicilio. Explique cada paso que emplee para la solución.

```
ejercicio-supermercado/
├─ supermercado-mcp/        Servidor MCP: Spring Boot 3.5 + Spring AI 1.1.4 + JPA + MySQL   (puerto 8090)
├─ chatbot-supermercado/    Cliente ChatBot: Spring AI (ChatClient + cliente MCP) + Gemini + interfaz web (8091)
├─ scripts/
│  ├─ cargar-base.sh               crea la base "supermercado" con su catálogo
│  ├─ iniciar-supermercado-mcp.sh  arranca el servidor MCP
│  ├─ iniciar-chatbot.sh           arranca el chatbot
│  ├─ probar-servidor-mcp.sh       recorre un pedido completo contra el servidor solo con curl
│  └─ resumen_mcp.py               formatea las respuestas JSON-RPC del script anterior
├─ evidencias/              salidas de consola, capturas del chat y consultas a MySQL
└─ EJERCICIO.md             este documento
```

---

## Paso 0. Análisis del problema

El cliente debe poder, conversando en lenguaje natural:

1. Identificarse con su celular o registrarse con nombre y dirección.
2. Consultar el catálogo: categorías, productos, precios y disponibilidad.
3. Armar un pedido (carrito): agregar productos, cambiar cantidades y quitarlos.
4. Ver el resumen con el costo del domicilio y confirmar con un método de pago contra entrega.
5. Consultar y cancelar sus pedidos.

Reglas de negocio definidas para el domicilio:

| Regla | Valor |
|---|---|
| Costo del domicilio | $6.000 |
| Domicilio gratis | Subtotal ≥ $100.000 |
| Pedido mínimo para confirmar | $20.000 en productos |
| Cantidad por producto | 1 a 30 unidades, sin superar el stock |
| Pago | Contra entrega: EFECTIVO, DATAFONO o NEQUI (el chatbot nunca pide datos de tarjetas) |
| Tiempo de entrega estimado | 60 minutos después de confirmar |
| Edición | Solo un pedido en BORRADOR (carrito) se modifica; un cliente tiene como máximo un carrito abierto |
| Cancelación | Solo BORRADOR o CONFIRMADO, solo por el celular dueño del pedido; repone el inventario |

## Paso 1. Arquitectura y decisiones

```
 Cliente (navegador)
     │  HTTP  POST /api/chat  {conversacionId, mensaje}
     ▼
┌──────────────────── chatbot-supermercado  (HOST + CLIENTE MCP) ─────────────────────┐
│ index.html  ──►  ChatController  ──►  ChatClient (Spring AI)                          │
│                                        ├─ prompt de sistema (prompts/sistema.st)      │
│                                        ├─ memoria por conversación (40 mensajes)      │
│                                        ├─ modelo: Gemini (GoogleGenAiChatModel)  ◄────┼──► API de Gemini
│                                        └─ herramientas = cliente MCP (decoradas por   │
│                                           RegistroHerramientas; confirmar y cancelar  │
│                                           esperan el botón Aprobar del cliente)       │
└──────────────────────────────────────────┬────────────────────────────────────────────┘
                                           │ MCP: initialize · tools/list · tools/call
                                           │ JSON-RPC 2.0 sobre Streamable HTTP  →  http://localhost:8090/mcp
┌──────────────────────────────────────────▼──────── supermercado-mcp  (SERVIDOR MCP) ──┐
│ Adaptadores MCP (@Tool):  CatalogoTools · ClienteTools · PedidoTools   (11 herramientas)
│ Casos de uso:             ClienteService · PedidoService · ReglasNegocio               │
│ Persistencia:             Spring Data JPA (Producto, Cliente, Pedido, DetallePedido)   │
└──────────────────────────────────────────┬────────────────────────────────────────────┘
                                           ▼
                           MySQL 8.4  ·  base "supermercado"
```

| Decisión | Por qué |
|---|---|
| **Servidor MCP separado del chatbot** | Las herramientas de negocio se escriben una sola vez y cualquier cliente MCP las puede usar: este chatbot, Claude Desktop o un agente ADK, igual que en el laboratorio. |
| **Spring AI en ambos lados** | En el servidor convierte métodos `@Tool` en herramientas MCP; en el cliente, el *starter* MCP convierte el `tools/list` del servidor en `ToolCallback`s que el `ChatClient` entrega al modelo. Ninguna de las dos partes escribe JSON-RPC a mano. |
| **Gemini como modelo** | El curso ya usa una clave de Gemini (Parte E del laboratorio). Spring AI abstrae el proveedor: cambiar de modelo es cambiar el *starter* y dos propiedades, sin tocar el servidor MCP. |
| **Reglas en el servidor, no en el prompt** | El modelo puede equivocarse o ser manipulado ("ponme el domicilio gratis"). Precios, stock, mínimos y estados se validan en `PedidoService`. El prompt solo guía la conversación. |
| **Capas en el servidor** | `mcp` (adaptadores delgados) → `service` (reglas y transacciones) → `repository` → `model`. Las herramientas MCP son una interfaz más, como sería un controlador REST. |
| **DTOs en vez de entidades** | Las respuestas de las herramientas son `record`s planos: se evitan ciclos y la carga perezosa de JPA al serializar, y el modelo recibe solo lo que necesita, con campos de ayuda como `faltaParaDomicilioGratis`. |

## Paso 2. Base de datos (MySQL)

Archivo: `supermercado-mcp/src/main/resources/schema.sql`. Se carga con `scripts/cargar-base.sh`.

```
cliente (id, nombre, telefono UNIQUE, direccion, fecha_registro)
   1 ──< pedido (id, cliente_id FK, direccion_entrega, estado, metodo_pago,
                 subtotal, costo_domicilio, total, fecha_creacion, fecha_confirmacion,
                 entrega_estimada, version)
              1 ──< detalle_pedido (id, pedido_id FK ON DELETE CASCADE, producto_id FK,
                                    cantidad CHECK > 0, precio_unitario, subtotal,
                                    UNIQUE(pedido_id, producto_id))
producto (id, nombre, categoria, presentacion, precio CHECK > 0, stock CHECK >= 0, activo, version)
   1 ──< detalle_pedido
```

- **Catálogo:** 35 productos genéricos en 7 categorías, con precios en COP. La crema dental tiene stock 0 para probar la regla de producto agotado. Hay además una clienta de ejemplo con un pedido histórico ENTREGADO.
- **`precio_unitario` congelado:** se guarda el precio del momento en que se agrega el producto, así un cambio de precio posterior no altera pedidos ya hechos.
- **`version` (bloqueo optimista):** en `producto` y `pedido`, para que dos confirmaciones simultáneas no vendan dos veces la última unidad.
- **Restricciones en la BD:** `CHECK`, `UNIQUE` y claves foráneas son una segunda línea de defensa además de las validaciones Java.
- **Colación `utf8mb4_unicode_ci`:** el `LIKE` ignora mayúsculas y tildes, así que buscar "limon" encuentra "Limón Tahití".
- **`ddl-auto=validate`:** Hibernate no modifica el esquema; al arrancar comprueba que las entidades coinciden con `schema.sql` y falla si no.

## Paso 3. Servidor MCP (`supermercado-mcp`)

### 3.1 Configuración

`application.properties` usa el mismo patrón del laboratorio: `protocol=STREAMABLE`, `type=SYNC`, `mcp-endpoint=/mcp`, y la clave de MySQL leída de `${PWD_UDEA}`. Además:

- `server.address=127.0.0.1`: el servidor solo escucha en *loopback*. Es la mitigación de la actividad 16 del laboratorio, y se verificó que un POST desde la IP de la red local es rechazado.
- `spring.ai.mcp.server.instructions`: describe al cliente qué ofrece el servidor (catálogo, clientes y pedidos; precios en COP; pago contra entrega).

### 3.2 Herramientas MCP publicadas (11)

| Clase | Herramienta | Qué hace |
|---|---|---|
| `CatalogoTools` | `listarCategorias` | Categorías del catálogo. |
| | `buscarProductos(texto?, categoria?)` | Hasta 20 productos con id, precio, presentación, stock y `disponible`. |
| `ClienteTools` | `buscarCliente(telefono)` | Devuelve `{registrado, cliente, mensaje}`. Si no existe, indica el siguiente paso en vez de dar error. |
| | `registrarCliente(nombre, telefono, direccion)` | Valida un celular colombiano (10 dígitos, empieza por 3) y que no esté repetido. |
| `PedidoTools` | `crearPedido(telefono, direccionEntrega?)` | Crea el carrito, o devuelve el carrito abierto si ya existe (idempotente). |
| | `agregarProducto(pedidoId, telefono, productoId, cantidad)` | Suma unidades y valida stock, rango y estado. |
| | `actualizarCantidad(pedidoId, telefono, productoId, cantidad)` | Fija la cantidad; 0 quita el producto. |
| | `verPedido(pedidoId, telefono)` | Detalle con subtotal, domicilio, total, `faltaParaPedidoMinimo` y `faltaParaDomicilioGratis`. |
| | `confirmarPedido(pedidoId, telefono, metodoPago)` | Valida el mínimo, descuenta el stock en la misma transacción y calcula la hora de entrega. |
| | `cancelarPedido(pedidoId, telefono)` | Verifica el estado y repone el stock si estaba confirmado. |
| | `listarPedidosCliente(telefono)` | Historial del cliente, del más reciente al más antiguo. |

Todas las herramientas que operan sobre un pedido reciben el **celular del dueño**, y `PedidoService.pedidoDelCliente` verifica que el pedido le pertenezca antes de leerlo o modificarlo (control de acceso a nivel de objeto; ver Paso 6, problema 8).

**Cómo se diseñaron las herramientas para un modelo de lenguaje:**

- **Descripciones con instrucciones de uso.** La de `confirmarPedido`, por ejemplo, termina en: *«Llámala SOLO después de mostrar el resumen con el total y recibir la confirmación explícita del cliente»*.
- **Errores como mensajes que guían.** Algunos ejemplos: «Solo quedan 15 unidades de Lomo de cerdo», «El pedido mínimo es de $20000; faltan $10800», «Método de pago no válido… Opciones: [EFECTIVO, DATAFONO, NEQUI]». Cada excepción llega al cliente como resultado con `isError: true`, y el modelo puede explicarla y corregirse.
- **Campos calculados en las respuestas** (`faltaParaDomicilioGratis`, `faltaParaPedidoMinimo`, `disponible`), para que el modelo no tenga que hacer cuentas.
- **Fechas como texto con a. m./p. m.** (`"2026-11-13 4:09 a. m."`): el serializador por defecto las enviaba como arreglos `[2026, 11, 12, …]`, y en formato de 24 h el modelo llegó a confundir la madrugada con la tarde (problema 9).
- **Teléfonos normalizados:** "300 123 4567", "+57 3001234567" y "(300) 1234567" se aceptan como el mismo número.

### 3.3 Pruebas automatizadas (28/28)

```bash
cd supermercado-mcp && ./mvnw test
```

| Clase | Pruebas |
|---|---|
| `PedidoServiceTest` (17) | Mockito con un reloj fijo, sin BD. Cubre: suma de unidades y domicilio bajo el umbral, domicilio gratis desde $100.000, stock insuficiente, producto agotado, cantidad fuera de rango, pedido confirmado no editable, cantidad 0 quita el producto, pedido mínimo con lo que falta, confirmar descuenta el stock y calcula la entrega (10:00 → 11:00), método de pago inválido, cancelar repone el stock, cancelar con el celular de otro cliente, cancelar un pedido entregado, reutilizar el carrito abierto, cliente no registrado, **ver, agregar o confirmar un pedido ajeno (IDOR)** y celular del dueño escrito con formato. |
| `ReglasNegocioTest` (10) | Normalización y rechazo de teléfonos; tabla del costo de domicilio. |
| `SupermercadoMcpApplicationTests` (1) | Contexto completo contra MySQL: valida el mapeo JPA (`ddl-auto=validate`) y que se publiquen exactamente las 11 herramientas. |

### 3.4 Prueba del protocolo con curl

`scripts/probar-servidor-mcp.sh` hace `initialize` y `tools/list` y luego recorre un pedido completo con `tools/call`, incluidos los casos de error, verificando en MySQL el stock antes y después. Salida completa en `evidencias/servidor-mcp-curl.md`. Resumen:

| Paso | Resultado |
|---|---|
| `tools/list` | 11 herramientas |
| `buscarProductos texto='limon'` | "Limón Tahití" (sin tilde funciona) |
| `registrarCliente` con "12345" | `isError`: «debe ser un celular colombiano de 10 dígitos…» |
| `crearPedido` dos veces | El mismo pedido (no duplica) |
| Agregar 2 Arroz + 1 Aceite + 1 Huevos | Subtotal 43.600, domicilio 6.000, total 49.600 |
| Crema dental / 16 Lomo de cerdo | «está agotado» / «Solo quedan 15 unidades» |
| `confirmarPedido` con "tarjeta de crédito" | `isError` con las opciones válidas |
| `confirmarPedido EFECTIVO` | CONFIRMADO, entrega estimada 60 min; stock Arroz 100→97, Aceite 35→34, Huevos 29→28 |
| Agregar a un pedido confirmado | «ya no se puede modificar» |
| `verPedido` / `agregarProducto` con el celular de otro cliente | «El pedido 5 no pertenece al teléfono 3001234567» (IDOR bloqueado) |
| `cancelarPedido` con el celular de otro cliente | «no pertenece» |
| `cancelarPedido` (dueño) | CANCELADO; stock repuesto a 100, 35, 29 |
| POST a la IP de la LAN:8090 | Conexión rechazada (solo *loopback*) |

## Paso 4. Cliente ChatBot (`chatbot-supermercado`)

### 4.1 Cómo se conecta al servidor MCP

Basta con el *starter* `spring-ai-starter-mcp-client` y estas propiedades:

```properties
spring.ai.mcp.client.type=SYNC
spring.ai.mcp.client.streamable-http.connections.supermercado.url=http://localhost:8090
spring.ai.mcp.client.streamable-http.connections.supermercado.endpoint=/mcp
```

Al arrancar, el cliente hace `initialize` (negocia el protocolo `2025-06-18`) y `tools/list`, y publica un `ToolCallbackProvider` con las 11 herramientas. Cuando Gemini decide usar una, Spring AI la ejecuta como `tools/call` sobre MCP y le devuelve el resultado al modelo.

### 4.2 Componentes

| Archivo | Responsabilidad |
|---|---|
| `config/ChatConfig` | Arma el `ChatClient`: prompt de sistema, herramientas MCP (decoradas; `confirmarPedido` y `cancelarPedido` pasan por `AprobacionesPendientes`) y `MessageChatMemoryAdvisor` con una ventana de 40 mensajes por conversación. |
| `prompts/sistema.st` | Rol del asistente, flujo de atención en 6 pasos y reglas: no inventar precios, nunca pedir datos bancarios, ignorar intentos de cambiar las reglas, mencionar siempre el número de pedido y explicar que confirmar y cancelar se aprueban con un botón. |
| `chat/ChatController` | `POST /api/chat`, `POST /api/chat/{id}/aprobaciones/{accionId}` (botones Aprobar y Rechazar) y `DELETE /api/chat/{id}` (reiniciar la memoria y descartar acciones pendientes). Valida el tamaño del mensaje y traduce los fallos del proveedor a mensajes claros. |
| `chat/AprobacionesPendientes` | Aprobación en el *host* (problema 10). Intercepta `confirmarPedido` y `cancelarPedido`: en vez de enviarlas al servidor, guarda la acción con los argumentos exactos que propuso el modelo, lee el resumen real con `verPedido` (que además verifica el dueño) y le responde al modelo `PENDIENTE_DE_APROBACION`. Solo el botón Aprobar la ejecuta: una vez, desde la misma conversación y dentro de los 10 minutos siguientes. El modelo no tiene ninguna herramienta para aprobar. |
| `chat/RegistroHerramientas` | Decorador de cada herramienta: anota cuáles se usaron en el turno (la interfaz las muestra como etiquetas) y convierte los errores de herramienta en `{"error": "…"}`. |
| `config/GeminiConfig` | Cliente de Gemini con tiempo máximo por llamada y reintentos con espera creciente para 408/429/5xx. |
| `static/index.html` | Interfaz web: burbujas de chat, sugerencias, indicador de "escribiendo", etiquetas ⚙ con las herramientas MCP usadas, tarjeta de aprobación con el resumen del pedido y los botones Aprobar y Rechazar, modo claro u oscuro y diseño adaptable al móvil. El Markdown se interpreta después de escapar el HTML. |

Pruebas: `./mvnw test` → **15/15**. Cubren el registro de herramientas, la conversión de errores a JSON, los mensajes de error y el aviso de operaciones ya ejecutadas, y 9 casos de la aprobación en el host: la acción no llega al servidor hasta que el cliente aprueba, se usa una sola vez, rechazar no ejecuta nada, otra conversación no puede aprobarla, vence a los 10 minutos, un pedido ajeno devuelve el error del servidor sin mostrar botones, no se ofrece confirmar bajo el pedido mínimo o con un método de pago inválido, solo se interceptan las dos acciones sensibles, y el endpoint deja el resultado en la memoria del chat.

## Paso 5. Prueba de punta a punta por chat

Se recorrió una conversación real en `http://localhost:8091` con una clienta nueva, Laura. Capturas en `evidencias/chat-0*.jpg`. Los turnos 1 y 2 usaron `gemini-3.6-flash`; al agotarse su cuota diaria (Paso 6, problema 6), los turnos 3 a 6 usaron `gemini-3-flash-preview`. Entre turnos el chatbot se reinició para aplicar las correcciones del Paso 6, así que cada bloque es una conversación nueva: el carrito sobrevive porque vive en la BD del servidor, no en el chat.

| # | Mensaje de la clienta | Herramientas MCP invocadas | Respuesta / efecto |
|---|---|---|---|
| 1 | «Hola, quiero hacer un pedido. Mi celular es 320 987 6543» | `buscarCliente` | No está registrada; pide nombre y dirección. |
| 2 | «Me llamo Laura Restrepo y vivo en la Carrera 43A # 1-50, El Poblado, Medellín» | `registrarCliente` → `crearPedido` | «Te he registrado con éxito. He creado tu pedido (N.º 3).» |
| 3 | Nueva conversación: «Hola, mi celular es 3209876543. Quiero 2 bolsas de leche entera, una cubeta de huevos y una crema dental» | `buscarCliente`, `crearPedido`, `buscarProductos` ×4, `agregarProducto` ×2 | Agrega la leche y los huevos; avisa que la crema dental está agotada. Subtotal $29.100 + domicilio $6.000. |
| 4 | Nueva conversación: «Soy Laura (3209876543). Agrega a mi pedido un café molido y 2 kilos de pechuga de pollo» | `buscarCliente`, `crearPedido`, `buscarProductos` ×2, `agregarProducto` ×2 | Recupera el **Pedido N.º 3** del servidor aunque la conversación es nueva. Subtotal $85.400; «te faltan $14.600 para el domicilio gratis». |
| 5 | «Agrega 2 aguacates y un queso campesino. Después quiero confirmar, pago con Nequi» | `buscarCliente`, `buscarProductos` ×4, `crearPedido`, `agregarProducto` ×2 | Resumen de 6 productos, subtotal $105.900, **domicilio gratis**, dirección y NEQUI. **Pide confirmación explícita y no confirma solo.** |
| 6 | «Sí, confirmo» | `confirmarPedido` | El pedido quedó CONFIRMADO en MySQL (ver abajo). El modelo agotó la cuota diaria al redactar el mensaje final (ver Paso 6, problema 6). |

Estado final en MySQL (`evidencias/chat-pedido-confirmado-mysql.txt`):

```
pedido 3 | Laura Restrepo | 3209876543 | CONFIRMADO | NEQUI | subtotal 105900 | domicilio 0 | total 105900
         | Carrera 43A # 1-50, El Poblado, Medellín | confirmado 11:16 | entrega estimada 12:16
detalle: 2 Leche 9.200 · 1 Huevos 19.900 · 1 Café 18.500 · 2 Pechuga 37.800 · 2 Aguacate 7.000 · 1 Queso 13.500
stock:   Aguacate 45→43 · Leche 120→118 · Huevos 30→29 · Queso 25→24 · Pechuga 30→28 · Café 30→29
```

### Sesión de cierre (13 de noviembre, con la cuota renovada)

| # | Mensaje | Herramientas MCP invocadas | Respuesta / efecto |
|---|---|---|---|
| A | «Hola, mi celular es 3209876543. ¿Cuál es el estado de mi pedido 3?» | `verPedido` | «Tu pedido N.º 3 se encuentra en estado **CONFIRMADO**. La entrega está programada aproximadamente para las 12:16 PM… total $105.900 mediante NEQUI.» Cierra el turno 6, cuyo mensaje final no alcanzó a generarse (`evidencias/chat-06-estado-pedido-confirmado.jpg`). Esta prueba destapó el problema 8 (IDOR), corregido enseguida. |
| B | Ana: «Hola, soy Ana (3001234567). Quiero 3 paquetes de café molido y un pan tajado. Pago en efectivo, muéstrame el resumen para confirmar» | `buscarCliente`, `buscarProductos` ×2, `crearPedido`, `agregarProducto` ×2 | Se crearon el pedido 4 y sus líneas (3 cafés $55.500 + 1 pan $7.800 = $63.300). El modelo agotó la cuota diaria al redactar el resumen, y la interfaz mostró el aviso nuevo: *«antes del error alcancé a ejecutar crearPedido, agregarProducto. Pregúntame por el estado de tu pedido antes de repetir la solicitud»* (`evidencias/chat-07-aviso-operaciones-ejecutadas.jpg`). |
| C | (03:07, cuota renovada) Ana: «Muéstrame mi pedido 4, quiero confirmarlo con pago en efectivo» | `buscarCliente`, `verPedido` | Resumen: 3 cafés $55.500 + 1 pan $7.800, subtotal $63.300, domicilio $6.000, **total $69.300**, dirección registrada. **Pide confirmación explícita** (`evidencias/chat-08-resumen-antes-de-confirmar.jpg`). |
| D | «Sí, confirmo» | `confirmarPedido` | «¡Listo, Ana! Tu Pedido N.º 4 ha sido confirmado con éxito… pago en EFECTIVO… hora estimada… **04:09 PM**». El pedido quedó bien, pero la hora decía **p. m.** cuando era de madrugada (problema 9, `evidencias/chat-09-confirmacion-final-error-am-pm.jpg`). |
| E | Tras la corrección: «¿A qué hora llega mi pedido 4?» | `verPedido` | «…hora estimada de entrega para las **4:09 a. m.** del 13 de noviembre de 2026» (`evidencias/chat-10-hora-entrega-corregida.jpg`). |
| F | «Ahora quiero un pedido nuevo con 10 botellas de agua sin gas» → «Pensándolo bien, cancela ese pedido» | `buscarCliente`, `crearPedido`, `buscarProductos`, `agregarProducto` → `cancelarPedido` | Crea el pedido 6 ($22.000 + $6.000) y luego lo cancela con el celular de su dueña. **No pidió confirmación antes de cancelar** (problema 10, `evidencias/chat-11-crear-y-cancelar.jpg`). |

Estado final de la base (`evidencias/chat-cierre-mysql.txt`):

```
pedido 1 | Ana Gómez      | ENTREGADO  | EFECTIVO | 35.100  | histórico del schema.sql
pedido 2 | Carlos Ruiz    | CANCELADO  | EFECTIVO | 55.000  | prueba con curl (12 nov)
pedido 3 | Laura Restrepo | CONFIRMADO | NEQUI    | 105.900 | chatbot, domicilio gratis
pedido 4 | Ana Gómez      | CONFIRMADO | EFECTIVO | 69.300  | chatbot, confirmado 03:09, entrega 04:09
pedido 5 | Carlos Ruiz*   | CANCELADO  | EFECTIVO | 55.000  | prueba con curl con casos IDOR (13 nov)  *otro celular
pedido 6 | Ana Gómez      | CANCELADO  | —        | 28.000  | chatbot; era BORRADOR, así que no había descontado stock
stock: Pan 40→39 · Café 29→26 (pedido 4) · Agua 100 (sin cambios)
```

### Sesión de aprobación en el host (14 de noviembre)

Después de implementar la aprobación del problema 10 se repitió con Ana el caso que había fallado, en una conversación nueva con `gemini-3.6-flash`:

| # | Acción del cliente | Herramientas MCP | Resultado |
|---|---|---|---|
| G | «Hola, soy Ana (3001234567). Quiero 2 paquetes de café molido y una botella de aceite. Pago en efectivo, confírmame el pedido.» | `buscarCliente`, `crearPedido`, `buscarProductos` ×2, `agregarProducto` ×2. El modelo llamó `confirmarPedido`, pero el host la detuvo | El chat muestra la tarjeta «Confirmar el Pedido N.º 7»: 2 cafés $37.000, 1 aceite $12.900, domicilio $6.000, total $55.900, EFECTIVO y la dirección. En MySQL el pedido seguía en BORRADOR y sin método de pago (`evidencias/chat-12-tarjeta-de-aprobacion.jpg`). |
| H | Clic en **Aprobar** | `confirmarPedido`, ejecutada por el host | «Pedido N.º 7 confirmado. Total: $55.900, pago en EFECTIVO contra entrega. Entrega estimada: 2026-11-15 12:20 a. m.» Stock: café 26→24, aceite 35→34. No hizo falta otra llamada al modelo (`chat-13-aprobado-y-confirmado.jpg`). |
| I | «Pensándolo bien, cancela ese pedido» | Ninguna ejecutada: `cancelarPedido` quedó pendiente | Tarjeta «Cancelar el Pedido N.º 7». Es la misma frase con la que el modelo canceló el pedido 6 sin preguntar (turno F); ahora no puede hacerlo por su cuenta (`chat-14-cancelar-pide-aprobacion.jpg`). |
| J | Clic en **Rechazar** | — | «Listo, no cancelé el Pedido N.º 7. ¿Quieres cambiar algo?» El pedido sigue CONFIRMADO y el stock no cambia (`chat-15-cancelacion-rechazada.jpg`). |

Estado en MySQL al terminar (`evidencias/chat-aprobacion-mysql.txt`):

```
pedido 7 | Ana Gómez | CONFIRMADO | EFECTIVO | subtotal 49.900 | domicilio 6.000 | total 55.900
         | confirmado 23:20 del 14 nov | entrega estimada 00:20 del 15 nov
detalle: 2 Café molido 37.000 · 1 Aceite de girasol 12.900
stock:   Café 26→24 · Aceite 35→34 (sin cambios después del rechazo)
```

## Paso 6. Problemas encontrados y cómo se resolvieron

| # | Problema | Causa | Solución |
|---|---|---|---|
| 1 | El catálogo aparecía como "LÃ¡cteos", y "limon" no encontraba nada | El cliente `mysql`, sin locale configurado, envió el UTF-8 del script como latin1 | `SET NAMES utf8mb4` en `schema.sql` y `--default-character-set=utf8mb4` en `cargar-base.sh`. |
| 2 | Fechas como `[2026, 11, 12, 11, 57, …]` | Serialización por defecto de `LocalDateTime` | `PedidoDto` las envía como texto legible (después con a. m./p. m., ver problema 9). |
| 3 | El chat se quedaba "pensando" indefinidamente | El SDK de Gemini no tiene timeout de lectura por defecto y el servicio a veces no responde (503, alta demanda) | `GeminiConfig`: tiempo máximo por llamada (150 s, que incluye los reintentos) y reintentos del SDK para 408/429/5xx. Spring AI con `retry.max-attempts=1`, porque reintentar el turno completo podría ejecutar dos veces una herramienta; el SDK reintenta una sola llamada al modelo, que es seguro. |
| 4 | El modelo inventó "pedido 101" | La memoria del chat guarda los textos finales, no los resultados de las herramientas, y la respuesta no mencionaba el número | El prompt exige mencionar siempre «Pedido N.º X» y, si no lo recuerda, volver a llamar `crearPedido` (idempotente, devuelve el carrito abierto). |
| 5 | Un error de herramienta abortaba el turno ("Failed to parse JSON: Error calling tool…") | Ante un `isError`, el cliente MCP de Spring AI lanza una excepción con texto plano, y el conector de Gemini exige que la respuesta de la herramienta sea JSON | `RegistroHerramientas` devuelve `{"error": "No existe el pedido 101"}`; el modelo lo lee y se corrige. Hay una prueba unitaria que reproduce el caso. |
| 6 | Gemini respondía 429 RESOURCE_EXHAUSTED | La clave es del **plan gratuito: 20 peticiones por día por modelo** y un límite por minuto. Cada turno con herramientas consume entre 2 y 5 peticiones | Mensaje claro al usuario. Si el fallo ocurre después de una herramienta de escritura, se avisa: «antes del error alcancé a ejecutar confirmarPedido; pregúntame por el estado de tu pedido antes de repetir la solicitud». El modelo se cambia sin recompilar con `GEMINI_MODEL`; la demostración se terminó con `gemini-3-flash-preview`. |
| 7 | Incluso con cuota diaria, un turno largo fallaba con 429 | El límite **por minuto** del plan gratuito: un turno con 5 o 6 herramientas encadena el mismo número de llamadas seguidas al modelo | Esperas de reintento de 5, 10, 20 y 30 s (algo más de un minuto, la ventana del límite). El turno se vuelve más lento, pero termina. |
| 8 | **IDOR**: con `verPedido(3)` el chatbot podía mostrar un pedido sin verificar quién preguntaba. Lo mismo pasaba con `agregarProducto`, `actualizarCantidad` y `confirmarPedido`: con adivinar un id se podía ver o modificar un pedido ajeno | Las herramientas solo recibían el `pedidoId`; únicamente `cancelarPedido` validaba el dueño | Todas reciben el celular, y `PedidoService.pedidoDelCliente` lo compara con el dueño del pedido. 2 pruebas unitarias nuevas y 2 casos IDOR en el script de curl («El pedido 5 no pertenece al teléfono 3001234567»). |
| 9 | El chatbot dijo «04:09 PM» para una entrega a las 4:09 de la madrugada | El DTO enviaba la hora en 24 h (`2026-11-13 04:09`) y el modelo le agregó "PM" por su cuenta | El servidor envía la hora con a. m./p. m. explícito (`2026-11-13 4:09 a. m.`), armado a mano porque el locale es-CO de Java mete un espacio especial (U+202F). Además, el prompt pide copiar las horas tal como llegan. Verificado en el turno E. |
| 10 | El chatbot canceló el pedido 6 sin pedir confirmación, aunque el prompt lo exige | Las reglas del prompt son orientaciones, no garantías: el modelo tomó «cancela ese pedido» como consentimiento explícito | **Resuelto con aprobación en el *host*.** `AprobacionesPendientes` intercepta las dos herramientas: el modelo solo puede proponerlas, y se ejecutan cuando el cliente pulsa Aprobar en la tarjeta con el resumen real del pedido (Paso 4.2). Verificado en los turnos G a J: la misma frase «cancela ese pedido» ahora muestra la tarjeta, y al rechazarla el pedido 7 sigue confirmado. Se usó un decorador de `ToolCallback` en lugar de `internalToolExecutionEnabled=false` porque conserva el bucle de herramientas de Spring AI y la memoria del chat sin reescribirlos. Es el mismo principio de Claude Desktop, que pide permiso antes de ejecutar una herramienta. |

## Paso 7. Cómo ejecutarlo

Requisitos: JDK 21, Docker (o un MySQL 8 local) y una clave de Gemini en `lab3/.env` (`../scripts/configurar-gemini.sh`).

```bash
./scripts/cargar-base.sh               # 1. crea la base y el catálogo
./scripts/iniciar-supermercado-mcp.sh  # 2. servidor MCP en :8090 (dejarlo corriendo)
./scripts/iniciar-chatbot.sh           # 3. en otra terminal: chatbot en :8091
open http://localhost:8091             # 4. conversar
./scripts/probar-servidor-mcp.sh       # opcional: prueba del protocolo con curl
```

Para usar otro modelo: `GEMINI_MODEL=gemini-3-flash-preview ./scripts/iniciar-chatbot.sh`.

Como el servidor es MCP estándar, también se puede conectar a Claude Desktop con el mismo puente del laboratorio (`npx -y mcp-remote http://localhost:8090/mcp`) sin cambiar una línea del servidor.

## Paso 8. Limitaciones y mejoras propuestas

- **Autenticación real.** El servidor ya verifica que el celular sea el dueño de cada pedido (problema 8), pero el celular en sí no se verifica: quien conozca el número de otra persona puede suplantarla. En producción haría falta un OTP por SMS u OAuth, y el servidor MCP debería exigir un token (MCP define autorización OAuth 2.1).
- **Memoria persistente.** La memoria del chat está en RAM (`InMemoryChatMemoryRepository`); se pierde al reiniciar. Spring AI ofrece repositorios JDBC para guardarla en MySQL. El pedido sí persiste en la BD.
- **Carrito implícito.** Un paso más allá del problema 8 es que las herramientas del carrito no reciban `pedidoId` sino solo el celular, y operen siempre sobre el carrito abierto del cliente. Así desaparece la clase de errores del problema 4.
- **Operación del domicilio.** Faltan herramientas internas (no expuestas al chatbot) para pasar los pedidos a EN_CAMINO y ENTREGADO, y para administrar el catálogo.
- **Cuotas del modelo.** Para uso real hace falta un plan de pago o un modelo local, además de limitar las peticiones por usuario en `ChatController`.
