package com.mx.tvmazemiddleware.client;

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetryLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(RetryLoggingConfig.class);

    @Bean
    public RegistryEventConsumer<Retry> retryLogConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
                Retry retry = entryAddedEvent.getAddedEntry();
                retry.getEventPublisher().onRetry(event -> {
                    // Solo registramos datos esenciales: instancia, intento, maximo y tipo de excepción simple (sin traza ni URLs)
                    int maxAttemptsMinusOne = retry.getRetryConfig().getMaxAttempts() - 1;
                    String exceptionName = event.getLastThrowable() != null
                            ? event.getLastThrowable().getClass().getSimpleName()
                            : "UnknownException";

                    log.warn("Reintento {}/{} de '{}' tras {} (espera {} ms)",
                            event.getNumberOfRetryAttempts(),
                            maxAttemptsMinusOne,
                            event.getName(),
                            exceptionName,
                            event.getWaitInterval().toMillis());
                });
                retry.getEventPublisher().onError(event -> {
                    String exceptionName = event.getLastThrowable() != null
                            ? event.getLastThrowable().getClass().getSimpleName()
                            : "UnknownException";

                    log.error("Reintentos agotados de '{}' tras {} intentos por {}",
                            event.getName(),
                            event.getNumberOfRetryAttempts(),
                            exceptionName);
                });
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemovedEvent) {}

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {}
        };
    }
}