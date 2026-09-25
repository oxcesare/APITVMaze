package com.mx.tvmazemiddleware.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Rating(
        Double average
) {}

