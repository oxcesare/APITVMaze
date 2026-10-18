package com.mx.tvmazemiddleware.util;


public final class ApiConstants {

    public static final String V1 = "/api/v1";

    // Constructor privado para evitar instanciación (buena práctica para clases utilitarias)
    private ApiConstants() {
        throw new UnsupportedOperationException("Esta es una clase de constantes y no debe ser instanciada");
    }
}