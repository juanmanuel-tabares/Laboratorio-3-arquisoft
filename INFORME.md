# Laboratorio 3 — MCP Server con Spring Boot, Spring AI, Claude Desktop y Google ADK

**Asignatura:** Arquitectura de Software  
**Autor:** Juan Manuel Tabares Uribe  
**Fecha:** noviembre de 2026  
**Caso de estudio:** administración de tareas mediante herramientas MCP

```
lab3/
├─ README.md             presentación del repositorio
├─ INFORME.md            este informe
├─ tarea-mcp/            MCP Server (Spring Boot 3.4.7 + Spring AI 1.1.4 + JPA + MySQL)
├─ agente-mcp/           Agente Google ADK Java 1.0.0 (Gemini) que consume el MCP Server
├─ scripts/
│  ├─ mcp.sh                    cliente MCP mínimo con curl (init | list | call)
│  ├─ iniciar-servidor-mcp.sh   arranca MySQL (Docker) + MCP Server en :8088
│  ├─ iniciar-agente-adk.sh     arranca la UI de ADK en :8080
│  ├─ configurar-gemini.sh      guarda GOOGLE_API_KEY en .env sin mostrarla
│  └─ configurar-claude-desktop.sh  agrega tareas-mcp a Claude Desktop (con la app cerrada)
├─ evidencias/           salidas de consola y capturas de cada parte
├─ ejercicio-supermercado/  ejercicio propuesto (ver sección 9 y su propio EJERCICIO.md)
├─ .env.example          plantilla de variables; la copia real .env queda fuera de Git
└─ .gitignore
```

---

## 0. Entorno y adaptaciones respecto a la guía

La guía está escrita para Windows/PowerShell; el laboratorio se ejecutó en **macOS (Apple Silicon)**.

| Requisito | Versión usada | Nota |
|---|---|---|
| JDK | 21.0.8 | El JDK por defecto del equipo es 25; se fija `JAVA_HOME=$(/usr/libexec/java_home -v 21)` antes de cada comando Maven. |
| Maven Wrapper | 3.9.12 (`mvnw`, `mvnw.cmd`) | Generado con `mvn -N wrapper:wrapper`. |
| MySQL | 8.4.11 en Docker (`tareas-mysql`, puerto **3307**) | El equipo ya tiene un MySQL 9.7 en 3306. Para no tocarlo, el laboratorio usa un contenedor MySQL 8.x (lo que pide la guía). La URL JDBC quedó `localhost:${MYSQL_PORT:3306}`: en un PC del laboratorio sin la variable funciona igual que la guía. |
| Node / npm | 22.20.0 / 10.9.3 (vía nvm) | |
| MCP Inspector | 0.12.0 (la versión de la guía) | |
| mcp-remote | 0.16.6 | |
| Google ADK Java | `google-adk` 1.0.0 + `google-adk-dev` 1.0.0 | La guía mezclaba 0.6.0 y 1.0.0; se alinearon (sección 10.3 de la guía). |
| Modelo Gemini | `gemini-3.6-flash` (configurable con `GEMINI_MODEL`) | `gemini-2.5-flash` (el de la guía) responde HTTP 404 "no longer available to new users". `gemini-3.8-flash` no respondió en 45 s y `gemini-flash-latest` dio 503 (alta demanda). Se probó primero con `gemini-3.5-flash` (1,7 s) y la versión final usa `gemini-3.6-flash` (1,9 s); ambos devuelven la llamada a herramienta correcta. |

**Inconsistencias de la guía corregidas:** la guía menciona a veces `http://localhost:8888/mcp`, pero `server.port=8088`. Todo el laboratorio usa **8088** (también el mensaje de error de `TareaAgent`).

**Manejo de secretos:** la clave de MySQL se generó al azar y vive solo en `lab3/.env` (permisos 600, en `.gitignore`). `application.properties` la lee con `${PWD_UDEA}`. Los scripts hacen `source .env` antes de arrancar.

---

## 1. Parte A — Base de datos

`tarea-mcp/src/main/resources/schema.sql` crea la base `tareas_mcp`, la tabla `tarea` y 3 registros.

```bash
docker run -d --name tareas-mysql -p 3307:3306 -e MYSQL_ROOT_PASSWORD="$PWD_UDEA" \
  -v tareas-mysql-data:/var/lib/mysql mysql:8.4
mysql -h 127.0.0.1 -P 3307 -u root -p < tarea-mcp/src/main/resources/schema.sql
mysql -h 127.0.0.1 -P 3307 -u root -p -e "SELECT id, titulo, fecha, completado FROM tareas_mcp.tarea;"
```

> El cliente `mysql` 9.x no aceptó `-e "source ruta"` (error 1064); redirigir el archivo con `<` es equivalente.
> Se agregó `spring.sql.init.mode=never` para que Spring no vuelva a ejecutar `schema.sql` (y duplique los INSERT) al arrancar.

```
+----+---------------------+------------+------------+
| id | titulo              | fecha      | completado |
+----+---------------------+------------+------------+
|  1 | Preparar charla MCP | 2026-04-20 |          0 |
|  2 | Revisar slides      | 2026-04-21 |          0 |
|  3 | Probar demo en vivo | 2026-04-22 |          1 |
+----+---------------------+------------+------------+
```

## 2. Parte B — MCP Server con Spring Boot + Spring AI

Paquete `com.udea.tarea_mcp`:

| Clase | Rol |
|---|---|
| `model/Tarea` | Entidad JPA (`@Version` para bloqueo optimista). |
| `repository/TareaRepository` | `JpaRepository` + consultas derivadas `findByCompletado`, `findByTituloContainingIgnoreCase`. |
| `service/TareaToolsService` | Métodos `@Tool` / `@ToolParam` → herramientas MCP. |
| `TareaMcpApplication` | `@Bean ToolCallbackProvider` con `MethodToolCallbackProvider` que registra el servicio. |

Configuración clave (`application.properties`): `spring.ai.mcp.server.protocol=STREAMABLE`, `type=SYNC`, `streamable-http.mcp-endpoint=/mcp`, `server.port=8088`.

```bash
./scripts/iniciar-servidor-mcp.sh      # equivale a: ./mvnw clean test && ./mvnw spring-boot:run
```

Log de arranque:

```
McpServerAutoConfiguration : Registered tools: 7        (9 tras las actividades 10 y 11)
TomcatWebServer            : Tomcat started on port 8088 (http)
TareaMcpApplication        : Started TareaMcpApplication in 2.7 seconds
```

## 3. Parte C — Pruebas del protocolo MCP

### 3.1 Handshake con curl

```bash
curl -X POST http://localhost:8088/mcp -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```

```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26",
 "capabilities":{"completions":{},"logging":{},"prompts":{"listChanged":true},
   "resources":{"subscribe":false,"listChanged":true},"tools":{"listChanged":true}},
 "serverInfo":{"name":"tarea-mcp-server","version":"1.0.0"}}}
```

El servidor responde `HTTP 200` con la cabecera `Mcp-Session-Id`. Con Streamable HTTP, las peticiones siguientes deben enviar esa cabecera y, antes de `tools/list`, la notificación `notifications/initialized` (responde `202`). El script `scripts/mcp.sh` automatiza el flujo:

```bash
./scripts/mcp.sh init
./scripts/mcp.sh list
./scripts/mcp.sh call buscarTareas '{"fragmento":"slides"}'
```

### 3.2 MCP Inspector

El comando de la guía (`npx @modelcontextprotocol/inspector@0.12.0 sse http://...`) pasa `sse` como *comando STDIO*, no como transporte. Se ejecutó sin argumentos y se configuró en la UI: **Transport Type = Streamable HTTP**, **URL = `http://127.0.0.1:8088/mcp`** → *Connect* → *Tools* → *List Tools*: aparecen las 7 herramientas, y `listarPendientes` devuelve **Tool Result: Success**.

Evidencia: `evidencias/parteC-inspector-listarPendientes.jpg`.

### 3.3 Prueba de las 7 herramientas (sección 8.3)

Salida completa en `evidencias/parteC-curl.md`. Resumen:

| # | Herramienta | Argumentos | Resultado |
|---|---|---|---|
| 1 | listarTareas | — | 3 tareas |
| 2 | listarPendientes | — | ids 1 y 2 (completado=false) |
| 3 | buscarTareas | `fragmento="slides"` | id 2 "Revisar slides" |
| 4 | crearTarea | "Tarea de prueba curl", 2026-11-20 | id 4, completado=false, version=0 |
| 5 | marcarCompletada | id 4 | completado=true, version=1 |
| 6 | actualizarTarea | id 4, título editado, 2026-11-21 | version=2; `SELECT` lo confirma |
| 7 | eliminarTarea | id 4 | "Tarea 4 eliminada correctamente." |
| — | eliminarTarea / marcarCompletada | id 999 | "no existe" / `isError: true` "No existe la tarea 999" |

Las excepciones de un `@Tool` llegan al cliente como resultado con `isError: true` y el mensaje, lo que permite que el modelo explique el error al usuario.

## 4. Parte D — Claude Desktop

Claude Desktop sólo lanza servidores **STDIO** desde `claude_desktop_config.json`, así que se usa el puente `mcp-remote` (STDIO ↔ Streamable HTTP).

**Verificación del puente por consola** (simulando a Claude Desktop):

```
initialize OK -> {'name': 'tarea-mcp-server', 'version': '1.0.0'} 2025-03-26
tools/list OK -> 7 herramientas
```

**Configuración** (`~/Library/Application Support/Claude/claude_desktop_config.json` en macOS; `%APPDATA%\Claude\claude_desktop_config.json` en Windows). Se añadió sólo la clave `mcpServers` (respaldo: `claude_desktop_config.json.bak-lab3-*`):

```json
"mcpServers": {
  "tareas-mcp": {
    "command": "/Users/<usuario>/.nvm/versions/node/v22.20.0/bin/npx",
    "args": ["-y", "mcp-remote@0.16.6", "http://localhost:8088/mcp"],
    "env": { "PATH": "/Users/<usuario>/.nvm/versions/node/v22.20.0/bin:/usr/local/bin:/usr/bin:/bin" }
  }
}
```

> Node está instalado con **nvm**, que Claude Desktop (abierto desde el Dock) no tiene en su PATH: por eso se usa la ruta absoluta de `npx` y un `PATH` explícito. Es el equivalente macOS de la nota de la guía sobre `npx.cmd`.

> **Hallazgo:** si se edita el archivo con Claude Desktop **abierto**, la app lo reescribe con su copia en memoria al guardar preferencias y **borra** `mcpServers` (ocurrió al día siguiente sin haber reiniciado la app). Por eso `scripts/configurar-claude-desktop.sh` se niega a correr si la app está abierta, hace un respaldo y luego agrega la entrada.

Procedimiento:

1. Cerrar Claude Desktop por completo (⌘Q).
2. En Terminal.app: `scripts/configurar-claude-desktop.sh` y luego `scripts/iniciar-servidor-mcp.sh` (dejarlo corriendo).
3. Abrir Claude Desktop → *Settings → Desktop app → Extensions → Local MCP servers*: `tareas-mcp` debe aparecer **Running**.

**Validación (9.3):**

| Paso | Resultado |
|---|---|
| 3. Servidor visible y conectado | `tareas-mcp` **Running**, con el comando `npx`, los argumentos `-y mcp-remote@0.16.6 http://localhost:8088/mcp` y el `PATH` configurado (`evidencias/parteD-claude-desktop-tareas-mcp-running.jpg`). La lista de procesos lo confirma: `Claude.app → npx mcp-remote@0.16.6 → node mcp-remote http://localhost:8088/mcp`. |
| 4. «Lista mis tareas pendientes» | Claude invocó **`Tareas-mcp: Listar pendientes`** y respondió con una tabla de 4 tareas (ids 1, 2, 8, 9). Por iniciativa propia marcó como *vencidas* las de abril y ofreció completarlas o borrarlas (`evidencias/parteD-claude-desktop-chat-pendientes.png`). |
| 5. «Crea una tarea llamada Preparar laboratorio MCP con fecha 2026-11-20» | `crearTarea` → `{"id":10,"titulo":"Preparar laboratorio MCP","fecha":"2026-11-20","completado":false,"version":0}`; `contarTareas` → 6. |
| 6. Confirmación en MySQL | La fila `10 \| Preparar laboratorio MCP \| 2026-11-20 \| 0` aparece en el `SELECT` (`evidencias/parteD-select-mysql.txt`). |

## 5. Parte E — Agente Google ADK para Java

`agente-mcp/src/main/java/com/udea/agente_mcp/TareaAgent.java` crea un `McpToolset` con `StreamableHttpServerParameters` (`http://localhost:8088/mcp`), descubre las herramientas y construye un `LlmAgent` con Gemini (`gemini-3.6-flash` por defecto, ver sección 0). `AdkWebServer` (perfil `runAdkWebServer` del POM) escanea `target/` y publica el agente en `http://localhost:8080`.

```bash
./scripts/iniciar-agente-adk.sh     # equivale a: ./mvnw clean install && ./mvnw exec:java@runAdkWebServer
```

Log (`evidencias/parteE-adk-arranque.log`):

```
LifecycleInitializer   : Server response with Protocol: 2025-06-18, Capabilities: ...
Herramientas MCP descubiertas: 9
CompiledAgentLoader    : CompiledAgentLoader initialized with 1 agents: [TareaAgent]
```

Nótese que el cliente MCP de ADK negocia la versión de protocolo `2025-06-18`, mientras que curl/Inspector/mcp-remote usaron `2025-03-26`: el mismo servidor Spring AI atiende ambas.

UI de ADK con `TareaAgent` cargado: `evidencias/parteE-adk-ui.jpg`.

La clave se guarda con `scripts/configurar-gemini.sh`, que la pide con entrada oculta y la escribe en `.env` (nunca en el código).

**Conversación con Gemini (10.6):**

| Petición | Herramientas invocadas (vía MCP) | Respuesta del agente |
|---|---|---|
| «Muestra las tareas pendientes y agrúpalas por fecha» | `listarPendientes` | 📅 2026-04-20 → ID 1 Preparar charla MCP · 📅 2026-04-21 → ID 2 Revisar slides |
| «Crea una tarea de prueba llamada Probar agente ADK para el 20 de noviembre de 2026 y luego dime cuántas tareas hay en total» | `crearTarea` → `contarTareas` | «La tarea "Probar agente ADK" ha sido creada exitosamente para el 20 de noviembre de 2026 (ID: 8). Actualmente hay 4 tareas en total.» |

Verificación en MySQL: aparece la fila `8 | Probar agente ADK | 2026-11-20 | 0`. Detalles a notar:

- Gemini convirtió «20 de noviembre de 2026» al formato ISO que exige la validación de la actividad 12.
- Encadenó dos herramientas en una sola petición, y la segunda es la nueva `contarTareas`.
- El id es 8 y no 5 porque las pruebas `@DataJpaTest` hacen *rollback* de las filas, pero MySQL no devuelve los valores de `AUTO_INCREMENT`.

Evidencias: `evidencias/parteE-adk-chat-pendientes.jpg`, `evidencias/parteE-adk-chat-crear-contar.jpg`.

**Repetición con `gemini-3.6-flash` (versión final del agente):** se recompiló el agente (`./mvnw clean install`: 1 prueba, 0 fallos), se reinició ADK (9 herramientas descubiertas) y se repitieron las dos pruebas en una sesión nueva, sin errores en el log:

| Petición | Herramientas invocadas | Respuesta del agente |
|---|---|---|
| «Muestra las tareas pendientes y agrúpalas por fecha» | `listarPendientes` | 📅 2026-04-20 → ID 1 · 📅 2026-04-21 → ID 2 · 📅 2026-11-20 → ID 8 Probar agente ADK |
| «Crea una tarea de prueba llamada Probar Gemini 3.6 para el 21 de noviembre de 2026 y luego dime cuántas tareas hay en total» | `crearTarea` → `contarTareas` | «La tarea "Probar Gemini 3.6" ha sido creada exitosamente para el 21 de noviembre de 2026 (ID: 9). Actualmente hay un total de 5 tareas registradas (incluyendo pendientes y completadas).» |

MySQL confirma la fila `9 | Probar Gemini 3.6 | 2026-11-21 | 0` y 5 filas en total. Evidencias: `evidencias/parteE-adk-gemini36-pendientes.jpg`, `evidencias/parteE-adk-gemini36-crear-contar.jpg`.

## 6. Flujo de ejecución observado

1. `initialize` → el servidor devuelve capacidades y `Mcp-Session-Id`.
2. `notifications/initialized` → `202 Accepted`.
3. `tools/list` → nombre, descripción y `inputSchema` (JSON Schema generado desde `@ToolParam`) de cada herramienta.
4. El modelo elige herramienta y argumentos; el cliente envía `tools/call`.
5. Spring AI despacha al método `@Tool`; JPA consulta/modifica MySQL; el resultado se serializa a JSON en `content[0].text`.
6. El modelo redacta la respuesta en lenguaje natural.

## 7. Tabla de pruebas (sección 12)

| Prueba | Resultado | Evidencia |
|---|---|---|
| Conectividad | Servidor activo en 8088, sin errores | log de arranque |
| Negociación | `initialize` válido, protocolo 2025-03-26 | `evidencias/parteC-curl.md` |
| Descubrimiento | 7 herramientas (9 tras actividades) | Inspector + `tools/list` |
| Lectura | `listarPendientes` → sólo ids 1 y 2 | Inspector / curl |
| Creación | `crearTarea` → fila id 4 en MySQL | `SELECT` posterior |
| Actualización | `marcarCompletada` → completado=1 | `SELECT` posterior |
| Claude Desktop | `tareas-mcp` Running; el chat lista las 4 pendientes con datos reales; `crearTarea` → fila id 10 en MySQL | capturas de Settings y del chat + `SELECT` |
| Google ADK | Agente (gemini-3.6-flash) descubre 9 tools e invoca `listarPendientes`, `crearTarea`, `contarTareas`; filas id 8 y 9 en MySQL | consola + capturas UI ADK |

---

## 8. Actividades para los estudiantes (sección 15)

### Actividad 10 — `contarTareas`

```java
@Tool(description = "Devuelve el número total de tareas almacenadas (pendientes y completadas). "
    + "Úsala cuando el usuario pregunte cuántas tareas hay, sin necesidad de listarlas.")
public long contarTareas() { return repo.count(); }
```

La descripción es lo único que el modelo ve para decidir si usar la herramienta: dice **qué devuelve** (un número, no una lista), **qué incluye** (pendientes y completadas) y **cuándo usarla** (preguntas de "cuántas"), para que el modelo no llame `listarTareas` y cuente a mano. Al no tener parámetros, su `inputSchema` es un objeto vacío. Resultado: `3`.

### Actividad 11 — `listarCompletadas` vs `listarPendientes`

Ambas usan la misma consulta derivada `findByCompletado(Boolean)` con valores opuestos, así que forman una partición del conjunto: `listarCompletadas` → id 3; `listarPendientes` → ids 1 y 2; la unión es `listarTareas` y la intersección es vacía (verificado también en `TareaToolsServiceTest`).

### Actividad 12 — Validación ISO `yyyy-MM-dd`

`TareaToolsService.validarFechaIso` usa `LocalDate.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE)`. Ese formateador es `ResolverStyle.STRICT`, así que rechaza tanto formatos distintos (`20/11/2026`, `2026/04/25`, `2026-1-5`) como fechas imposibles (`2026-02-30`). Se aplica en `crearTarea` (fecha obligatoria) y en `actualizarTarea` (sólo si se envía fecha). El mensaje de error indica el formato correcto para que el modelo pueda corregir y reintentar. Evidencia: `evidencias/actividades-10-12.md`.

### Actividad 13 — Host, cliente MCP, servidor MCP y herramienta

```
┌──────────────────────── HOST (Claude Desktop / AdkWebServer) ────────────────────────┐
│  Usuario ──► LLM (Claude / Gemini) ── decide qué tool usar                           │
│                     │                                                                 │
│              ┌──────▼───────┐   1 conexión por servidor                               │
│              │ CLIENTE MCP  │   (mcp-remote vía STDIO  /  McpToolset nativo)          │
│              └──────┬───────┘                                                         │
└─────────────────────┼─────────────────────────────────────────────────────────────────┘
                      │ JSON-RPC 2.0: initialize · tools/list · tools/call
                      │ transporte Streamable HTTP  →  http://localhost:8088/mcp
┌─────────────────────▼──────────── SERVIDOR MCP (Spring Boot + Spring AI) ────────────┐
│  ToolCallbackProvider ──► HERRAMIENTAS (@Tool en TareaToolsService)                   │
│      listarTareas · listarPendientes · listarCompletadas · contarTareas · buscarTareas│
│      crearTarea · marcarCompletada · actualizarTarea · eliminarTarea                  │
│                     │                                                                 │
│              TareaRepository (Spring Data JPA) ──► MySQL tareas_mcp.tarea             │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

- **Host:** la aplicación con la que interactúa el usuario; contiene el modelo y decide cuándo usar herramientas.
- **Cliente MCP:** componente del host que mantiene *una* sesión con *un* servidor: negocia el protocolo, lista herramientas y envía `tools/call`.
- **Servidor MCP:** proceso que anuncia capacidades y traduce cada llamada a código Java.
- **Herramienta:** una operación concreta con nombre, descripción y esquema de entrada (un método `@Tool`).

### Actividad 14 — Claude Desktop vs Google ADK como clientes

| | Claude Desktop | Google ADK |
|---|---|---|
| Rol | Host listo para usar (app de escritorio) | Framework: el desarrollador programa el host/agente |
| Modelo | Claude | Gemini 3.6 Flash |
| Conexión | Archivo JSON + puente `mcp-remote` (STDIO → HTTP) | Código Java: `McpToolset` + `StreamableHttpServerParameters` (HTTP nativo) |
| Instrucciones | Las define el usuario en el chat | `.instruction(...)` fijada en código |
| Control de herramientas | Muestra cada llamada en el chat (`Tareas-mcp: Listar pendientes`) y puede pedir aprobación antes de ejecutarla, según los permisos del usuario | Ejecuta automáticamente; la confirmación hay que programarla (o pedirla en la instrucción) |
| Respuesta con los mismos datos | Tabla de 4 pendientes; añadió por iniciativa propia que las de abril están vencidas | Lista agrupada por fecha, como se pidió |
| Protocolo negociado | 2025-03-26 (vía mcp-remote) | 2025-06-18 |

**Qué se reutiliza:** el 100 % del servidor — entidad, repositorio, `TareaToolsService`, validaciones y configuración de Spring AI — sin cambiar una línea. Cada cliente sólo aporta su configuración de conexión. Las **descripciones de las herramientas** también se reutilizan: son el "contrato" que ambos modelos leen. Esa es la ventaja de MCP: N clientes × M servidores sin integraciones a la medida.

### Actividad 15 — Pruebas automatizadas

`./mvnw test` → **19 pruebas, 0 fallos**:

| Clase | Tipo | Qué prueba |
|---|---|---|
| `service/TareaToolsServiceTest` (15) | Unitaria con Mockito, sin BD | título obligatorio y recortado; 6 fechas inválidas parametrizadas; fecha nula; `actualizarTarea` no guarda si la fecha es inválida y conserva valores con `null`; ids inexistentes; `contarTareas`; partición completadas/pendientes |
| `repository/TareaRepositoryTest` (2) | `@DataJpaTest` contra MySQL real | consultas derivadas; cada prueba hace *rollback* (la BD queda con sus 3 filas) |
| `TareaMcpApplicationTests` (2) | `@SpringBootTest` | el contexto carga y el `ToolCallbackProvider` publica exactamente las 9 herramientas |

> En `TareaRepositoryTest` se usa `@MockitoBean TareaToolsService`: el *slice* JPA no carga `@Component`, pero `TareaMcpApplication` declara el `@Bean ToolCallbackProvider` que lo necesita (como indica la guía en 7.7).

### Actividad 16 — Riesgo de seguridad: endpoint MCP HTTP sin autenticación

**Riesgo observado:** Spring Boot escucha por defecto en todas las interfaces. Se verificó que `lsof` muestra `java *:8088` y que un `POST /mcp` a la **IP de la red local** (`192.168.x.x:8088`) con un `Origin` ajeno responde `HTTP 200`. Cualquier equipo en la misma red Wi-Fi (o una página web maliciosa mediante *DNS rebinding*) puede abrir una sesión y llamar `tools/call eliminarTarea` en bucle sin credenciales. Además el servidor se conecta a MySQL como `root`, así que un fallo en una herramienta tendría privilegios totales sobre la base.

**Mitigaciones propuestas (de menor a mayor esfuerzo):**

1. Escuchar sólo en loopback — una línea en `application.properties`:
   ```properties
   server.address=127.0.0.1
   ```
2. Validar la cabecera `Origin` (la especificación MCP lo exige para Streamable HTTP) y rechazar orígenes no permitidos con `403`.
3. Autenticación: Spring Security como *OAuth2 Resource Server* (el mecanismo de autorización que define MCP) o, para el laboratorio, un token en cabecera `Authorization: Bearer` que `mcp-remote` puede enviar con `--header`.
4. Usuario de MySQL con privilegios mínimos (`SELECT, INSERT, UPDATE, DELETE` sólo sobre `tareas_mcp.tarea`) en lugar de `root`.
5. Mínimo privilegio en herramientas: no publicar `eliminarTarea` o implementar borrado lógico, y TLS si el servidor sale de `localhost`.

---

## 9. Ejercicio propuesto (supermercado a domicilio)

Resuelto en `ejercicio-supermercado/`; la explicación paso a paso está en `ejercicio-supermercado/EJERCICIO.md`. Se compone de un servidor MCP con 11 herramientas sobre MySQL (catálogo, clientes y pedidos, con reglas de stock, pedido mínimo y domicilio gratis) y de un ChatBot web propio hecho con Spring AI y Gemini que lo consume como cliente MCP. Pruebas: 28 en el servidor y 15 en el chatbot.

La demostración por chat cubrió:

- registro de una clienta nueva;
- un pedido con domicilio gratis confirmado en NEQUI;
- un pedido confirmado en efectivo con su hora de entrega;
- la consulta de estado;
- la creación y cancelación de un pedido;
- la confirmación y la cancelación aprobadas o rechazadas por el cliente con un botón, sin que el modelo pueda ejecutarlas por su cuenta.

Todo quedó verificado en MySQL. Las pruebas destaparon y corrigieron 10 problemas: codificación, fechas, timeouts, número de pedido inventado, errores de herramienta, cuotas, límite por minuto, **IDOR**, a. m./p. m. y una cancelación hecha sin pedir confirmación. Este último se resolvió en el *host*: `confirmarPedido` y `cancelarPedido` solo se ejecutan cuando el cliente pulsa «Aprobar» en la interfaz, como pide la sección 14 de la guía (solicitar confirmación antes de operaciones destructivas o de impacto significativo).

## 10. Estado final

Todas las partes (A–E) y las actividades 10–16 están completas y verificadas. Estado de la base al cierre: 6 tareas (ids 1, 2, 3 del script inicial; 8 y 9 creadas por el agente ADK; 10 creada desde Claude Desktop).

Para volver a levantar el entorno:

```bash
./scripts/iniciar-servidor-mcp.sh     # MySQL (Docker) + MCP Server en :8088
./scripts/iniciar-agente-adk.sh       # UI de ADK en :8080 (en otra terminal)
```
