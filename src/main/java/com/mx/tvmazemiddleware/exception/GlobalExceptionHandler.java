package com.mx.tvmazemiddleware.exception;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {


    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TvNotShowException.class)
    public ProblemDetail handleNotFound(TvNotShowException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Show no encontrado");
        return pd;
    }

    @ExceptionHandler(TvMazeUnavailableException.class)
    public ProblemDetail handleUnavailable(TvMazeUnavailableException ex) {
        log.error("TVMaze no disponible", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "El servicio externo no está disponible");
        pd.setTitle("Dependencia no disponible");
        return pd;
    }

    @ExceptionHandler(TvMazeRateLimitException.class)
    public ProblemDetail handleRateLimit(TvMazeRateLimitException ex) {
        log.warn("TVMaze rechazó la petición por límite de solicitudes", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Se excedió el límite de peticiones a TVMaze");
        pd.setTitle("Límite de peticiones excedido");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Error inesperado", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }
}
