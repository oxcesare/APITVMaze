package com.mx.tvmazemiddleware.client;

import com.mx.tvmazemiddleware.security.RetryIntervals;
import com.mx.tvmazemiddleware.security.TvMazeRetryProperties;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TvMazeRetryProperties.class)
public class ResilienceConfig {

    @Bean
    public RetryConfigCustomizer tvmazeRetryCustomizer(TvMazeRetryProperties properties) {
        return RetryConfigCustomizer.of("tvmaze",
                builder -> builder.intervalFunction(RetryIntervals.exponentialWithJitter(properties)));
    }
}