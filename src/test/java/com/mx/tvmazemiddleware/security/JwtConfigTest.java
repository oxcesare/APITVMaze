package com.mx.tvmazemiddleware.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @Test
    void claveCortaLanzaExcepcion() {
        String secret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> config.jwtDecoder(new JwtProperties(secret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(secret);
    }

    @Test
    void claveDe32BytesCreaDecoder() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);

        JwtDecoder decoder = config.jwtDecoder(new JwtProperties(secret));

        assertThat(decoder).isNotNull();
    }

    @Test
    void claveNoBase64LanzaExcepcion() {
        String secret = "esto-no-es-base64!!";

        assertThatThrownBy(() -> config.jwtDecoder(new JwtProperties(secret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64")
                .hasMessageNotContaining(secret);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void claveNulaOVaciaLanzaExcepcion(String secret) {
        assertThatThrownBy(() -> config.jwtDecoder(new JwtProperties(secret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está configurado");
    }
}
