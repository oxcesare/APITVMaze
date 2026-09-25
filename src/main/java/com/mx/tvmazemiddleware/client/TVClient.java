package com.mx.tvmazemiddleware.client;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import com.mx.tvmazemiddleware.exception.TvNotShowException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class TVClient {

    private final RestClient restClient;

    public TVClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ShowDetailResponse getShowById(Long showId) {

        ShowDetailResponse showDetailResponse;

        try {
            showDetailResponse = restClient.get()
                    .uri("/shows/{id}", showId)
                    .retrieve()
                    .body(ShowDetailResponse.class);
        } catch (RestClientException ex) {
            throw new TvMazeUnavailableException("No se pudo conectar con TVMaze", ex);
        }

        if (showDetailResponse == null) {
            throw new TvNotShowException(String.valueOf(showId));
        }

        return showDetailResponse;
    }

    public List<ShowResponse> searchShows(String searchQuery) {

        SearchResultItem[] results;

        try {
            results = restClient.get()
                    .uri("/search/shows?q={q}", searchQuery)
                    .retrieve()
                    .body(SearchResultItem[].class);
        } catch (RestClientException ex) {
            throw new TvMazeUnavailableException("No se pudo conectar con TVMaze", ex);
        }


        if (results == null || results.length == 0) {
            throw new TvNotShowException(searchQuery);
        }

        return List.of(results).stream()
                .map(SearchResultItem::show)
                .toList();
    }


    record SearchResultItem(ShowResponse show) {
    }
}
