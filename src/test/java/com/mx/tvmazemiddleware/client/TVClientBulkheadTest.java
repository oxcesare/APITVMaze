package com.mx.tvmazemiddleware.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = {
        TVClient.class,
        RestClientConfig.class,
        ResilienceConfig.class,
        CircuitBreakerLoggingConfig.class
})
@ImportAutoConfiguration({
        AopAutoConfiguration.class,
        BulkheadAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class
})
@ExtendWith(OutputCaptureExtension.class)
public class TVClientBulkheadTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TVClient tvClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tvmaze.base-url", wireMockServer::baseUrl);
        registry.add("app.tvmaze.read-timeout", () -> "5s");
        registry.add("app.tvmaze.connect-timeout", () -> "1s");
        registry.add("resilience4j.retry.instances.tvmaze.max-attempts", () -> "1");
        registry.add("resilience4j.bulkhead.instances.tvmaze.max-concurrent-calls", () -> "2");
    }

    @Test
    void bulkheadBajoElLimite() throws Exception {
        wireMockServer.stubFor(get(urlEqualTo("/shows/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Girls\"}")
                        .withFixedDelay(600)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var primero = executor.submit(() -> tvClient.getShowById(1L));
            var segundo = executor.submit(() -> tvClient.getShowById(1L));

            await().atMost(2, SECONDS).untilAsserted(() ->
                    assertThat(bulkheadRegistry.bulkhead("tvmaze").getMetrics().getAvailableConcurrentCalls())
                            .isZero());

            assertThat(primero.get(5, SECONDS)).isNotNull();
            assertThat(segundo.get(5, SECONDS)).isNotNull();
            assertThat(bulkheadRegistry.bulkhead("tvmaze").getMetrics().getAvailableConcurrentCalls())
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

}
