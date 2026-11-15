## Actividades 10-12 - nuevas herramientas y validación ISO (2026-11-11 21:42)

### tools/list
TOTAL 9
- buscarTareas : Busca tareas cuyo título contiene el fragmento indicado.
- listarCompletadas : Devuelve únicamente las tareas que ya fueron completadas.
- crearTarea : Crea una nueva tarea con título y fecha; queda pendiente.
- listarPendientes : Devuelve únicamente las tareas pendientes.
- eliminarTarea : Elimina la tarea con el id indicado.
- listarTareas : Devuelve la lista completa de tareas almacenadas.
- marcarCompletada : Marca como completada la tarea indicada por su id.
- contarTareas : Devuelve el número total de tareas almacenadas (pendientes y completadas). Úsala cuando el usuario pregunte cuántas tareas hay, sin necesidad de listarlas.
- actualizarTarea : Actualiza el título o fecha de una tarea existente.

### Actividad 10 - contarTareas
3

### Actividad 11 - listarCompletadas
[{"id":3,"titulo":"Probar demo en vivo","fecha":"2026-04-22","completado":true,"version":0}]

### Actividad 11 - listarPendientes (comparación)
[{"id":1,"titulo":"Preparar charla MCP","fecha":"2026-04-20","completado":false,"version":0},{"id":2,"titulo":"Revisar slides","fecha":"2026-04-21","completado":false,"version":0}]

### Actividad 12 - crearTarea con fecha no ISO (20/11/2026)
[isError] La fecha '20/11/2026' no es válida: use el formato ISO yyyy-MM-dd (ej. 2026-11-20)

### Actividad 12 - crearTarea con fecha inexistente (2026-02-30)
[isError] La fecha '2026-02-30' no es válida: use el formato ISO yyyy-MM-dd (ej. 2026-11-20)

### Actividad 12 - actualizarTarea con fecha no ISO
[isError] La fecha '2026/04/25' no es válida: use el formato ISO yyyy-MM-dd (ej. 2026-11-20)

### contarTareas tras los intentos (debe seguir igual)
3
