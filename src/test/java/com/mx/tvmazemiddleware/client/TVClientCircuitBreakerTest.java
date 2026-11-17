package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mx.tvmazemiddleware.exception.TvMazeClientErrorException;
import com.mx.tvmazemiddleware.exception.TvMazeRateLimitException;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import com.mx.tvmazemiddleware.exception.TvNotShowException;
import com.mx.tvmazemiddleware.exception.TvShowSearchEmptyException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.stream.Stream;
import java.time.Duration;

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
public class TVClientCircuitBreakerTest {

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
        registry.add("resilience4j.retry.instances.tvmaze.max-attempts", () -> "1");
    }

    @Test
    void permaneceCerradoConExitos() {
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Girls\"}")));

        for (int i = 0; i < 4; i++) {
            assertThat(tvClient.getShowById(1L)).isNotNull();
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tvmaze");
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(4);
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(0);
        wireMockServer.verify(4, getRequestedFor(urlPathEqualTo("/shows/1")));
    }

    @ParameterizedTest(name = "Secuencia {0} resulta en estado {1}")
    @MethodSource("sequenceProvider")
    void seAbreAlSuperarElUmbral(String sequence, CircuitBreaker.State expectedState) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tvmaze");

        for (char callType : sequence.toCharArray()) {
            wireMockServer.resetMappings();
            if (callType == 'S') {
                wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"id\":1,\"name\":\"Girls\"}")));
                assertThat(tvClient.getShowById(1L)).isNotNull();
            } else if (callType == 'F') {
                wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                        .willReturn(aResponse().withStatus(503)));
                assertThatThrownBy(() -> tvClient.getShowById(1L))
                        .isInstanceOf(TvMazeUnavailableException.class);
            } else {
                throw new IllegalArgumentException("Tipo de llamada desconocido: " + callType);
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(expectedState);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls())
                .isEqualTo(sequence.chars().filter(call -> call == 'S').count());
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls())
                .isEqualTo(sequence.chars().filter(call -> call == 'F').count());
    }

    static Stream<Arguments> sequenceProvider() {
        return Stream.of(
                Arguments.of("SSSSS", CircuitBreaker.State.CLOSED),
                Arguments.of("FSSSS", CircuitBreaker.State.CLOSED),
                Arguments.of("FFFSS", CircuitBreaker.State.OPEN),
                Arguments.of("SSFFF", CircuitBreaker.State.OPEN),
                Arguments.of("FFFFS", CircuitBreaker.State.OPEN),
                Arguments.of("FFF", CircuitBreaker.State.CLOSED),
                // With a minimum of five calls, reach exactly 50% only at the tenth call.
                Arguments.of("FSFSSSFSFF", CircuitBreaker.State.OPEN),
                Arguments.of("SFSFSSFSFF", CircuitBreaker.State.OPEN),
                // 40% failures with the window of 10 full stays below the threshold.
                Arguments.of("FSFSSSFSSF", CircuitBreaker.State.CLOSED)
        );
    }

    @Test
    void conCircuitoAbiertoFallaRapido() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tvmaze");

        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse().withStatus(503)));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> tvClient.getShowById(1L))
                    .isInstanceOf(TvMazeUnavailableException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        wireMockServer.resetRequests();
        wireMockServer.resetMappings();
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1000)));

        long startTime = System.nanoTime();
        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isExactlyInstanceOf(CallNotPermittedException.class)
                .satisfies(exception -> assertThat(((CallNotPermittedException) exception)
                        .getCausingCircuitBreakerName()).isEqualTo("tvmaze"));
        long elapsedNanos = System.nanoTime() - startTime;

        wireMockServer.verify(0, anyRequestedFor(anyUrl()));
        assertThat(elapsedNanos).isLessThan(Duration.ofMillis(200).toNanos());
    }

    @Test
    void elCircuitoEsCompartidoEntreLasTresOperaciones() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tvmaze");

        wireMockServer.stubFor(get(urlPathEqualTo("/search/shows"))
                .willReturn(aResponse().withStatus(503)));
        wireMockServer.stubFor(get(urlPathEqualTo("/episodes/1"))
                .willReturn(aResponse().withStatus(503)));
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Girls\"}")));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> tvClient.searchShows("girls"))
                    .isInstanceOf(TvMazeUnavailableException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> tvClient.getEpisodeById(1L))
                .isInstanceOf(CallNotPermittedException.class);
        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isInstanceOf(CallNotPermittedException.class);

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/episodes/1")));
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/shows/1")));
    }

    @Test
    void cincoRespuestas429AbrenElCircuito() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tvmaze");

        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse().withStatus(429)));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> tvClient.getShowById(1L))
                    .isInstanceOf(TvMazeRateLimitException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        wireMockServer.verify(5, getRequestedFor(urlPathEqualTo("/shows/1")));

        wireMockServer.resetRequests();

        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isExactlyInstanceOf(CallNotPermittedException.class);
        wireMockServer.verify(0, anyRequestedFor(anyUrl()));
    }

    static Stream<Arguments> nonFailureCasesProvider() {
        return Stream.of(
                Arguments.of("GET_SHOW_404", 404, "/shows/1", TvNotShowException.class),
                Arguments.of("GET_EPISODE_404", 404, "/episodes/1", TvNotShowException.class),
                Arguments.of("CLIENT_ERROR_400", 400, "/shows/1", TvMazeClientErrorException.class),
                Arguments.of("CLIENT_ERROR_401", 401, "/shows/1", TvMazeClientErrorException.class),
                Arguments.of("CLIENT_ERROR_403", 403, "/shows/1", TvMazeClientErrorException.class),
                Arguments.of("SEARCH_EMPTY_200", 200, "/search/shows?q=unknown", TvShowSearchEmptyException.class)
        );
    }

    @ParameterizedTest(name = "Caso {0} no afecta la salud del circuito y cuenta como éxito")
    @MethodSource("nonFailureCasesProvider")
    void noCuentaComoFalloLosErroresDelCliente(String caseName, int statusCode, String endpoint,
                                         Class<? extends RuntimeException> expectedException) {

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tvmaze");

        String path = endpoint.split("\\?")[0];

        wireMockServer.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody(path.contains("search") ? "[]" : "")));

        Runnable request;
        if (path.contains("search")) {
            request = () -> tvClient.searchShows("unknown");
        } else if (path.contains("episodes")) {
            request = () -> tvClient.getEpisodeById(1L);
        } else {
            request = () -> tvClient.getShowById(1L);
        }

        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(request::run).isExactlyInstanceOf(expectedException);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isGreaterThan(0);
        wireMockServer.verify(20, getRequestedFor(urlPathEqualTo(path)));
    }
}
