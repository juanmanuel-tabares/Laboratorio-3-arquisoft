# Laboratorio 3: MCP con Spring Boot, Spring AI, Claude Desktop y Google ADK

**Asignatura:** Arquitectura de Software  
**Autor:** Juan Manuel Tabares Uribe

Implementación de un servidor MCP (Model Context Protocol) con Spring Boot y Spring AI sobre MySQL, consumido por dos clientes distintos: Claude Desktop y un agente de Google ADK para Java. Incluye las actividades de la sección 15 de la guía y el ejercicio propuesto: un ChatBot de pedidos a domicilio para un supermercado, integrado con su propio servidor MCP.

## Documentos

| Documento | Contenido |
|---|---|
| [INFORME.md](INFORME.md) | Informe del laboratorio: partes A a E, tabla de pruebas y actividades 10 a 16. |
| [ejercicio-supermercado/EJERCICIO.md](ejercicio-supermercado/EJERCICIO.md) | Ejercicio propuesto explicado paso a paso: análisis, arquitectura, base de datos, servidor MCP, chatbot, pruebas y problemas resueltos. |

## Estructura

| Carpeta | Qué contiene |
|---|---|
| `tarea-mcp/` | Servidor MCP de tareas (Spring Boot 3.4.7, Spring AI 1.1.4, JPA, MySQL), puerto 8088. |
| `agente-mcp/` | Agente Google ADK Java 1.0.0 con Gemini que consume el servidor de tareas. |
| `scripts/` | Arranque del servidor y del agente, cliente MCP mínimo con curl y configuración de Claude Desktop y Gemini. |
| `evidencias/` | Salidas de consola, consultas a MySQL y capturas de cada parte del laboratorio. |
| `ejercicio-supermercado/supermercado-mcp/` | Servidor MCP del supermercado: catálogo, clientes y pedidos sobre MySQL, puerto 8090. |
| `ejercicio-supermercado/chatbot-supermercado/` | ChatBot web (Spring AI + Gemini) que actúa como host y cliente MCP, puerto 8091. |
| `ejercicio-supermercado/evidencias/` | Capturas del chat, pruebas del protocolo con curl y estado de la base. |

## Requisitos

- JDK 21 (cada proyecto trae Maven Wrapper: `mvnw` / `mvnw.cmd`).
- MySQL 8 (local o en Docker).
- Node.js 18 o superior, solo para MCP Inspector y el puente `mcp-remote` de Claude Desktop.
- Una clave de la API de Gemini para el agente ADK y el chatbot.

Las claves no están en el repositorio. Copie `.env.example` como `.env` y complete los valores; los scripts lo cargan antes de arrancar.

## Ejecución en macOS o Linux

```bash
cp .env.example .env                                  # completar PWD_UDEA, MYSQL_PORT y GOOGLE_API_KEY
./scripts/iniciar-servidor-mcp.sh                     # servidor de tareas en :8088
./scripts/iniciar-agente-adk.sh                       # en otra terminal: UI de ADK en http://localhost:8080

cd ejercicio-supermercado
./scripts/cargar-base.sh                              # base "supermercado" con su catálogo
./scripts/iniciar-supermercado-mcp.sh                 # servidor MCP en :8090
./scripts/iniciar-chatbot.sh                          # en otra terminal: chatbot en http://localhost:8091
```

## Ejecución en Windows (PowerShell)

```powershell
$env:PWD_UDEA = "clave_de_root_de_mysql"
$env:MYSQL_PORT = "3306"
$env:GOOGLE_API_KEY = "clave_de_gemini"

# Laboratorio
Get-Content tarea-mcp\src\main\resources\schema.sql | mysql -u root -p
cd tarea-mcp; .\mvnw.cmd spring-boot:run                          # :8088
cd ..\agente-mcp; .\mvnw.cmd clean install; .\mvnw.cmd exec:java@runAdkWebServer   # :8080

# Ejercicio
Get-Content ejercicio-supermercado\supermercado-mcp\src\main\resources\schema.sql -Encoding UTF8 | mysql --default-character-set=utf8mb4 -u root -p
cd ejercicio-supermercado\supermercado-mcp; .\mvnw.cmd spring-boot:run      # :8090
cd ..\chatbot-supermercado; .\mvnw.cmd spring-boot:run                      # :8091
```

Cada comando `spring-boot:run` se deja corriendo en su propia ventana de PowerShell, con las variables definidas en esa ventana.

## Pruebas automatizadas

```bash
cd tarea-mcp && ./mvnw test
```

| Proyecto | Pruebas |
|---|---|
| `tarea-mcp` | 19 |
| `agente-mcp` | 1 |
| `ejercicio-supermercado/supermercado-mcp` | 28 |
| `ejercicio-supermercado/chatbot-supermercado` | 15 |

Las pruebas que cargan el contexto de Spring o usan `@DataJpaTest` necesitan MySQL con la base correspondiente ya creada.
