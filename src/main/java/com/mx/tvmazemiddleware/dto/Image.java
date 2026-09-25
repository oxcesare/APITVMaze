package com.mx.tvmazemiddleware.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Image(
        String medium,
        String original
) {}