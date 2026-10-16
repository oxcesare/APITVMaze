package com.mx.tvmazemiddleware.exception;

public class TvShowSearchEmptyException extends RuntimeException {

    public TvShowSearchEmptyException(String message) {
        super("No se encontró información" + " para el show: " + message);
    }
}
