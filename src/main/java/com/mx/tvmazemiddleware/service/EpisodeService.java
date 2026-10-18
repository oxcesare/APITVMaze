package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.client.TVClient;
import com.mx.tvmazemiddleware.dto.EpisodeDetailResponse;
import org.springframework.stereotype.Service;


@Service
public class EpisodeService {

    private final TVClient tvClient;

    public EpisodeService(TVClient tvClient) {
        this.tvClient = tvClient;
    }

    public EpisodeDetailResponse getEpisodeById(Long episodeId) {
        return tvClient.getEpisodeById(episodeId);
    }
}
