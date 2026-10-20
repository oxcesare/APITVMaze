package com.mx.tvmazemiddleware.exception;

public class InvalidSortFieldException extends RuntimeException {

    private final String field;

    public InvalidSortFieldException(String field) {
        super("El criterio de ordenamiento '" + field + "' no es válido.");
        this.field = field;
    }

    public String field() {
        return field;
    }
}
