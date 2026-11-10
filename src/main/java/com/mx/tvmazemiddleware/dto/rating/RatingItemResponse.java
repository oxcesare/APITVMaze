package com.mx.tvmazemiddleware.dto.rating;

public record RatingItemResponse(
        String id,
        Long showId,
        String comment,
        Integer rating) {
}
