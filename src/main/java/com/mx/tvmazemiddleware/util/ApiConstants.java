package com.mx.tvmazemiddleware.util;


public final class ApiConstants {

    public static final String V1 = "/api/v1";

    public static final java.net.URI FORBIDDEN_TYPE = java.net.URI.create("urn:tvmaze:problem:forbidden");
    public static final String FORBIDDEN_TITLE = "Acceso prohibido";
    public static final String FORBIDDEN_DETAIL = "No cuentas con los permisos necesarios para acceder a este recurso";

    // Constructor privado para evitar instanciación (buena práctica para clases utilitarias)
    private ApiConstants() {
        throw new UnsupportedOperationException("Esta es una clase de constantes y no debe ser instanciada");
    }
}