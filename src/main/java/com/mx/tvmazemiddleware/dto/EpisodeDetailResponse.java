package com.mx.tvmazemiddleware.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EpisodeDetailResponse(
        Long id,
        String url,
        String name,
        Integer season,
        Integer number,
        String type,
        String airdate,
        String airtime,
        String airstamp,
        Integer runtime,
        Rating rating,
        Image image,
        String summary,
        @JsonProperty("_links") Links links
) {}
