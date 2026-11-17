package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {TVClient.class, RestClientConfig.class, ResilienceConfig.class})
@ImportAutoConfiguration({AopAutoConfiguration.class, RetryAutoConfiguration.class})
class TVClientBackoffTest {

    private static final long DELAY_INICIAL_MS = 200;

    private static WireMockServer wireMockServer;

    @Autowired
    private TVClient tvClient;

    @Autowired
    private RetryRegistry retryRegistry;

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
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tvmaze.base-url", wireMockServer::baseUrl);
        registry.add("app.tvmaze.read-timeout", () -> "1s");
        registry.add("app.tvmaze.connect-timeout", () -> "1s");
        registry.add("resilience4j.retry.instances.tvmaze.max-attempts", () -> "3");
        registry.add("tvmaze.retry.initial-interval", () -> DELAY_INICIAL_MS + "ms");
        registry.add("tvmaze.retry.multiplier", () -> "2");
        registry.add("tvmaze.retry.jitter", () -> "0");
    }

    @Test
    void esperaCreceExponencialmente() {
        String scenario = "Exponential Backoff Scenario";

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Attempt 1 Failed"));

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenario)
                .whenScenarioStateIs("Attempt 1 Failed")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Attempt 2 Failed"));

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenario)
                .whenScenarioStateIs("Attempt 2 Failed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 1, \"name\": \"Under the Dome\"}")));

        List<Duration> intervalos = new CopyOnWriteArrayList<>();
        retryRegistry.retry("tvmaze").getEventPublisher()
                .onRetry(e -> intervalos.add(e.getWaitInterval()));

        var response = tvClient.getShowById(1L);

        assertThat(response.id()).isEqualTo(1L);

        List<Long> instantes = wireMockServer.findAll(getRequestedFor(urlEqualTo("/shows/1"))).stream()
                .map(r -> r.getLoggedDate().getTime())
                .sorted()
                .toList();
        assertThat(instantes).hasSize(3);

        long espera1 = instantes.get(1) - instantes.get(0);
        long espera2 = instantes.get(2) - instantes.get(1);

        assertThat(espera1).isGreaterThanOrEqualTo(DELAY_INICIAL_MS);
        assertThat(espera2).isGreaterThanOrEqualTo(DELAY_INICIAL_MS * 2);

        assertThat(intervalos).containsExactly(
                Duration.ofMillis(DELAY_INICIAL_MS), Duration.ofMillis(DELAY_INICIAL_MS * 2));
    }
}
