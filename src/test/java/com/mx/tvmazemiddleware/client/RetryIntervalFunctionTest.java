package com.mx.tvmazemiddleware.client;

import com.mx.tvmazemiddleware.security.RetryIntervals;
import com.mx.tvmazemiddleware.security.TvMazeRetryProperties;
import io.github.resilience4j.core.IntervalFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class RetryIntervalFunctionTest {

    private static final int SAMPLES = 1_000;

    private final TvMazeRetryProperties properties =
            new TvMazeRetryProperties(Duration.ofMillis(100), 2.0, 0.5);

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


        assertThat(muestras)
                .allSatisfy(ms -> assertThat(ms).isBetween(minimo - 1, maximo + 1));
    }

    @Test
    void lasMuestrasNoSonTodasIguales() {

        IntervalFunction function = RetryIntervals.exponentialWithJitter(properties);

        List<Long> muestras = IntStream.range(0, SAMPLES)
                .mapToObj(i -> function.apply(3))
                .toList();

        long valoresDistintos = muestras.stream().distinct().count();

        assertThat(valoresDistintos).isGreaterThan(10);
    }

    @Test
    void intentoInvalidoDebeLanzarExcepcion() {
        IntervalFunction function = RetryIntervals.exponentialWithJitter(properties);

        assertThatThrownBy(() -> function.apply(0))
                .isInstanceOf(Exception.class);
    }
}