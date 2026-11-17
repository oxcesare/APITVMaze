package com.mx.tvmazemiddleware.exception;

import com.mx.tvmazemiddleware.client.TVClient;
import com.mx.tvmazemiddleware.controller.TVMazeController;
import com.mx.tvmazemiddleware.service.TVMazeService;
import com.mx.tvmazemiddleware.util.ApiConstants;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TVMazeController.class)
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @TestConfiguration
    static class CircuitBreakerTestConfiguration {
        @Bean
        CircuitBreakerRegistry circuitBreakerRegistry() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .waitDurationInOpenState(Duration.ofSeconds(7))
                    .build();
            CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
            registry.circuitBreaker("tvmaze");
            return registry;
        }
    }

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TVClient tvClient;

    @MockBean
    private TVMazeService tvMazeService;

    @Test
    void circuitoAbiertoDevuelve503ConProblemDetail() throws Exception {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("tvmaze");
        given(tvMazeService.getShowById(1L))
                .willThrow(CallNotPermittedException.createCallNotPermittedException(breaker));

        String body = mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:upstream-circuit-open"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Servicio externo temporalmente no disponible"))
                .andExpect(jsonPath("$.detail").value("El servicio externo está temporalmente no disponible"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("CircuitBreaker", "CallNotPermitted", "io.github");
    }

    @Test
    void circuitoAbiertoIndicaRetryAfterConLaEsperaDelCircuito() throws Exception {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("tvmaze");
        given(tvMazeService.getShowById(1L))
                .willThrow(CallNotPermittedException.createCallNotPermittedException(breaker));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "7"));
    }

    @Test
    void showNoEncontradoTieneTypePropio() throws Exception {
        when(tvMazeService.getShowById(99L)).thenThrow(new TvNotShowException("99"));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:show-not-found"));
    }

    @Test
    void busquedaSinResultadosTieneTypePropio() throws Exception {
        when(tvMazeService.searchShows("zzzz")).thenThrow(new TvShowSearchEmptyException("zzzz"));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/search").param("searchQuery", "zzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:search-no-results"));
    }

    @Test
    void rateLimitTieneTypePropio() throws Exception {
        when(tvMazeService.getShowById(1L))
                .thenThrow(new TvMazeRateLimitException("limite", new RuntimeException()));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rate-limited"));
    }

    @Test
    void timeoutTieneTypePropioY504() throws Exception {
        when(tvMazeService.getShowById(1L))
                .thenThrow(new TvMazeTimeoutException("Timeout al conectar con TVMaze", new RuntimeException()));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isGatewayTimeout()) // HTTP 504
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:upstream-timeout"));
    }

    @Test
    void handlerTimeoutDevuelve504() throws Exception {

        java.net.SocketTimeoutException causaOriginal = new java.net.SocketTimeoutException("Read timed out");

        given(tvMazeService.getShowById(1L))
                .willThrow(new TvMazeTimeoutException("Timeout de conexión con TVMaze", causaOriginal));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:upstream-timeout"))
                .andExpect(jsonPath("$.title").value("Timeout en servicio externo"))
                .andExpect(jsonPath("$.status").value(504));
    }

    @Test
    void respuestaNoFiltraInternos() throws Exception {

        String url = "https://api.tvmaze.com/shows/1";

        java.net.SocketTimeoutException causaOriginal =
                new java.net.SocketTimeoutException("Read timed out calling " + url);

        given(tvMazeService.getShowById(1L))
                .willThrow(new TvMazeTimeoutException("Timeout al consultar " + url, causaOriginal));

        String body = mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isGatewayTimeout())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("api.tvmaze.com")
                .doesNotContain("tvmaze.com")
                .doesNotContain(url)
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContain("SocketTimeoutException")
                .doesNotContain("java.net")
                .doesNotContain("com.mx.tvmazemiddleware")
                .doesNotContain("stackTrace")
                .doesNotContain("\tat ")
                .doesNotContain("Read timed out");
    }

    @Test
    void unavailableSigueSiendo503SinRetryAfter() throws Exception {
        given(tvMazeService.getShowById(1L))
                .willThrow(new TvMazeUnavailableException("TVMaze caído", new RuntimeException()));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:upstream-unavailable"))
                .andExpect(jsonPath("$.detail").value("El servicio externo no está disponible"))
                .andExpect(header().doesNotExist("Retry-After"));
    }
}
