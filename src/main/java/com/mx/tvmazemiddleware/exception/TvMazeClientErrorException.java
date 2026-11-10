package com.mx.tvmazemiddleware.exception;

public class TvMazeClientErrorException extends TvMazeUnavailableException {
    public TvMazeClientErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
