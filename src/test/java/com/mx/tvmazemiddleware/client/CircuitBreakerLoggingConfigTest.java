package com.mx.tvmazemiddleware.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerLoggingConfigTest {

    @Test
    void registraLasTransicionesDeCircuitosConfiguradosYAgregados() {
        Logger logger = (Logger) LoggerFactory.getLogger(CircuitBreakerLoggingConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class))
                    .withUserConfiguration(CircuitBreakerLoggingConfig.class)
                    .withPropertyValues("resilience4j.circuitbreaker.instances.configurado.sliding-window-size=10")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);
                        CircuitBreaker configurado = registry.find("configurado").orElseThrow();
                        CircuitBreaker dinamico = registry.circuitBreaker("dinamico");

                        configurado.transitionToOpenState();
                        configurado.transitionToHalfOpenState();
                        dinamico.transitionToOpenState();

                        assertThat(appender.list)
                                .hasSize(3)
                                .extracting(ILoggingEvent::getLevel)
                                .containsExactly(Level.WARN, Level.INFO, Level.WARN);

                        assertThat(appender.list)
                                .extracting(ILoggingEvent::getFormattedMessage)
                                .containsExactly(
                                        "Circuito 'configurado' cambió de CLOSED a OPEN",
                                        "Circuito 'configurado' cambió de OPEN a HALF_OPEN",
                                        "Circuito 'dinamico' cambió de CLOSED a OPEN");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
