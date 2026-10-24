package com.mx.tvmazemiddleware.dto.rating;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ratings")
@CompoundIndex(name = "show_user_unique_idx", def = "{'showId': 1, 'userId': 1}", unique = true)
public record Rating(
        @Id String id,
        String userId,
        Long showId,
        String comment,
        Integer rating
) { }
