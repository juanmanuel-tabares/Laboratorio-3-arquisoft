package com.udea.tarea_mcp.service;

import com.udea.tarea_mcp.model.Tarea;
import com.udea.tarea_mcp.repository.TareaRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class TareaToolsService {
    private final TareaRepository repo;

    public TareaToolsService(TareaRepository repo) {
        this.repo = repo;
    }

    @Tool(description = "Devuelve la lista completa de tareas almacenadas.")
    public List<Tarea> listarTareas() {
        return repo.findAll();
    }

    @Tool(description = "Devuelve únicamente las tareas pendientes.")
    public List<Tarea> listarPendientes() {
        return repo.findByCompletado(false);
    }

    // Actividad 10
    @Tool(description = "Devuelve el número total de tareas almacenadas (pendientes y completadas). "
        + "Úsala cuando el usuario pregunte cuántas tareas hay, sin necesidad de listarlas.")
    public long contarTareas() {
        return repo.count();
    }

    // Actividad 11
    @Tool(description = "Devuelve únicamente las tareas que ya fueron completadas.")
    public List<Tarea> listarCompletadas() {
        return repo.findByCompletado(true);
    }

    @Tool(description = "Busca tareas cuyo título contiene el fragmento indicado.")
    public List<Tarea> buscarTareas(
        @ToolParam(description = "Fragmento de texto a buscar en el título")
        String fragmento) {
        return repo.findByTituloContainingIgnoreCase(fragmento);
    }

    @Tool(description = "Crea una nueva tarea con título y fecha; queda pendiente.")
    @Transactional
    public Tarea crearTarea(
        @ToolParam(description = "Título descriptivo de la tarea") String titulo,
        @ToolParam(description = "Fecha en formato ISO yyyy-MM-dd, por ejemplo 2026-11-20") String fecha) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El título es obligatorio");
        }
        return repo.save(new Tarea(titulo.trim(), validarFechaIso(fecha), false));
    }

    @Tool(description = "Marca como completada la tarea indicada por su id.")
    @Transactional
    public Tarea marcarCompletada(
        @ToolParam(description = "Identificador único de la tarea") Long id) {
        Tarea t = repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("No existe la tarea " + id));
        t.setCompletado(true);
        return repo.save(t);
    }

    @Tool(description = "Actualiza el título o fecha de una tarea existente.")
    @Transactional
    public Tarea actualizarTarea(
        @ToolParam(description = "Id de la tarea") Long id,
        @ToolParam(description = "Nuevo título; null para no cambiar") String titulo,
        @ToolParam(description = "Nueva fecha ISO yyyy-MM-dd; null para no cambiar") String fecha) {
        Tarea t = repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("No existe la tarea " + id));
        if (titulo != null && !titulo.isBlank()) t.setTitulo(titulo.trim());
        if (fecha != null && !fecha.isBlank()) t.setFecha(validarFechaIso(fecha));
        return repo.save(t);
    }

    @Tool(description = "Elimina la tarea con el id indicado.")
    @Transactional
    public String eliminarTarea(
        @ToolParam(description = "Id de la tarea a eliminar") Long id) {
        if (!repo.existsById(id)) return "La tarea " + id + " no existe.";
        repo.deleteById(id);
        return "Tarea " + id + " eliminada correctamente.";
    }

    // Actividad 12: la fecha debe ser una fecha real en formato ISO yyyy-MM-dd.
    // ISO_LOCAL_DATE usa ResolverStyle.STRICT, así que también rechaza fechas como 2026-02-30.
    static String validarFechaIso(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            throw new IllegalArgumentException("La fecha es obligatoria y debe tener formato yyyy-MM-dd (ej. 2026-11-20)");
        }
        String valor = fecha.trim();
        try {
            LocalDate.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE);
            return valor;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "La fecha '" + valor + "' no es válida: use el formato ISO yyyy-MM-dd (ej. 2026-11-20)");
        }
    }
}
