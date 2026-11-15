package com.udea.tarea_mcp.service;

import com.udea.tarea_mcp.model.Tarea;
import com.udea.tarea_mcp.repository.TareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Actividad 15: pruebas unitarias de la lógica de negocio (sin base de datos). */
class TareaToolsServiceTest {

    private TareaRepository repo;
    private TareaToolsService service;

    @BeforeEach
    void setUp() {
        repo = mock(TareaRepository.class);
        when(repo.save(any(Tarea.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new TareaToolsService(repo);
    }

    @Test
    void crearTarea_conDatosValidos_quedaPendienteYSinEspacios() {
        Tarea t = service.crearTarea("  Preparar laboratorio MCP  ", "2026-11-20");

        assertThat(t.getTitulo()).isEqualTo("Preparar laboratorio MCP");
        assertThat(t.getFecha()).isEqualTo("2026-11-20");
        assertThat(t.getCompletado()).isFalse();
        verify(repo).save(t);
    }

    @Test
    void crearTarea_sinTitulo_lanzaError() {
        assertThatThrownBy(() -> service.crearTarea("   ", "2026-11-20"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("título");
        verify(repo, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"20/11/2026", "2026-1-5", "2026-13-01", "2026-02-30", "mañana", "2026/11/20"})
    void crearTarea_conFechaNoIso_lanzaError(String fecha) {
        assertThatThrownBy(() -> service.crearTarea("Tarea", fecha))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("yyyy-MM-dd");
        verify(repo, never()).save(any());
    }

    @Test
    void crearTarea_sinFecha_lanzaError() {
        assertThatThrownBy(() -> service.crearTarea("Tarea", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarTarea_conFechaInvalida_noGuarda() {
        when(repo.findById(1L)).thenReturn(Optional.of(new Tarea("Original", "2026-04-20", false)));

        assertThatThrownBy(() -> service.actualizarTarea(1L, null, "31-12-2026"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void actualizarTarea_conNulls_conservaValores() {
        when(repo.findById(1L)).thenReturn(Optional.of(new Tarea("Original", "2026-04-20", false)));

        Tarea t = service.actualizarTarea(1L, null, null);

        assertThat(t.getTitulo()).isEqualTo("Original");
        assertThat(t.getFecha()).isEqualTo("2026-04-20");
    }

    @Test
    void marcarCompletada_idInexistente_lanzaError() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarCompletada(99L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("99");
    }

    @Test
    void eliminarTarea_idInexistente_noBorra() {
        when(repo.existsById(99L)).thenReturn(false);

        assertThat(service.eliminarTarea(99L)).contains("no existe");
        verify(repo, never()).deleteById(any());
    }

    @Test
    void contarTareas_delegaEnCount() {
        when(repo.count()).thenReturn(3L);
        assertThat(service.contarTareas()).isEqualTo(3L);
    }

    @Test
    void listarCompletadas_yPendientes_consultanEstadosOpuestos() {
        when(repo.findByCompletado(true)).thenReturn(List.of(new Tarea("Hecha", "2026-04-22", true)));
        when(repo.findByCompletado(false)).thenReturn(List.of(new Tarea("Pendiente", "2026-04-20", false)));

        assertThat(service.listarCompletadas()).allMatch(Tarea::getCompletado);
        assertThat(service.listarPendientes()).noneMatch(Tarea::getCompletado);
    }
}
