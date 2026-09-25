package com.mx.tvmazemiddleware.service;


import com.mx.tvmazemiddleware.client.TVClient;
import com.mx.tvmazemiddleware.dto.*;

import java.util.List;

import com.mx.tvmazemiddleware.repository.ShowRepository;
import org.springframework.stereotype.Service;

@Service
public class TVMazeService {

    private final TVClient tvClient;
    private final ShowRepository showRepository;

    public TVMazeService(TVClient tvClient, ShowRepository showRepository) {
        this.tvClient = tvClient;
        this.showRepository = showRepository;
    }

    public List<ShowResponse> searchShows(String searchQuery) {
        return tvClient.searchShows(searchQuery);
    }

    public ShowDetailResponse getShowById(Long showId) {
        return showRepository.findById(showId)
                .orElseGet(() -> {
                    ShowDetailResponse show = tvClient.getShowById(showId);
                    return showRepository.save(show);
                });
    }
}


