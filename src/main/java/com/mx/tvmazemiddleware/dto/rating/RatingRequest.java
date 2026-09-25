package com.mx.tvmazemiddleware.dto.rating;

public record RatingRequest (
        Long showId,
        String comment,
        Integer rating) {
}
