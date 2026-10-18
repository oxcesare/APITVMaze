package com.mx.tvmazemiddleware.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Externals(
        Integer tvrage,
        Integer thetvdb,
        String imdb
) {}
