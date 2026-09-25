package com.mx.tvmazemiddleware.exception;

public class TvNotShowException extends RuntimeException {

    public TvNotShowException(String message) {
        super("No se encontró información" + " para el show: " + message);
    }
}
