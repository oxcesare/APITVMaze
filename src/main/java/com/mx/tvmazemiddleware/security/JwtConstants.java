package com.mx.tvmazemiddleware.security;

import java.time.Duration;

public final class JwtConstants {

    public static final String ISSUER = "tvmaze-middleware";
    public static final Duration TTL = Duration.ofMinutes(15);

    private JwtConstants() {
    }
}
