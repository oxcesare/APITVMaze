package com.mx.tvmazemiddleware.exception;

public class RatingAlreadyExistsException extends RuntimeException {

    private final Long showId;
    private final String userId;
    private final String ratingId;

    public RatingAlreadyExistsException(Long showId, String userId, String ratingId) {
        super("Ya existe una calificación del usuario " + userId + " para el show con ID " + showId + ".");
        this.showId = showId;
        this.userId = userId;
        this.ratingId = ratingId;
    }

    public Long showId() {
        return showId;
    }

    public String userId() {
        return userId;
    }

    public String ratingId() {
        return ratingId;
    }
}
