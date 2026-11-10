package com.mx.tvmazemiddleware.client;

import com.mx.tvmazemiddleware.dto.EpisodeDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.exception.*;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Component
public class TVClient {

    private final RestClient restClient;

    public TVClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Retry(name = "tvmaze")
    public EpisodeDetailResponse getEpisodeById(Long episodeId) {
        try {
            return restClient.get()
                    .uri("/episodes/{id}", episodeId)
                    .retrieve()
                    .body(EpisodeDetailResponse.class);
        } catch (RestClientException ex) {
            throw translateException(ex, () -> new TvNotShowException(String.valueOf(episodeId)),
                    "obtener el episodio con ID " + episodeId);
        }
    }

    @Retry(name = "tvmaze")
    public ShowDetailResponse getShowById(Long showId) {

        try {
            return restClient.get()
                    .uri("/shows/{id}", showId)
                    .retrieve()
                    .body(ShowDetailResponse.class);
        } catch (RestClientException ex) {
            throw translateException(ex, () -> new TvNotShowException(String.valueOf(showId)),
                    "obtener el show con ID " + showId);
        }
    }

    @Retry(name = "tvmaze")
    public List<ShowResponse> searchShows(String searchQuery) {

        SearchResultItem[] results;

        try {
            results = restClient.get()
                    .uri("/search/shows?q={q}", searchQuery)
                    .retrieve()
                    .body(SearchResultItem[].class);
        } catch (RestClientException ex) {
            throw translateException(ex, () -> new TvShowSearchEmptyException(searchQuery),
                    "buscar shows con la consulta " + searchQuery);
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

    private RuntimeException translateException(RestClientException ex, Supplier<RuntimeException> notFound, String operation) {
        if (ex instanceof RestClientResponseException responseException) {
            if (responseException.getStatusCode().value() == 429) {
                return new TvMazeRateLimitException("Se excedió el límite de peticiones", ex);
            }
            if (responseException.getStatusCode().value() == 404) {
                return notFound.get();
            }
            if (responseException.getStatusCode().is4xxClientError()) {
                return new TvMazeClientErrorException(
                        "TVMaze rechazó la solicitud con HTTP " + responseException.getStatusCode().value(), ex);
            }
            return new TvMazeUnavailableException(
                    "TVMaze respondió con un error HTTP " + responseException.getStatusCode().value(), ex);
        }
        if (ex instanceof ResourceAccessException) {
            if (hasCause(ex, SocketTimeoutException.class) || hasCause(ex, HttpTimeoutException.class)) {
                return new TvMazeTimeoutException("Timeout al consultar TVMaze", ex);
            }
            if (hasCause(ex, ConnectException.class)) {
                return new TvMazeUnavailableException("No se pudo establecer conexión con TVMaze", ex);
            }
            return new TvMazeUnavailableException("Error de acceso al consultar TVMaze", ex);
        }
        return new TvMazeUnavailableException("Error inesperado al " + operation, ex);
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
