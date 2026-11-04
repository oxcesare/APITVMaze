package com.mx.tvmazemiddleware.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "tvmaze.retry")
public record TvMazeRetryProperties(
        @DefaultValue("200ms") Duration initialInterval,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("0.5") double jitter) {
}
