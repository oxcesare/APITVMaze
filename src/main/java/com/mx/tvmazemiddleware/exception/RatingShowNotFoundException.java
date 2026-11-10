package com.mx.tvmazemiddleware.exception;

public class RatingShowNotFoundException extends RuntimeException {

    private final Long showId;

    public RatingShowNotFoundException(Long showId) {
        super("No hay un show registrado en nuestra base de datos con ID " + showId + ".");
        this.showId = showId;
    }

    public Long showId() {
        return showId;
    }
}
