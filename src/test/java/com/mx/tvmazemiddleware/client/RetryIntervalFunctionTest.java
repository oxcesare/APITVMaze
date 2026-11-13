package com.mx.tvmazemiddleware.client;

import io.github.resilience4j.core.IntervalFunction;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

class RetryIntervalFunctionTest {

    private static final int SAMPLES = 1_000;

    private final ResilienceConfig.TvMazeRetryProperties properties =
            new ResilienceConfig.TvMazeRetryProperties(Duration.ofMillis(100), 2.0, 0.5);

    @ParameterizedTest(name = "intento {0}: entre {1} y {2} ms")
    @CsvSource({
            "1,  50,  150",
            "2, 100,  300",
            "3, 200,  600",
            "4, 400, 1200",
            "5, 800, 2400"
    })
    void cadaEsperaCaeDentroDelRango(int intento, long minimo, long maximo) {
        IntervalFunction function = RetryIntervals.exponentialWithJitter(properties);

        List<Long> muestras = IntStream.range(0, SAMPLES)
                .mapToObj(i -> function.apply(intento))
                .toList();


        Assertions.assertThat(muestras)
                .allSatisfy(ms -> Assertions.assertThat(ms).isBetween(minimo - 1, maximo + 1));
    }

    @Test
    void lasMuestrasNoSonTodasIguales() {

        IntervalFunction function = RetryIntervals.exponentialWithJitter(properties);

        List<Long> muestras = IntStream.range(0, SAMPLES)
                .mapToObj(i -> function.apply(3))
                .toList();

        long valoresDistintos = muestras.stream().distinct().count();

        Assertions.assertThat(valoresDistintos).isGreaterThan(10);
    }

    @ParameterizedTest
    @CsvSource({
            "2.0, -0.1, El factor de jitter debe ser mayor o igual a 0.0 y menor que 1.0",
            "2.0,  1.0, El factor de jitter debe ser mayor o igual a 0.0 y menor que 1.0",
            "2.0,  1.5, El factor de jitter debe ser mayor o igual a 0.0 y menor que 1.0",
            "0.5,  0.5, El multiplicador debe ser al menos 1.0"
    })
    void configuracionInvalidaDebeLanzarExcepcion(double multiplier, double jitter, String mensaje) {
        var propiedadesInvalidas = new ResilienceConfig.TvMazeRetryProperties(
                Duration.ofMillis(100), multiplier, jitter);

        Assertions.assertThatThrownBy(() -> RetryIntervals.exponentialWithJitter(propiedadesInvalidas))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(mensaje);
    }

    @Test
    void intentoInvalidoDebeLanzarExcepcion() {
        IntervalFunction function = RetryIntervals.exponentialWithJitter(properties);

        Assertions.assertThatThrownBy(() -> function.apply(0))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}