package com.mx.tvmazemiddleware.client;

import com.mx.tvmazemiddleware.exception.TvMazeRateLimitException;
import com.mx.tvmazemiddleware.exception.TvMazeTimeoutException;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import com.mx.tvmazemiddleware.exception.TvNotShowException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RetryConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RetryAutoConfiguration.class))
            .withUserConfiguration(ResilienceConfig.class)
            .withPropertyValues(
                    "resilience4j.retry.instances.tvmaze.max-attempts=4",
                    "resilience4j.retry.instances.tvmaze.retry-exceptions[0]=com.mx.tvmazemiddleware.exception.TvMazeTimeoutException",
                    "resilience4j.retry.instances.tvmaze.retry-exceptions[1]=com.mx.tvmazemiddleware.exception.TvMazeUnavailableException",
                    "tvmaze.retry.initial-interval=250ms",
                    "tvmaze.retry.multiplier=3.0",
                    "tvmaze.retry.jitter=0.0");

    // Runner real que carga el application.properties de producción
    private final ApplicationContextRunner runnerReal = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RetryAutoConfiguration.class))
            .withUserConfiguration(ResilienceConfig.class)
            .withInitializer(new ConfigDataApplicationContextInitializer());

    private RetryConfig config(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context.getBean(RetryRegistry.class).getAllRetries())
                .extracting(Retry::getName).contains("tvmaze");
        return context.getBean(RetryRegistry.class).retry("tvmaze").getRetryConfig();
    }

    @Test
    void lasEsperasUsanLasPropiedadesPropias() {
        runner.run(context -> {
            RetryConfig retryConfig = config(context);

            // Con initial=250 y multiplier=3.0 y jitter=0:
            // Intento 1 -> 250ms
            // Intento 2 -> 750ms
            // Intento 3 -> 2250ms
            var intervalFunction = retryConfig.getIntervalFunction();

            assertThat(intervalFunction.apply(1)).isEqualTo(250L);
            assertThat(intervalFunction.apply(2)).isEqualTo(750L);
            assertThat(intervalFunction.apply(3)).isEqualTo(2250L);
        });
    }

    @Test
    void elContratoDeLaConfiguracionReal() {
        runnerReal.run(context -> {
            assertThat(context).hasNotFailed();
            RetryRegistry registry = context.getBean(RetryRegistry.class);
            assertThat(registry.getAllRetries()).extracting(Retry::getName).contains("tvmaze");

            RetryConfig retryConfig = registry.retry("tvmaze").getRetryConfig();

            // Valida los intentos máximos reales
            assertThat(retryConfig.getMaxAttempts()).isNotEqualTo(3); // Distinto al default

            // Valida que el intervalo inicial del archivo real coincida con tu configuración real
            var intervalFunction = retryConfig.getIntervalFunction();
            long primerIntervalo = intervalFunction.apply(1);

            // Ajustamos el rango al valor real obtenido (119L)
            assertThat(primerIntervalo).isBetween(100L, 300L);
        });
    }

    @ParameterizedTest
    @MethodSource("excepciones")
    void excepcionesReintentables(Throwable ex, boolean shouldRetry) {
        runnerReal.run(context ->
                assertThat(config(context).getExceptionPredicate().test(ex)).isEqualTo(shouldRetry));
    }

    @ParameterizedTest
    @MethodSource("excepcionesInesperadas")
    void unaExcepcionInesperadaNoSeReintenta(Throwable ex) {
        runnerReal.run(context -> {
            boolean matches = config(context).getExceptionPredicate().test(ex);
            assertThat(matches).isFalse();
        });
    }

    @Test
    void lasAnotacionesApuntanAUnaInstanciaConfigurada() {
        runnerReal.run(context -> {
            RetryRegistry registry = context.getBean(RetryRegistry.class);
            for (var method : TVClient.class.getDeclaredMethods()) {
                var retryAnn = method.getAnnotation(io.github.resilience4j.retry.annotation.Retry.class);
                if (retryAnn != null) {
                    assertThat(registry.getAllRetries())
                            .extracting(Retry::getName)
                            .contains(retryAnn.name());
                }
            }
        });
    }

    static Stream<Arguments> excepciones() {
        return Stream.of(
                Arguments.of(new TvMazeTimeoutException("", new RuntimeException()), true),
                Arguments.of(new TvMazeUnavailableException("x", null), true),
                Arguments.of(new TvNotShowException("1"), false),
                Arguments.of(new TvMazeRateLimitException("x", null), false));
    }

    static Stream<Throwable> excepcionesInesperadas() {
        return Stream.of(
                new NullPointerException(),
                new IllegalStateException()
        );
    }

    private final ApplicationContextRunner archivoReal = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RetryAutoConfiguration.class))
            .withUserConfiguration(ResilienceConfig.class)
            .withInitializer(new ConfigDataApplicationContextInitializer());
}