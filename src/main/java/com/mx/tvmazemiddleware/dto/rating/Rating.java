package com.mx.tvmazemiddleware.dto.rating;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ratings")
public record Rating(
        @Id String id,
        String userId,
        Long showId,
        String comment,
        Integer rating
) { }
