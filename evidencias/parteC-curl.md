## Parte C - pruebas de herramientas MCP con curl (2026-11-11 21:35)

### initialize
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{"completions":{},"logging":{},"prompts":{"listChanged":true},"resources":{"subscribe":false,"listChanged":true},"tools":{"listChanged":true}},"serverInfo":{"name":"tarea-mcp-server","version":"1.0.0"}}}
Mcp-Session-Id: 0fff080e-bf0d-4413-b382-bcebeb20907a

### 1. listarTareas
[{"id":1,"titulo":"Preparar charla MCP","fecha":"2026-04-20","completado":false,"version":0},{"id":2,"titulo":"Revisar slides","fecha":"2026-04-21","completado":false,"version":0},{"id":3,"titulo":"Probar demo en vivo","fecha":"2026-04-22","completado":true,"version":0}]

### 2. listarPendientes
[{"id":1,"titulo":"Preparar charla MCP","fecha":"2026-04-20","completado":false,"version":0},{"id":2,"titulo":"Revisar slides","fecha":"2026-04-21","completado":false,"version":0}]

### 3. buscarTareas (slides)
[{"id":2,"titulo":"Revisar slides","fecha":"2026-04-21","completado":false,"version":0}]

### 4. crearTarea
{"id":4,"titulo":"Tarea de prueba curl","fecha":"2026-11-20","completado":false,"version":0}

### 5. marcarCompletada (id=4)
{"id":4,"titulo":"Tarea de prueba curl","fecha":"2026-11-20","completado":true,"version":1}

### 6. actualizarTarea (id=4)
{"id":4,"titulo":"Tarea de prueba curl (editada)","fecha":"2026-11-21","completado":true,"version":2}

### SELECT en MySQL tras crear/completar/actualizar
+----+--------------------------------+------------+------------+---------+
| id | titulo                         | fecha      | completado | version |
+----+--------------------------------+------------+------------+---------+
|  1 | Preparar charla MCP            | 2026-04-20 |          0 |       0 |
|  2 | Revisar slides                 | 2026-04-21 |          0 |       0 |
|  3 | Probar demo en vivo            | 2026-04-22 |          1 |       0 |
|  4 | Tarea de prueba curl (editada) | 2026-11-21 |          1 |       2 |
+----+--------------------------------+------------+------------+---------+

### 7. eliminarTarea (id=4)
"Tarea 4 eliminada correctamente."

### 7b. eliminarTarea de un id inexistente (999)
"La tarea 999 no existe."

### Error controlado: marcarCompletada id inexistente
[isError] No existe la tarea 999

### SELECT final
+----+---------------------+------------+------------+
| id | titulo              | fecha      | completado |
+----+---------------------+------------+------------+
|  1 | Preparar charla MCP | 2026-04-20 |          0 |
|  2 | Revisar slides      | 2026-04-21 |          0 |
|  3 | Probar demo en vivo | 2026-04-22 |          1 |
+----+---------------------+------------+------------+
