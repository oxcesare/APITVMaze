package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.exception.TvMazeOverloadedException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "resilience4j.bulkhead.instances.tvmaze.max-concurrent-calls=2",
        "resilience4j.bulkhead.instances.tvmaze.max-wait-duration=0"},
        classes = {
                TVClient.class,
                RestClientConfig.class,
                ResilienceConfig.class,
                CircuitBreakerLoggingConfig.class
        })
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        BulkheadAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class
})
@ExtendWith(OutputCaptureExtension.class)
public class TVClientBulkheadTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TVClient tvClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tvmaze.base-url", wireMockServer::baseUrl);
        registry.add("app.tvmaze.read-timeout", () -> "5s");
        registry.add("app.tvmaze.connect-timeout", () -> "1s");
        registry.add("resilience4j.retry.instances.tvmaze.max-attempts", () -> "1");
    }

    @Test
    void bulkheadBajoElLimite() throws Exception {
        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Girls\"}")
                        .withFixedDelay(600)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var primero = executor.submit(() -> tvClient.getShowById(1L));
            var segundo = executor.submit(() -> tvClient.getShowById(1L));

            await().atMost(2, SECONDS).untilAsserted(() ->
                    assertThat(bulkheadRegistry.bulkhead("tvmaze").getMetrics().getAvailableConcurrentCalls())
                            .isEqualTo(0));

            assertThat(primero.get(5, SECONDS)).isNotNull();
            assertThat(segundo.get(5, SECONDS)).isNotNull();
            assertThat(bulkheadRegistry.bulkhead("tvmaze").getMetrics().getAvailableConcurrentCalls())
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void excedenteSeRechazaDeInmediato() throws Exception {
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Girls\"}")
                        .withFixedDelay(800)));

        var listas = new CountDownLatch(5);
        var arranque = new CountDownLatch(1);
        List<Future<Resultado>> tareas = new ArrayList<>();
        List<Resultado> resultados = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                tareas.add(executor.submit(() -> {
                    listas.countDown();
                    arranque.await();
                    long inicio = System.nanoTime();
                    try {
                        var show = tvClient.getShowById(1L);
                        return new Resultado(show, null, System.nanoTime() - inicio);
                    } catch (Throwable ex) {
                        return new Resultado(null, ex, System.nanoTime() - inicio);
                    }
                }));
            }

            try {
                assertThat(listas.await(5, SECONDS)).isTrue();
            } finally {
                arranque.countDown();
            }
            for (var tarea : tareas) {
                resultados.add(tarea.get(5, SECONDS));
            }
        }

        var exitos = resultados.stream().filter(resultado -> resultado.error() == null).toList();
        var fallos = resultados.stream().filter(resultado -> resultado.error() != null).toList();
        assertThat(exitos).hasSize(2).allSatisfy(resultado -> assertThat(resultado.show()).isNotNull());
        assertThat(fallos).hasSize(3);

        wireMockServer.verify(2, getRequestedFor(urlPathEqualTo("/shows/1")));

        assertThat(fallos).allSatisfy(resultado ->
                assertThat(resultado.duracionNanos()).isLessThan(TimeUnit.MILLISECONDS.toNanos(500)));

        assertThat(fallos).allSatisfy(resultado ->
                assertThat(resultado.error()).isInstanceOf(TvMazeOverloadedException.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("metodosDelCliente")
    void elExcedenteSeTraduceEnCadaMetodo(String nombre, String ruta, Function<TVClient, Object> metodo)
            throws Exception {
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlEqualTo(ruta))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("null")
                        .withFixedDelay(800)));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primero = executor.submit(() -> metodo.apply(tvClient));
            var segundo = executor.submit(() -> metodo.apply(tvClient));
            try {
                await().atMost(2, SECONDS).untilAsserted(() ->
                        assertThat(bulkheadRegistry.bulkhead("tvmaze").getMetrics().getAvailableConcurrentCalls())
                                .isZero());
                assertThatThrownBy(() -> metodo.apply(tvClient))
                        .isInstanceOf(TvMazeOverloadedException.class);
            } finally {
                for (var tarea : List.of(primero, segundo)) {
                    try {
                        tarea.get(5, SECONDS);
                    } catch (ExecutionException ignored) {
                        // The occupying requests need not deserialize successfully.
                    }
                }
                await().atMost(2, SECONDS).untilAsserted(() ->
                        assertThat(bulkheadRegistry.bulkhead("tvmaze").getMetrics().getAvailableConcurrentCalls())
                                .isEqualTo(2));
            }
        }
    }

    static Stream<Arguments> metodosDelCliente() {
        return Stream.of(
                Arguments.of("getShowById", "/shows/1", (Function<TVClient, Object>) client -> client.getShowById(1L)),
                Arguments.of("searchShows", "/search/shows?q=girls",
                        (Function<TVClient, Object>) client -> client.searchShows("girls")),
                Arguments.of("getEpisodeById", "/episodes/1",
                        (Function<TVClient, Object>) client -> client.getEpisodeById(1L)));
    }

    private record Resultado(ShowDetailResponse show, Throwable error, long duracionNanos) {
    }

}
