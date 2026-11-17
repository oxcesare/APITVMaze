package com.mx.tvmazemiddleware.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TVClientTimeBudgetTest {

    // Cargamos el application.properties real de producción de la aplicación
    private final ApplicationContextRunner realRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void elPeorCasoDeEsperaRespetaElPresupuestoMaximoReal() {
        realRunner.run(context -> {
            Environment env = context.getEnvironment();
            Binder binder = Binder.get(env);

            // 1. Leer los parámetros reales desde la configuración (application.properties)
            int maxAttempts = env.getRequiredProperty("resilience4j.retry.instances.tvmaze.max-attempts", Integer.class);

            Duration connectTimeout = binder.bind("app.tvmaze.connect-timeout", Duration.class)
                    .orElseThrow(() -> new IllegalStateException("Falta app.tvmaze.connect-timeout"));

            Duration readTimeout = binder.bind("app.tvmaze.read-timeout", Duration.class)
                    .orElseThrow(() -> new IllegalStateException("Falta app.tvmaze.read-timeout"));

            double initialIntervalMs = binder.bind("tvmaze.retry.initial-interval", Duration.class)
                    .orElseThrow(() -> new IllegalStateException("Falta tvmaze.retry.initial-interval")).toMillis();

            double multiplier = env.getRequiredProperty("tvmaze.retry.multiplier", Double.class);

            double jitter = env.getRequiredProperty("tvmaze.retry.jitter", Double.class);

            // 2. Cálculo determinista de las esperas máximas acumuladas considerando el Jitter al alza:
            // Fórmula: initial × multiplier^(k-1) × (1 + jitter)
            double totalMaxWaitTimeMs = 0;
            double currentInterval = initialIntervalMs;

            for (int k = 1; k < maxAttempts; k++) {
                double maxWaitForAttempt = currentInterval * (1.0 + jitter);
                totalMaxWaitTimeMs += maxWaitForAttempt;
                currentInterval *= multiplier;
            }

            // 3. Fórmula del peor caso completo:
            // intentos * (conexion + lectura) + sumatoria de esperas máximas con jitter
            double singleAttemptTimeMs = connectTimeout.toMillis() + readTimeout.toMillis();
            double worstCaseTotalTimeMs = (maxAttempts * singleAttemptTimeMs) + totalMaxWaitTimeMs;

            // 4. Regla de negocio estricta: Presupuesto máximo de 10 segundos (10,000 ms)
            double timeBudgetLimitMs = 10000.0;

            assertThat(worstCaseTotalTimeMs)
                    .as("El peor caso calculado (%.2f ms) excede el presupuesto estricto de %.2f ms",
                            worstCaseTotalTimeMs, timeBudgetLimitMs)
                    .isLessThanOrEqualTo(timeBudgetLimitMs);
        });
    }
}