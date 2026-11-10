package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.dto.User;
import com.mx.tvmazemiddleware.security.JwtConfig;
import com.mx.tvmazemiddleware.security.JwtConstants;
import com.mx.tvmazemiddleware.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TokenServiceTest {

    // En el futuro lejano para que el decoder (que usa el reloj real) no vea el token como expirado.
    private static final Instant NOW = Instant.parse("2100-01-01T00:00:00Z");

    private static final User USER = new User("u1", "ana", "hash", List.of("USER"), true);

    private TokenService tokenService;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(Base64.getEncoder().encodeToString(new byte[32]));
        JwtConfig config = new JwtConfig();
        decoder = config.jwtDecoder(properties);
        tokenService = new TokenService(config.jwtEncoder(properties), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void emiteTokenConClaimsEsperados() {
        String token = tokenService.issue(USER);

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("u1");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("tvmaze-middleware");
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(java.time.Duration.ofMinutes(15)));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsStringList("roles")).contains("USER");
    }

    @Test
    void cadaTokenTieneJtiDistinto() {

        Jwt first = decoder.decode(tokenService.issue(USER));
        Jwt second = decoder.decode(tokenService.issue(USER));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void rechazaTokenFirmadoConOtraClave() {
        JwtProperties otherKey = new JwtProperties(Base64.getEncoder().encodeToString(filledKey(32, (byte) 1)));
        TokenService other = new TokenService(new JwtConfig().jwtEncoder(otherKey), Clock.fixed(NOW, ZoneOffset.UTC));

        String token = other.issue(USER);

        assertThatThrownBy(() -> decoder.decode(token)).isExactlyInstanceOf(BadJwtException.class)
                .hasMessageContaining("Invalid signature");
    }

    @Test
    void rechazaTokenExpirado() {
        Instant past = Instant.parse("2000-01-01T00:00:00Z");
        JwtProperties properties = new JwtProperties(Base64.getEncoder().encodeToString(new byte[32]));
        TokenService expired = new TokenService(new JwtConfig().jwtEncoder(properties), Clock.fixed(past, ZoneOffset.UTC));

        String token = expired.issue(USER);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rechazaTokenConEmisorDistinto() {
        JwtProperties properties = new JwtProperties(Base64.getEncoder().encodeToString(new byte[32]));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("otro-emisor")
                .subject("u1")
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(900))
                .build();
        String token = new JwtConfig().jwtEncoder(properties).encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(token))
                .isExactlyInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iss");
    }

    @Test
    void rechazaTokenSinEmisor() {
        JwtProperties properties = new JwtProperties(Base64.getEncoder().encodeToString(new byte[32]));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("u1")
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(900))
                .build();
        String token = new JwtConfig().jwtEncoder(properties).encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(token))
                .isExactlyInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iss");
    }

    @Test
    void aceptaTokenHs256ConClaveDe64Bytes() {
        JwtProperties properties = new JwtProperties(Base64.getEncoder().encodeToString(new byte[64]));

        String token = signWith(MacAlgorithm.HS256, properties);

        assertThat(new JwtConfig().jwtDecoder(properties).decode(token).getSubject()).isEqualTo("u1");
    }

    @Test
    void rechazaTokenFirmadoConOtroAlgoritmo() {
        // Misma clave de 64 bytes (válida para HS384) en firma y decoder: solo cambia el algoritmo.
        JwtProperties properties = new JwtProperties(Base64.getEncoder().encodeToString(new byte[64]));
        JwtDecoder strictDecoder = new JwtConfig().jwtDecoder(properties);

        String token = signWith(MacAlgorithm.HS384, properties);

        assertThatThrownBy(() -> strictDecoder.decode(token)).isExactlyInstanceOf(BadJwtException.class)
                .hasMessageContaining("Another algorithm expected");
    }

    private static String signWith(MacAlgorithm algorithm, JwtProperties properties) {
        JwtEncoder encoder = new JwtConfig().jwtEncoder(properties);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConstants.ISSUER)
                .subject("u1")
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(900))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims)).getTokenValue();
    }

    private static byte[] filledKey(int size, byte value) {
        byte[] key = new byte[size];
        java.util.Arrays.fill(key, value);
        return key;
    }
}
