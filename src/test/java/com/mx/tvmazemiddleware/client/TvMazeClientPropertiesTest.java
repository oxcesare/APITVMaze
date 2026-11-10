package com.mx.tvmazemiddleware.client;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class TvMazeClientPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropsConfig.class);

    @EnableConfigurationProperties(TvMazeClientProperties.class)
    static class PropsConfig {
    }

    @Test
    void propiedadesPorDefectoYSobrescritas() {
        this.contextRunner
                .withPropertyValues(
                        "app.tvmaze.read-timeout=500ms",
                        "app.tvmaze.connect-timeout=500ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TvMazeClientProperties props = context.getBean(TvMazeClientProperties.class);
                    assertThat(props.readTimeout()).isEqualTo(Duration.ofMillis(500));
                    assertThat(props.connectTimeout()).isEqualTo(Duration.ofMillis(500));
                });
    }

    @Test
    void duracionAusenteImpideArrancar() {
        contextRunner
                .withPropertyValues("app.tvmaze.connect-timeout=1s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("app.tvmaze.read-timeout");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0ms", "-1s", ""})
    void propiedadesInvalidasImpidenArrancar(String timeoutInvalido) {
        contextRunner
                .withPropertyValues(
                        "app.tvmaze.read-timeout=" + timeoutInvalido,
                        "app.tvmaze.connect-timeout=1s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("app.tvmaze.read-timeout")
                            .hasMessageContaining("mayor a cero");
                });
    }
}