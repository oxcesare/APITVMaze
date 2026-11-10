package com.mx.tvmazemiddleware.exception;

public class TvMazeRateLimitException extends RuntimeException {
    public TvMazeRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
