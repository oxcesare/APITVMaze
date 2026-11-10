package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mx.tvmazemiddleware.exception.TvMazeTimeoutException;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TVClientTimeoutTest {

    private WireMockServer wireMockServer;
    private TVClient tvClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withFixedDelay(2_000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(500));

        RestClient restClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(requestFactory)
                .build();
        tvClient = new TVClient(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void lanzaExcepcionDeTimeoutPropiaCuandoGetShowSuperaElTimeoutDeLectura() {
        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isInstanceOf(TvMazeTimeoutException.class)
                .isNotInstanceOf(TvMazeUnavailableException.class);
    }

    @Test
    void respuestaRapidaNoFallaControl() {
        wireMockServer.stubFor(get(urlEqualTo("/shows/2"))
                .willReturn(aResponse()
                        .withFixedDelay(100) // Menor al timeout de 500ms
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 2, \"name\": \"Test Show\"}")));

        var response = tvClient.getShowById(2L);
        org.assertj.core.api.Assertions.assertThat(response).isNotNull();
    }

    @Test
    void conexionRechazadaEsUnavailable() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(500));

        RestClient invalidClient = RestClient.builder()
                .baseUrl("http://localhost:1") // Puerto cerrado intencionalmente
                .requestFactory(requestFactory)
                .build();
        TVClient badClient = new TVClient(invalidClient);

        assertThatThrownBy(() -> badClient.getShowById(1L))
                .isInstanceOf(TvMazeUnavailableException.class)
                .isNotInstanceOf(TvMazeTimeoutException.class);
    }

    @Test
    void lecturaLentaLanzaTimeout() {
        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isInstanceOf(TvMazeTimeoutException.class)
                .isNotInstanceOf(TvMazeUnavailableException.class);
    }

    @Test
    void respuestaRapidaNoFalla() {
        wireMockServer.stubFor(get(urlEqualTo("/shows/2"))
                .willReturn(aResponse()
                        .withFixedDelay(100)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 2, \"name\": \"Under the Dome\"}")));

        var result = tvClient.getShowById(2L);
        assertThat(result).isNotNull();
    }
}
