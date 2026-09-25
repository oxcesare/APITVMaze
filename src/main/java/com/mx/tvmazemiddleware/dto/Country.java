package com.mx.tvmazemiddleware.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Country(
        String name,
        String code,
        String timezone
) {}
