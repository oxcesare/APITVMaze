package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.mx.tvmazemiddleware.exception.TvMazeClientErrorException;
import com.mx.tvmazemiddleware.exception.TvMazeRateLimitException;
import com.mx.tvmazemiddleware.exception.TvMazeTimeoutException;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import com.mx.tvmazemiddleware.exception.TvNotShowException;
import com.mx.tvmazemiddleware.exception.TvShowSearchEmptyException;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {TVClient.class, RestClientConfig.class, ResilienceConfig.class})
@ImportAutoConfiguration({AopAutoConfiguration.class, RetryAutoConfiguration.class})
public class TVClientRetryTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TVClient tvClient;

    @BeforeAll
    static void setUpWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
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
        wireMockServer.resetScenarios();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tvmaze.base-url", wireMockServer::baseUrl);
        registry.add("app.tvmaze.read-timeout", () -> "300ms");
        registry.add("app.tvmaze.connect-timeout", () -> "300ms");
        registry.add("resilience4j.retry.instances.tvmaze.max-attempts", () -> "2");
        registry.add("resilience4j.retry.instances.tvmaze.retry-exceptions[0]",
                () -> "com.mx.tvmazemiddleware.exception.TvMazeTimeoutException");
        registry.add("resilience4j.retry.instances.tvmaze.retry-exceptions[1]",
                () -> "com.mx.tvmazemiddleware.exception.TvMazeUnavailableException");
        registry.add("resilience4j.retry.instances.tvmaze.ignore-exceptions[0]",
                () -> "com.mx.tvmazemiddleware.exception.TvMazeClientErrorException");
        registry.add("tvmaze.retry.initial-interval", () -> "10ms");
        registry.add("tvmaze.retry.multiplier", () -> "2.0");
        registry.add("tvmaze.retry.jitter", () -> "0.0");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("metodosTvMaze")
    void reintentaTrasTimeoutYTerminaBien(String nombre, String url, String body,
                                         Function<TVClient, Long> invocar) {
        String scenarioName = "Timeout Retry Scenario";

        wireMockServer.stubFor(get(urlEqualTo(url))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withFixedDelay(1_000) // Mayor al timeout configurado (300ms)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}"))
                .willSetStateTo("First Attempt Failed"));

        wireMockServer.stubFor(get(urlEqualTo(url))
                .inScenario(scenarioName)
                .whenScenarioStateIs("First Attempt Failed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        assertThat(invocar.apply(tvClient)).isEqualTo(1L);
        wireMockServer.verify(2, getRequestedFor(urlEqualTo(url)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("metodosTvMaze")
    void reintentaTras503(String nombre, String url, String body, Function<TVClient, Long> invocar) {
        String scenarioName = "HTTP 503 Retry Scenario";

        wireMockServer.stubFor(get(urlEqualTo(url))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json"))
                .willSetStateTo("First Attempt Failed"));


        wireMockServer.stubFor(get(urlEqualTo(url))
                .inScenario(scenarioName)
                .whenScenarioStateIs("First Attempt Failed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        assertThat(invocar.apply(tvClient)).isEqualTo(1L);
        wireMockServer.verify(2, getRequestedFor(urlEqualTo(url)));
    }

    private static Stream<Arguments> metodosTvMaze() {
        return Stream.of(
                Arguments.of("getShowById", "/shows/1", "{\"id\": 1, \"name\": \"Under the Dome\"}",
                        (Function<TVClient, Long>) client -> client.getShowById(1L).id()),
                Arguments.of("getEpisodeById", "/episodes/1", "{\"id\": 1, \"name\": \"Pilot\"}",
                        (Function<TVClient, Long>) client -> client.getEpisodeById(1L).id()),
                Arguments.of("searchShows", "/search/shows?q=dome",
                        "[{\"show\":{\"id\": 1, \"name\": \"Under the Dome\"}}]",
                        (Function<TVClient, Long>) client -> client.searchShows("dome").get(0).getId())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("metodosTvMaze")
    void agotaIntentosYPropagaTimeout(String nombre, String url, String body,
                                       Function<TVClient, ?> invocar) {
        wireMockServer.stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withFixedDelay(1_000)));

        assertThatThrownBy(() -> invocar.apply(tvClient))
                .isExactlyInstanceOf(TvMazeTimeoutException.class)
                .isNotInstanceOf(TvMazeUnavailableException.class);
        wireMockServer.verify(2, getRequestedFor(urlEqualTo(url)));
    }

    @ParameterizedTest(name = "{0} HTTP {3}")
    @MethodSource("metodosYEstados4xx")
    void clasifica4xxSinReintentar(String nombre, String url, Function<TVClient, ?> invocar, int status) {
        wireMockServer.stubFor(get(urlEqualTo(url))
                .willReturn(aResponse().withStatus(status).withBody("CANARIO-4XX-123")));

        Class<? extends RuntimeException> expected = switch (status) {
            case 400, 401, 403 -> TvMazeClientErrorException.class;
            case 404 -> nombre.equals("searchShows") ? TvShowSearchEmptyException.class : TvNotShowException.class;
            case 429 -> TvMazeRateLimitException.class;
            default -> throw new IllegalStateException("Estado inesperado: " + status);
        };
        var assertion = assertThatThrownBy(() -> invocar.apply(tvClient)).isExactlyInstanceOf(expected);
        assertion.satisfies(ex -> assertThat(ex.getMessage())
                .doesNotContain("CANARIO-4XX-123")
                .doesNotContain(wireMockServer.baseUrl()));
        if (expected == TvMazeClientErrorException.class) {
            assertion.hasMessageContaining(String.valueOf(status));
        }
        wireMockServer.verify(1, getRequestedFor(urlEqualTo(url)));
    }

    private static Stream<Arguments> metodosYEstados4xx() {
        return metodosTvMaze().flatMap(method -> {
            Object[] args = method.get();
            return IntStream.of(400, 401, 403, 404, 429)
                    .mapToObj(status -> Arguments.of(args[0], args[1], args[3], status));
        });
    }

    @Test
    void agotaIntentosYPropagaExcepcion() {
        String scenarioName = "Exhausted Retries Scenario";

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("CANARIO-4XX-123"))
                .willSetStateTo("First Attempt Failed"));

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("First Attempt Failed")
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("CANARIO-4XX-123"))
                .willSetStateTo("Second Attempt Failed"));

        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isInstanceOf(TvMazeUnavailableException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .doesNotContain("CANARIO-4XX-123")
                        .doesNotContain(wireMockServer.baseUrl()));
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/shows/1")));
    }

    @Test
    void latenciaTotalAcotada() {
        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withFixedDelay(5000)
                        .withHeader("Content-Type", "application/json")));

        long inicio = System.currentTimeMillis();


        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isInstanceOf(com.mx.tvmazemiddleware.exception.TvMazeTimeoutException.class);

        long duracion = System.currentTimeMillis() - inicio;

        assertThat(duracion).isLessThan(3000);

        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/shows/1")));
    }
}
