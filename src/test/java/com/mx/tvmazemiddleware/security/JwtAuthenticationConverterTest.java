package com.mx.tvmazemiddleware.security;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationConverterTest {

    @Test
    void convierteRolesCorrectamente() {
        Jwt jwt = Jwt.withTokenValue("token-dummy")
                .header("alg", "none")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        JwtAuthenticationConverter converter =
                new SecurityConfig(new ObjectMapper()).jwtAuthenticationConverter();

        var authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }
}
