package com.mx.tvmazemiddleware.client;

import com.mx.tvmazemiddleware.dto.EpisodeDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.exception.TvMazeRateLimitException;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import com.mx.tvmazemiddleware.exception.TvNotShowException;
import com.mx.tvmazemiddleware.exception.TvShowSearchEmptyException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.List;

@Component
public class TVClient {

    private final RestClient restClient;

    public TVClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public EpisodeDetailResponse getEpisodeById(Long episodeId) {

        EpisodeDetailResponse episodeDetailResponse = null;

        try {
            episodeDetailResponse = restClient.get()
                    .uri("/episodes/{id}", episodeId)
                    .retrieve()
                    .body(EpisodeDetailResponse.class);
            // Dentro de getShowById:
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw new TvMazeRateLimitException("Se excedió el límite de peticiones", ex);
            }
            if (ex.getStatusCode().value() == 404) {
                throw new TvNotShowException(String.valueOf(episodeId));
            }
            throw new TvMazeUnavailableException(
                    "TVMaze respondió con un error HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw new TvMazeUnavailableException(
                    "Error de acceso a recursos al obtener el show con ID " + episodeId, ex);
        } catch (Exception ex) {
            throw new TvMazeUnavailableException(
                    "Error inesperado al obtener el show con ID " + episodeId, ex);
        }

        return episodeDetailResponse;
    }

    public ShowDetailResponse getShowById(Long showId) {

        ShowDetailResponse showDetailResponse = null;

        try {
            showDetailResponse = restClient.get()
                    .uri("/shows/{id}", showId)
                    .retrieve()
                    .body(ShowDetailResponse.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw new TvMazeRateLimitException("Se excedió el límite de peticiones", ex);
            }
            if (ex.getStatusCode().value() == 404) {
                throw new TvNotShowException(String.valueOf(showId));
            }
            throw new TvMazeUnavailableException(
                    "TVMaze respondió con un error HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            if (hasCause(ex, SocketTimeoutException.class)
                    || hasCause(ex, HttpTimeoutException.class)) {
                throw new TvMazeUnavailableException("Timeout al consultar TVMaze", ex);
            }

            if (hasCause(ex, ConnectException.class)) {
                throw new TvMazeUnavailableException("No se pudo establecer conexión con TVMaze", ex);
            }

            throw new TvMazeUnavailableException("Error de acceso al consultar TVMaze", ex);
        } catch (Exception ex) {
            throw new TvMazeUnavailableException(
                    "Error inesperado al obtener el show con ID " + showId, ex);
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
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw new TvMazeRateLimitException("Se excedió el límite de peticiones", ex);
            }
            if (ex.getStatusCode().value() == 404) {
                throw new TvShowSearchEmptyException(String.valueOf(searchQuery));
            }
            throw new TvMazeUnavailableException(
                    "TVMaze respondió con un error HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            if (hasCause(ex, SocketTimeoutException.class)
                    || hasCause(ex, HttpTimeoutException.class)) {
                throw new TvMazeUnavailableException("Timeout al consultar TVMaze", ex);
            }

            if (hasCause(ex, ConnectException.class)) {
                throw new TvMazeUnavailableException("No se pudo establecer conexión con TVMaze", ex);
            }

            throw new TvMazeUnavailableException("Error de acceso al consultar TVMaze", ex);
        } catch (Exception ex) {
            throw new TvMazeUnavailableException(
                    "Error inesperado al obtener el show con ID " + searchQuery, ex);
        }

        if (results == null || results.length == 0) {
            throw new TvShowSearchEmptyException(searchQuery);
        }

        return Arrays.stream(results)
                .map(SearchResultItem::show)
                .toList();
    }

    record SearchResultItem(ShowResponse show) {
    }

    private boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
