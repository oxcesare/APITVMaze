package com.mx.tvmazemiddleware.dto.rating;

import java.util.List;

public record RatingPageResponse(
        List<RatingItemResponse> content,
        int page,
        int size,
        boolean hasNext) {
}
