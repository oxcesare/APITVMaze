package com.mx.tvmazemiddleware.client;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ResilienceConfig.TvMazeRetryProperties.class)
public class ResilienceConfig {

    @Bean
    public RetryConfigCustomizer tvmazeRetryCustomizer(TvMazeRetryProperties properties) {
        return RetryConfigCustomizer.of("tvmaze",
                builder -> builder.intervalFunction(RetryIntervals.exponentialWithJitter(properties)));
    }

    @ConfigurationProperties(prefix = "tvmaze.retry")
    public static record TvMazeRetryProperties(
            @DefaultValue("200ms") Duration initialInterval,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("0.5") double jitter) {
    }
}