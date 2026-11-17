package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = {
        TVClient.class,
        RestClientConfig.class,
        ResilienceConfig.class,
        CircuitBreakerLoggingConfig.class
})
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class
})
@ExtendWith(OutputCaptureExtension.class)
public class TVClientCircuitBreakerLoggingTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TVClient tvClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @AfterEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("tvmaze").reset();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tvmaze.base-url", wireMockServer::baseUrl);
        registry.add("resilience4j.retry.instances.tvmaze.max-attempts", () -> "1");
        registry.add("resilience4j.circuitbreaker.instances.tvmaze.wait-duration-in-open-state", () -> "500ms");
    }

    @Test
    void registraLaAperturaEnWarn(CapturedOutput output) {
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> tvClient.getShowById(1L))
                    .isInstanceOf(TvMazeUnavailableException.class);
        }

        var transitionLines = output.getAll().lines()
                .filter(line -> line.contains("CircuitBreakerLoggingConfig"))
                .filter(line -> line.contains("cambió de CLOSED a OPEN"))
                .toList();

        assertThat(transitionLines)
                .hasSize(1)
                .allSatisfy(line -> assertThat(line).contains("WARN", "tvmaze"));
    }

    @Test
    void registraLaSecuenciaCompletaEnOrden(CapturedOutput output) {
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> tvClient.getShowById(1L))
                    .isInstanceOf(TvMazeUnavailableException.class);
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tvmaze");
        await().atMost(3, SECONDS)
                .untilAsserted(() -> assertThat(circuitBreaker.getState())
                        .isEqualTo(CircuitBreaker.State.HALF_OPEN));

        wireMockServer.resetMappings();
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Girls\"}")));

        for (int i = 0; i < 3; i++) {
            assertThat(tvClient.getShowById(1L)).isNotNull();
        }

        await().atMost(3, SECONDS).untilAsserted(() -> assertThat(output.getAll().lines()
                .filter(line -> line.contains("CircuitBreakerLoggingConfig"))
                .filter(line -> line.contains("cambió de"))
                .toList()).hasSize(3));

        var transitionLines = output.getAll().lines()
                .filter(line -> line.contains("CircuitBreakerLoggingConfig"))
                .filter(line -> line.contains("cambió de"))
                .toList();

        assertThat(transitionLines.get(0))
                .contains("WARN", "tvmaze", "cambió de CLOSED a OPEN")
                .doesNotContain("INFO");
        assertThat(transitionLines.get(1))
                .contains("INFO", "tvmaze", "cambió de OPEN a HALF_OPEN")
                .doesNotContain("WARN");
        assertThat(transitionLines.get(2))
                .contains("INFO", "tvmaze", "cambió de HALF_OPEN a CLOSED")
                .doesNotContain("WARN");
    }


    @Test
    void llamadasRechazadasNoGeneranLineas(CapturedOutput output) {
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> tvClient.getShowById(1L))
                    .isInstanceOf(TvMazeUnavailableException.class);
        }

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> tvClient.getShowById(1L))
                    .isInstanceOf(CallNotPermittedException.class);
        }

        var transitionLines = output.getAll().lines()
                .filter(line -> line.contains("CircuitBreakerLoggingConfig"))
                .toList();

        assertThat(transitionLines)
                .hasSize(1)
                .first().asString().contains("cambió de CLOSED a OPEN");
    }

}
