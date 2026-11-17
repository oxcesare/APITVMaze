package com.mx.tvmazemiddleware.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircuitBreakerLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerLoggingConfig.class);


    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerLogConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                entryAddedEvent.getAddedEntry().getEventPublisher().onStateTransition(event -> {
                    CircuitBreaker.StateTransition transition = event.getStateTransition();
                    if (transition.getToState() == CircuitBreaker.State.OPEN) {
                        log.warn("Circuito '{}' cambió de {} a {}",
                                event.getCircuitBreakerName(),
                                transition.getFromState(),
                                transition.getToState());
                    } else {
                        log.info("Circuito '{}' cambió de {} a {}",
                                event.getCircuitBreakerName(),
                                transition.getFromState(),
                                transition.getToState());
                    }
                });
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemovedEvent) {
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
            }
        };
    }
}

