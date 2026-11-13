package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {TVClient.class, RestClientConfig.class, ResilienceConfig.class})
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class
})
public class TVClientCircuitBreakerRetryTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TVClient tvClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void setUpWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.resetAll();
    }

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("tvmaze").reset();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tvmaze.base-url", wireMockServer::baseUrl);
        registry.add("app.tvmaze.read-timeout", () -> "1s");
        registry.add("app.tvmaze.connect-timeout", () -> "1s");
        registry.add("resilience4j.retry.instances.tvmaze.max-attempts", () -> "3");
        registry.add("tvmaze.retry.initial-interval", () -> "10ms");
        registry.add("tvmaze.retry.jitter", () -> "0.0");
    }


    @Test
    void noReintentaConCircuitoAbierto() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tvmaze");

        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse().withStatus(503)));

        // 1. Llamar (máx. 5 llamadas lógicas) hasta que el breaker esté OPEN, capturando las excepciones
        List<Exception> thrown = new ArrayList<>();
        int calls = 0;
        while (circuitBreaker.getState() != CircuitBreaker.State.OPEN && calls < 5) {
            try {
                tvClient.getShowById(1L);
            } catch (Exception e) {
                thrown.add(e);
            }
            calls++;
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        // La 2ª llamada lógica termina con CallNotPermittedException: su 3er intento ya encuentra el circuito
        // abierto y esa excepción no se reintenta
        assertThat(thrown).hasSize(2);
        assertThat(thrown.get(0)).isExactlyInstanceOf(TvMazeUnavailableException.class);
        assertThat(thrown.get(1)).isExactlyInstanceOf(CallNotPermittedException.class);

        // 2. Anotar el número de llamadas no permitidas antes de la prueba y limpiar peticiones de WireMock
        long notPermittedBefore = circuitBreaker.getMetrics().getNumberOfNotPermittedCalls();
        wireMockServer.resetRequests();

        // 3. Ejecutar una llamada única con el circuito ya abierto
        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isExactlyInstanceOf(CallNotPermittedException.class)
                .satisfies(exception -> assertThat(((CallNotPermittedException) exception)
                        .getCausingCircuitBreakerName()).isEqualTo("tvmaze"));

        // 4. Validar que el incremento en getNumberOfNotPermittedCalls() sea exactamente 1 (y no 3 por culpa de reintentos)
        long notPermittedAfter = circuitBreaker.getMetrics().getNumberOfNotPermittedCalls();
        assertThat(notPermittedAfter - notPermittedBefore).isEqualTo(1);

        // Comprobar que WireMock no recibió ninguna petición de red
        wireMockServer.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void cadaIntentoCuentaEnLaVentana() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tvmaze");

        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse().withStatus(503)));

        // Una sola llamada lógica: Retry (exterior) reintenta 3 veces a través del breaker (interior)
        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isExactlyInstanceOf(TvMazeUnavailableException.class);

        // Cada intento se registra en la ventana; con el breaker por fuera sería 1
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/shows/1")));
    }


}
