package com.udea.chatbot_supermercado.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Reemplaza el cliente de Gemini que crea Spring AI (es @ConditionalOnMissingBean) para acotar los tiempos.
 * Por defecto el SDK no tiene timeout de lectura: una respuesta colgada del servicio bloqueaba el chat
 * indefinidamente. Ahora cada llamada tiene un tiempo máximo total y reintentos con espera creciente.
 *
 * El 429 se reintenta porque el plan gratuito también limita las peticiones POR MINUTO y un turno con
 * varias herramientas encadena varias llamadas seguidas. Si lo agotado es la cuota DIARIA, los reintentos
 * fallan igual y el usuario recibe el mensaje de límite alcanzado.
 */
@Configuration
public class GeminiConfig {

    @Bean
    public Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
                                    @Value("${chatbot.gemini.timeout:60s}") Duration timeout) {
        HttpRetryOptions reintentos = HttpRetryOptions.builder()
            // Esperas de ~5, 10, 20 y 30 s: suman algo más de un minuto, la ventana del límite por minuto.
            .attempts(5)
            .initialDelay(5.0)
            .maxDelay(30.0)
            .httpStatusCodes(List.of(408, 429, 500, 502, 503, 504))
            .build();
        return Client.builder()
            .apiKey(apiKey)
            .httpOptions(HttpOptions.builder()
                .timeout((int) timeout.toMillis())   // tiempo máximo total de cada llamada al modelo
                .retryOptions(reintentos)
                .build())
            .build();
    }
}
