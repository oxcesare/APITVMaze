package com.mx.tvmazemiddleware.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Schedule(
        String time,
        List<String> days
) {}