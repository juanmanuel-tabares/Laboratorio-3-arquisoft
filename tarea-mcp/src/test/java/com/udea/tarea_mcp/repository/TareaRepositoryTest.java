package com.udea.tarea_mcp.repository;

import com.udea.tarea_mcp.model.Tarea;
import com.udea.tarea_mcp.service.TareaToolsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actividad 15: prueba del repositorio contra la base MySQL real.
 * @DataJpaTest abre una transacción por prueba y hace rollback al final,
 * así que los datos de laboratorio no cambian.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TareaRepositoryTest {

    // El slice JPA no carga @Component; TareaMcpApplication declara un @Bean que lo necesita.
    @MockitoBean
    private TareaToolsService tareaToolsService;

    @Autowired
    private TareaRepository repo;

    @Test
    void findByCompletado_separaPendientesDeCompletadas() {
        repo.save(new Tarea("ZZ prueba pendiente", "2026-11-20", false));
        repo.save(new Tarea("ZZ prueba completada", "2026-11-21", true));

        assertThat(repo.findByCompletado(false))
            .extracting(Tarea::getTitulo).contains("ZZ prueba pendiente")
            .doesNotContain("ZZ prueba completada");
        assertThat(repo.findByCompletado(true))
            .extracting(Tarea::getTitulo).contains("ZZ prueba completada")
            .doesNotContain("ZZ prueba pendiente");
    }

    @Test
    void findByTituloContainingIgnoreCase_ignoraMayusculas() {
        repo.save(new Tarea("ZZ Revisar INFORME final", "2026-11-22", false));

        assertThat(repo.findByTituloContainingIgnoreCase("informe"))
            .extracting(Tarea::getTitulo).contains("ZZ Revisar INFORME final");
    }
}
