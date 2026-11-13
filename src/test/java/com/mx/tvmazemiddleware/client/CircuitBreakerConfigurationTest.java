package com.mx.tvmazemiddleware.client;


import com.mx.tvmazemiddleware.exception.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerConfigurationTest {


    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class))
            .withUserConfiguration(ResilienceConfig.class)
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void validaPropiedadesDelContratoCircuitBreaker() {
        contextRunner.run(context -> {
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            CircuitBreaker circuitBreaker = registry.circuitBreaker("tvmaze");
            CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();

            assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
            assertThat(config.getSlidingWindowSize()).isEqualTo(10);
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
            assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
            assertThat(Duration.ofMillis(config.getWaitIntervalFunctionInOpenState().apply(1)))
                    .isEqualTo(Duration.ofSeconds(10));
            assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
            assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
            assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(1500));
            assertThat(config.getSlowCallRateThreshold()).isEqualTo(50.0f);
        });
    }

    static Stream<Arguments> exceptionPredicateProvider() {
        return Stream.of(
                // Excepciones que SÍ cuentan como fallo
                Arguments.of(new TvMazeTimeoutException("Timeout", null), true),
                Arguments.of(new TvMazeUnavailableException("Unavailable", null), true),
                Arguments.of(new TvMazeRateLimitException("Rate limit", null), true),

                // Excepciones que NO cuentan como fallo
                Arguments.of(new TvNotShowException("Not show"), false),
                Arguments.of(new TvShowSearchEmptyException("Search empty"), false),
                Arguments.of(new TvMazeClientErrorException("Client error", null), false),
                Arguments.of(new NullPointerException("Bug propio"), false)
        );
    }

    @ParameterizedTest(name = "Excepción {0} cuenta como fallo: {1}")
    @MethodSource("exceptionPredicateProvider")
    void validaPredicadoDeExcepcionesRegistradas(Exception exception, boolean shouldCountAsFailure) {
        contextRunner.run(context -> {
            CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
            CircuitBreaker circuitBreaker = registry.circuitBreaker("tvmaze");
            CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();

            boolean matches = !config.getIgnoreExceptionPredicate().test(exception)
                    && config.getRecordExceptionPredicate().test(exception);
            assertThat(matches).isEqualTo(shouldCountAsFailure);
        });
    }




}