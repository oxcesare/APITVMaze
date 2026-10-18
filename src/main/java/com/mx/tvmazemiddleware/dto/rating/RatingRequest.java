package com.mx.tvmazemiddleware.dto.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RatingRequest (
        @NotNull Long showId,
        @NotBlank String userId,
        @NotBlank String comment,
        @NotNull @Min(0) @Max(5) Integer rating) {
}
