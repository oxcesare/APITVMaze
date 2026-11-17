package com.mx.tvmazemiddleware.client;


import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {TVClient.class, RestClientConfig.class, ResilienceConfig.class})
@ImportAutoConfiguration({AopAutoConfiguration.class, RetryAutoConfiguration.class})
class TVClientHttpMethodsGuardTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TVClient tvClient;

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

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tvmaze.base-url", wireMockServer::baseUrl);
        registry.add("app.tvmaze.read-timeout", () -> "1s");
        registry.add("app.tvmaze.connect-timeout", () -> "1s");
    }

    @Test
    void elClienteSoloEmiteGet() {
        // 1. Configurar stubs en WireMock para las 3 operaciones de lectura
        wireMockServer.stubFor(any(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"id\":1,\"name\":\"Girls\"}")));

        wireMockServer.stubFor(any(urlPathEqualTo("/search/shows"))
                .withQueryParam("q", equalTo("girls"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("[{\"show\":{\"id\":1,\"name\":\"Girls\"}}]")));

        wireMockServer.stubFor(any(urlPathEqualTo("/episodes/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"id\":1,\"name\":\"Pilot\"}")));

        // 2. Invocar los tres métodos de lectura del TVClient real
        tvClient.getShowById(1L);
        tvClient.searchShows("girls");
        tvClient.getEpisodeById(1L);

        // 3. Recoger todas las peticiones recibidas por WireMock
        var requests = wireMockServer.findAll(anyRequestedFor(anyUrl()));

        assertThat(requests)
                .as("El cliente debe haber emitido exactamente 3 peticiones HTTP")
                .hasSize(3);

        // 4. Validar estrictamente que TODAS sean exclusivas con el método GET
        for (var request : requests) {
            assertThat(request.getMethod())
                    .as("El endpoint %s fue invocado con el método HTTP incorrecto. ¡Todas las llamadas del TVClient deben ser GET!", request.getUrl())
                    .isEqualTo(com.github.tomakehurst.wiremock.http.RequestMethod.GET);
        }
    }
}
