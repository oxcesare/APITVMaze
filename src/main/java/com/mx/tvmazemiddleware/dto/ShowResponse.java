package com.mx.tvmazemiddleware.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShowResponse {

    private Long id;
    private String name;
    private String channel;
    private String summary;
    private List<String> genres;

    @JsonProperty("network")
    private void setNetwork(Map<String, Object> network) {
        if (network != null && this.channel == null) {
            this.channel = (String) network.get("name");
        }
    }

    @JsonProperty("webChannel")
    private void setWebChannel(Map<String, Object> webChannel) {
        if (webChannel != null && this.channel == null) {
            this.channel = (String) webChannel.get("name");
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }
}
