package com.mx.tvmazemiddleware.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.tvmaze")
public record TvMazeClientProperties(
        Duration readTimeout,
        Duration connectTimeout) {

    public TvMazeClientProperties {
        validar("app.tvmaze.read-timeout", readTimeout);
        validar("app.tvmaze.connect-timeout", connectTimeout);
    }

    private static void validar(String propiedad, Duration valor) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalStateException(
                    "La propiedad " + propiedad + " debe estar configurada con una duración mayor a cero");
        }
    }
}
