package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TVClientNetworkFaultsTest {

    private WireMockServer wireMockServer;
    private TVClient tvClient;

    @BeforeEach
    void setUp() {
        // 1. Levantar WireMock con puerto dinámico
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // 2. Configurar RestClient con SimpleClientHttpRequestFactory igual que en producción
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(500));
        requestFactory.setReadTimeout(Duration.ofMillis(2000)); // 2 segundos de lectura máxima

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .requestFactory(requestFactory)
                .build();

        // 3. Instanciar directamente TVClient sin proxy (sin reintentos automáticos)
        tvClient = new TVClient(restClient);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    // Proveedor de las 12 combinaciones (4 faults × 3 operaciones)
    static Stream<Arguments> networkFaultsAndOperationsProvider() {
        Fault[] faults = {
                Fault.CONNECTION_RESET_BY_PEER,
                Fault.EMPTY_RESPONSE,
                Fault.MALFORMED_RESPONSE_CHUNK,
                Fault.RANDOM_DATA_THEN_CLOSE
        };

        Stream.Builder<Arguments> builder = Stream.builder();
        for (Fault fault : faults) {
            builder.add(Arguments.of(fault, "/shows/1", "getShowById"));
            builder.add(Arguments.of(fault, "/search/shows", "searchShows"));
            builder.add(Arguments.of(fault, "/episodes/1", "getEpisodeById"));
        }
        return builder.build();
    }

    @ParameterizedTest(name = "Fault: {0} en endpoint {1} ({2})")
    @MethodSource("networkFaultsAndOperationsProvider")
    void fallosDeRedSonUnavailable(Fault fault, String endpoint, String operationName) {
        // 1. Configurar el stub genérico para el endpoint afectado con el fault de red
        wireMockServer.stubFor(any(urlPathEqualTo(endpoint))
                .willReturn(aResponse().withFault(fault)));

        // 2. Ejecutar la operación correspondiente del cliente según el nombre del método
        org.assertj.core.api.ThrowableAssert.ThrowingCallable action = switch (operationName) {
            case "getShowById" -> () -> tvClient.getShowById(1L);
            case "searchShows" -> () -> tvClient.searchShows("girls");
            case "getEpisodeById" -> () -> tvClient.getEpisodeById(1L);
            default -> throw new IllegalArgumentException("Operación desconocida: " + operationName);
        };

        // 3. Validar estrictamente la excepción de negocio, la causa raíz y la protección de datos (sin URL)
        String wireMockBaseUrl = wireMockServer.baseUrl();

        assertThatThrownBy(action)
                .isExactlyInstanceOf(TvMazeUnavailableException.class)
                .hasMessage("Error de acceso al consultar TVMaze")
                .hasCauseInstanceOf(RestClientException.class)
                .satisfies(ex -> {
                    String message = ex.getMessage();
                    assertThat(message)
                            .as("El mensaje de error no debe filtrar la URL base de WireMock (%s)", wireMockBaseUrl)
                            .doesNotContain(wireMockBaseUrl);
                });
    }

    @Test
    void unaRespuestaNormalNoFalla() {
        // 1. Configurar stubs exitosos (200 OK) para las tres operaciones del cliente
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Girls\"}")));

        wireMockServer.stubFor(get(urlPathEqualTo("/search/shows"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"show\":{\"id\":1,\"name\":\"Girls\"}}]")));

        wireMockServer.stubFor(get(urlPathEqualTo("/episodes/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Pilot\"}")));

        // 2. Validar que las llamadas devuelven resultados correctos sin errores
        assertThat(tvClient.getShowById(1L)).isNotNull();
        assertThat(tvClient.searchShows("girls")).isNotNull();
        assertThat(tvClient.getEpisodeById(1L)).isNotNull();
    }
}