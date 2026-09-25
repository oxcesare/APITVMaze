package com.mx.tvmazemiddleware.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "shows")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShowDetailResponse(
        Long id,
        String url,
        String name,
        String type,
        String language,
        List<String> genres,
        String status,
        Integer runtime,
        Integer averageRuntime,
        String premiered,
        String ended,
        String officialSite,
        Schedule schedule,
        Rating rating,
        Integer weight,
        Network network,
        Network webChannel,
        String dvdCountry,
        Externals externals,
        Image image,
        String summary,
        Long updated,
        @JsonProperty("_links") Links links,
        List<RatingInfo> comments
) {
}
