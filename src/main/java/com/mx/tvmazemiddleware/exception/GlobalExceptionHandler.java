package com.mx.tvmazemiddleware.exception;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {


    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI SHOW_NOT_FOUND = URI.create("urn:tvmaze:problem:show-not-found");
    private static final URI RATING_SHOW_REFERENCE_NOT_FOUND =
            URI.create("urn:tvmaze:problem:rating-show-reference-not-found");
    private static final URI RATING_ALREADY_EXISTS = URI.create("urn:tvmaze:problem:rating-already-exists");
    private static final URI SEARCH_NO_RESULTS = URI.create("urn:tvmaze:problem:search-no-results");
    private static final URI UPSTREAM_UNAVAILABLE = URI.create("urn:tvmaze:problem:upstream-unavailable");
    private static final URI RATE_LIMITED = URI.create("urn:tvmaze:problem:rate-limited");
    private static final URI INTERNAL_ERROR = URI.create("urn:tvmaze:problem:internal-error");
    private static final URI VALIDATION_ERROR = URI.create("urn:tvmaze:problem:validation-error");

    @ExceptionHandler(TvNotShowException.class)
    public ProblemDetail handleNotFound(TvNotShowException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(SHOW_NOT_FOUND);
        pd.setTitle("Show no encontrado");
        return pd;
    }

    @ExceptionHandler(RatingShowReferenceNotFoundException.class)
    public ProblemDetail handleRatingShowReferenceNotFound(RatingShowReferenceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(RATING_SHOW_REFERENCE_NOT_FOUND);
        pd.setTitle("Show referenciado no encontrado");
        pd.setProperty("showId", ex.showId());
        return pd;
    }

    @ExceptionHandler(RatingAlreadyExistsException.class)
    public ProblemDetail handleRatingAlreadyExists(RatingAlreadyExistsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(RATING_ALREADY_EXISTS);
        pd.setTitle("La calificación ya existe");
        pd.setProperty("showId", ex.showId());
        pd.setProperty("userId", ex.userId());
        pd.setProperty("ratingId", ex.ratingId());
        return pd;
    }

    @ExceptionHandler(TvMazeUnavailableException.class)
    public ProblemDetail handleUnavailable(TvMazeUnavailableException ex) {
        log.error("TVMaze no disponible", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "El servicio externo no está disponible");
        pd.setType(UPSTREAM_UNAVAILABLE);
        pd.setTitle("Dependencia no disponible");
        return pd;
    }

    @ExceptionHandler(TvShowSearchEmptyException.class)
    public ProblemDetail handleShowSearchEmpty(TvShowSearchEmptyException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(SEARCH_NO_RESULTS);
        pd.setTitle("No hay información o resultados");
        return pd;
    }

    @ExceptionHandler(TvMazeRateLimitException.class)
    public ProblemDetail handleRateLimit(TvMazeRateLimitException ex) {
        log.warn("TVMaze rechazó la petición por límite de solicitudes", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Se excedió el límite de peticiones a TVMaze");
        pd.setType(RATE_LIMITED);
        pd.setTitle("Límite de peticiones excedido");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Error inesperado", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
        pd.setType(INTERNAL_ERROR);
        pd.setTitle("Error interno");
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos");
        pd.setTitle("Error de validación");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList());
        pd.setType(VALIDATION_ERROR);
        return handleExceptionInternal(ex, pd, headers, status, request);
    }


    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Request body ilegible: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud es inválido, está vacío o tiene un tipo de dato incorrecto");
        pd.setTitle("Solicitud mal formada");
        pd.setType(VALIDATION_ERROR);
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        if (body instanceof ProblemDetail pd && ProblemDetail.forStatus(statusCode).getType().equals(pd.getType())) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            String slug = status != null ? status.name().toLowerCase().replace('_', '-') : "http-" + statusCode.value();
            pd.setType(URI.create("urn:tvmaze:problem:" + slug));
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
