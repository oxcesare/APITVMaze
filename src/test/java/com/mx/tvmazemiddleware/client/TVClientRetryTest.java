package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {TVClient.class,RestClientConfig.class})
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
    }

    @Test
    void reintentaTrasTimeoutYTerminaBien() {
        String scenarioName = "Timeout Retry Scenario";

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withFixedDelay(1_000) // Mayor al timeout configurado (300ms)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}"))
                .willSetStateTo("First Attempt Failed"));

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("First Attempt Failed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 1, \"name\": \"Under the Dome\"}")));


        var response = tvClient.getShowById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);

        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/shows/1")));
    }

    @Test
    void reintentaTras503() {

        String scenarioName = "HTTP 503 Retry Scenario";


        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json"))
                .willSetStateTo("First Attempt Failed"));


        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("First Attempt Failed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 1, \"name\": \"Under the Dome\"}")));


        var response = tvClient.getShowById(1L);


        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);


        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/shows/1")));
    }

    @Test
    void agotaIntentosYPropagaExcepcion() {
        String scenarioName = "Exhausted Retries Scenario";

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json"))
                .willSetStateTo("First Attempt Failed"));

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("First Attempt Failed")
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json"))
                .willSetStateTo("Second Attempt Failed"));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.mx.tvmazemiddleware.exception.TvMazeUnavailableException.class,
                () -> tvClient.getShowById(1L)
        );
        wireMockServer.verify(2, getRequestedFor(urlEqualTo("/shows/1")));
    }

    @Test
    void noReintentaEn404() {

        wireMockServer.stubFor(get(urlEqualTo("/shows/999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")));


        org.junit.jupiter.api.Assertions.assertThrows(
                com.mx.tvmazemiddleware.exception.TvNotShowException.class,
                () -> tvClient.getShowById(999L)
        );

        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/shows/999")));
    }
}