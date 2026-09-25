package com.mx.tvmazemiddleware.service;


import com.mx.tvmazemiddleware.client.TVClient;
import com.mx.tvmazemiddleware.dto.*;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class TVMazeService {

    private final TVClient tvClient;

    public TVMazeService(TVClient tvClient) {
        this.tvClient = tvClient;
    }

    public List<ShowResponse> searchShows(String searchQuery) {
        return tvClient.searchShows(searchQuery);
    }

    public ShowDetailResponse getShowById(Long showId) {
        return tvClient.getShowById(showId);
    }

}


