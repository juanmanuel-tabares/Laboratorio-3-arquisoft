package com.udea.supermercado_mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglasNegocioTest {

    @ParameterizedTest
    @ValueSource(strings = {"3001234567", "300 123 4567", "+57 300-123-4567", "(300) 1234567"})
    void normalizaCelularesValidos(String entrada) {
        assertThat(ReglasNegocio.normalizarTelefono(entrada)).isEqualTo("3001234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "6041234567", "30012345678", "abc", ""})
    void rechazaTelefonosInvalidos(String entrada) {
        assertThatThrownBy(() -> ReglasNegocio.normalizarTelefono(entrada))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("10 dígitos");
    }

    @Test
    void costoDomicilio() {
        assertThat(ReglasNegocio.costoDomicilio(0)).isZero();
        assertThat(ReglasNegocio.costoDomicilio(99_999)).isEqualTo(6_000);
        assertThat(ReglasNegocio.costoDomicilio(100_000)).isZero();
    }
}
