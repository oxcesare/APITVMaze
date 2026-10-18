package com.mx.tvmazemiddleware.exception;

public class TvMazeRateLimitException extends TvMazeUnavailableException {
    public TvMazeRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
