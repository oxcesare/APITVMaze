package com.mx.tvmazemiddleware.exception;

public class RatingShowReferenceNotFoundException extends RuntimeException {

    private final Long showId;

    public RatingShowReferenceNotFoundException(Long showId) {
        super("No se encontró en nuestra base de datos un show con ID " + showId
                + "; no se puede guardar la calificación.");
        this.showId = showId;
    }

    public Long showId() {
        return showId;
    }
}
