package com.mx.tvmazemiddleware.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Links(
        Link self,
        @JsonProperty("previousepisode") Link previousepisode
) {}
