package com.mx.tvmazemiddleware.client;


import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {
        TVClient.class,
        RestClientConfig.class,
        ResilienceConfig.class,
        RetryLoggingConfig.class
})
@ImportAutoConfiguration({AopAutoConfiguration.class, RetryAutoConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
public class TVClientRetryLoggingTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TVClient tvClient;

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
        wireMockServer.resetScenarios();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tvmaze.base-url", () -> "http://localhost:" + wireMockServer.port());
        // Propiedades específicas para el test de logging rápido
        registry.add("resilience4j.retry.instances.tvmaze.max-attempts", () -> "3");
        registry.add("tvmaze.retry.initial-interval", () -> "10ms");
        registry.add("tvmaze.retry.multiplier", () -> "1.0");
        registry.add("tvmaze.retry.jitter", () -> "0.0");
    }

    private static List<String> entradasDeReintento(String salida) {
        return Arrays.stream(salida.split("(?m)^(?=\\d{4}-\\d{2}-\\d{2}T)"))
                .filter(entrada -> entrada.lines().findFirst().orElse("").contains("RetryLoggingConfig"))
                .toList();
    }

    @Test
    void agrupaLasLineasDeCadaEntradaDeReintento() {
        String salida = """
                2026-01-01T10:00:00 WARN RetryLoggingConfig : Reintento
                java.lang.IllegalStateException: error
                \tat ejemplo.Cliente.llamada(Cliente.java:1)
                Caused by: java.lang.Exception: causa
                2026-01-01T10:00:01 INFO OtroLogger : mensaje
                \tat ejemplo.Otro.metodo(Otro.java:2)
                """;

        assertThat(entradasDeReintento(salida))
                .singleElement()
                .asString()
                .contains("\tat ejemplo.Cliente.llamada", "Caused by: java.lang.Exception")
                .doesNotContain("OtroLogger", "ejemplo.Otro");
    }


    @Test
    void registraUnWarnPorCadaReintento(CapturedOutput output) {
        String scenarioName = "Retry logging";
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("First retry"));
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("First retry")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Second retry"));
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Second retry")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Under the Dome\"}")));

        assertThat(tvClient.getShowById(1L).id()).isEqualTo(1L);
        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/shows/1")));

        var warningLines = output.getAll().lines()
                .filter(l -> l.contains("RetryLoggingConfig"))
                .filter(l -> l.contains("WARN"))
                .toList();

        assertThat(warningLines)
                .as("Debe registrarse exactamente una línea WARN por cada reintento realizado")
                .hasSize(2)
                .satisfiesExactly(
                        first -> assertThat(first).contains("WARN", "Reintento 1/2"),
                        second -> assertThat(second).contains("WARN", "Reintento 2/2"));
    }

    @Test
    void elLogIncluyeInstanciaTipoDeErrorYEspera(CapturedOutput output) {
        String scenarioName = "Detailed logging scenario";

        // Configuramos WireMock para que falle el primer intento con 503 (-> TvMazeUnavailableException)
        // y tenga éxito en el segundo intento.
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Success next"));

        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Success next")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Under the Dome\"}")));

        // Ejecutamos la llamada
        assertThat(tvClient.getShowById(1L).id()).isEqualTo(1L);

        // Validamos que alguna de las líneas de log contenga los elementos requeridos
        var matchingLine = output.getAll().lines()
                .filter(l -> l.contains("RetryLoggingConfig"))
                .filter(l -> l.contains("tvmaze"))
                .filter(l -> l.contains("TvMazeUnavailableException"))
                .filter(l -> l.contains("10 ms"))
                .findFirst();

        assertThat(matchingLine)
                .as("Debe existir una línea de log que incluya la instancia 'tvmaze', el error 'TvMazeUnavailableException' y la espera '10 ms'")
                .isPresent();
    }

    @Test
    void noFiltraDatosSensibles(CapturedOutput output) {
        String scenarioName = "Sensitive data leak scenario";
        String canaryBody = "CANARIO-CUERPO-123";
        String canaryQuery = "canario-consulta-456";

        // 1. Stub de WireMock que devuelve el cuerpo centinela ante un fallo 503 en el endpoint de búsqueda
        wireMockServer.stubFor(get(urlPathEqualTo("/search/shows"))
                .withQueryParam("q", equalTo(canaryQuery))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"" + canaryBody + "\"}"))
                .willSetStateTo("Success next"));

        wireMockServer.stubFor(get(urlPathEqualTo("/search/shows"))
                .withQueryParam("q", equalTo(canaryQuery))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Success next")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"show\":{\"id\":1,\"name\":\"Girls\"}}]")));

        // 2. Ejecutar la llamada con la consulta centinela
        tvClient.searchShows(canaryQuery);

        // 3. Capturar las entradas completas registradas por nuestro logger de reintentos
        List<String> retryEntries = entradasDeReintento(output.getAll());
        assertThat(retryEntries)
                .as("Debe registrarse el primer reintento")
                .hasSize(1)
                .anySatisfy(entrada -> assertThat(entrada).contains("Reintento 1/2"));
        String retryLogs = String.join("\n", retryEntries);

        String wireMockBaseUrl = wireMockServer.baseUrl();

        // 4. Aserciones estricta de seguridad
        assertThat(retryLogs)
                .as("El log de reintentos no debe contener el cuerpo de respuesta centinela")
                .doesNotContain(canaryBody)
                .as("El log de reintentos no debe contener la consulta/parámetros centinela del usuario")
                .doesNotContain(canaryQuery)
                .as("El log de reintentos no debe filtrar el host ni puerto base del cliente (%s)", wireMockBaseUrl)
                .doesNotContain(wireMockBaseUrl)
                .as("El log de reintentos no debe filtrar cabeceras de autorización")
                .doesNotContain("Authorization", "Bearer");
    }

    @Test
    void noRegistraTrazaDePila(CapturedOutput output) {
        String scenarioName = "Stack trace prevention scenario";

        // 1. Configurar WireMock para que falle consistentemente y fuerce los reintentos
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Retry phase"));

        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Retry phase")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Final fail"));

        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Final fail")
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isInstanceOf(TvMazeUnavailableException.class);
        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/shows/1")));

        List<String> retryEntries = entradasDeReintento(output.getAll());
        assertThat(retryEntries)
                .as("Debe haber dos entradas de reintento y una de agotamiento")
                .hasSize(3)
                .anySatisfy(entrada -> assertThat(entrada).contains("Reintento 1/2"));
        assertThat(retryEntries)
                .allSatisfy(entrada -> assertThat(entrada)
                        .as("La entrada de reintento no debe contener una traza de pila")
                        .doesNotContain("Caused by", "\tat "));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "Un error HTTP {0} no debe generar líneas de reintento")
    @org.junit.jupiter.params.provider.ValueSource(ints = {404, 429})
    void unErrorNoReintentableNoGeneraLineasDeReintento(int errorCode, CapturedOutput output) {
        // 1. Configurar WireMock para que responda con un error no reintentable (ej. 404 o 429)
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse().withStatus(errorCode)));

        // 2. Ejecutar la llamada (capturando la excepción esperada del cliente)
        try {
            tvClient.getShowById(1L);
        } catch (Exception ignored) {
            // El error no reintentable propagará inmediatamente
        }

        // 3. Verificar que las peticiones se hayan hecho una sola vez (sin reintentos)
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/shows/1")));

        // 4. Filtrar exclusivamente las líneas de log generadas por nuestro RetryLoggingConfig
        long retryLogCount = output.getAll().lines()
                .filter(l -> l.contains("RetryLoggingConfig"))
                .count();

        // 5. Validar estrictamente que NO exista ninguna línea de reintento
        assertThat(retryLogCount)
                .as("Un error HTTP %d no debe generar ningún evento ni línea de log de reintento", errorCode)
                .isZero();
    }

    @Test
    void unaLlamadaExitosaNoGeneraLineas(CapturedOutput output) {
        String scenarioName = "Positive logging control";
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/2"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Success next"));
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/2"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Success next")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":2,\"name\":\"Control\"}")));

        assertThat(tvClient.getShowById(2L).id()).isEqualTo(2L);
        wireMockServer.verify(2, getRequestedFor(urlPathEqualTo("/shows/2")));
        assertThat(entradasDeReintento(output.getAll()))
                .as("El logger debe registrar el primer reintento de la llamada de control")
                .isNotEmpty()
                .anySatisfy(entrada -> assertThat(entrada).contains("Reintento 1/2"));
        int outputLength = output.getAll().length();

        // 1. Configurar WireMock para que responda con éxito (200 OK) a la primera
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Girls\"}")));

        // 2. Ejecutar la llamada exitosa
        assertThat(tvClient.getShowById(1L).id()).isEqualTo(1L);

        // 3. Verificar que la petición se haya realizado exactamente una sola vez
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/shows/1")));

        // 4. Filtrar exclusivamente las líneas de log generadas por nuestro RetryLoggingConfig
        long retryLogCount = output.getAll().substring(outputLength).lines()
                .filter(l -> l.contains("RetryLoggingConfig"))
                .count();

        // 5. Validar estrictamente que el contador de logs de reintento sea cero
        assertThat(retryLogCount)
                .as("Una llamada exitosa a la primera no debe generar ninguna línea de log de reintento")
                .isZero();
    }

    @Test
    void elAgotamientoSeRegistraUnaVez(CapturedOutput output) {
        String scenarioName = "Exhausted retries scenario";

        // Configurar WireMock para que falle con 503 en los 3 intentos permitidos
        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Retry 1"));

        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Retry 1")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Retry 2"));

        wireMockServer.stubFor(get(urlPathEqualTo("/shows/1"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Retry 2")
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isInstanceOf(TvMazeUnavailableException.class);
        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/shows/1")));

        assertThat(entradasDeReintento(output.getAll()))
                .as("Deben registrarse dos reintentos y un agotamiento, todos como WARN")
                .hasSize(3)
                .satisfiesExactly(
                        first -> assertThat(first).contains("WARN", "Reintento 1/2"),
                        second -> assertThat(second).contains("WARN", "Reintento 2/2"),
                        exhausted -> assertThat(exhausted)
                                .contains("WARN", "Reintentos agotados", "tvmaze", "3 intentos",
                                        "TvMazeUnavailableException"))
                .allSatisfy(entrada -> assertThat(entrada.lines().findFirst().orElseThrow())
                        .doesNotContain("ERROR"));
    }
}
