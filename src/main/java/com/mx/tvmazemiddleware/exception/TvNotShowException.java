package com.mx.tvmazemiddleware.exception;

public class TvNotShowException extends RuntimeException {

    public TvNotShowException(String message) {
        super("Busqueda sin resultados" + " para el show: " + message);
    }
}
