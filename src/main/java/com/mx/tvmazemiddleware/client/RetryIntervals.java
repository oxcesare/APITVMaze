package com.mx.tvmazemiddleware.client;

import io.github.resilience4j.core.IntervalFunction;

public final class RetryIntervals {

    private RetryIntervals() {}

    public static IntervalFunction exponentialWithJitter(ResilienceConfig.TvMazeRetryProperties properties) {
        if (properties.jitter() < 0.0 || properties.jitter() > 1.0) {
            throw new IllegalArgumentException("El factor de jitter debe estar entre 0.0 y 1.0");
        }
        if (properties.multiplier() < 1.0) {
            throw new IllegalArgumentException("El multiplicador debe ser al menos 1.0");
        }

        return IntervalFunction.ofExponentialRandomBackoff(
                properties.initialInterval(),
                properties.multiplier(),
                properties.jitter()
        );
    }
}
