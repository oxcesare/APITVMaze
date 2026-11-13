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
        var request = restClient.get().uri("/episodes/{id}", episodeId);
        try {
            return request.retrieve().body(EpisodeDetailResponse.class);
        } catch (RestClientException | IllegalArgumentException ex) {
            throw translateException(ex, () -> new TvNotShowException(String.valueOf(episodeId)));
        }
    }

    @Retry(name = "tvmaze")
    public ShowDetailResponse getShowById(Long showId) {
        var request = restClient.get().uri("/shows/{id}", showId);
        try {
            return request.retrieve().body(ShowDetailResponse.class);
        } catch (RestClientException | IllegalArgumentException ex) {
            throw translateException(ex, () -> new TvNotShowException(String.valueOf(showId)));
        }
    }

    @Retry(name = "tvmaze")
    public List<ShowResponse> searchShows(String searchQuery) {
        var request = restClient.get().uri("/search/shows?q={q}", searchQuery);

        SearchResultItem[] results;
        try {
            results = request.retrieve().body(SearchResultItem[].class);
        } catch (RestClientException | IllegalArgumentException ex) {
            throw translateException(ex, () -> new TvShowSearchEmptyException(searchQuery));
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

    private RuntimeException translateException(RuntimeException ex, Supplier<RuntimeException> notFound) {
        RestClientException clientException = ex instanceof RestClientException restClientException
                ? restClientException
                : new RestClientException("Respuesta inválida de TVMaze", ex);

        if (clientException instanceof RestClientResponseException responseException) {
            if (responseException.getStatusCode().value() == 429) {
                return new TvMazeRateLimitException("Se excedió el límite de peticiones", clientException);
            }
            if (responseException.getStatusCode().value() == 404) {
                return notFound.get();
            }
            if (responseException.getStatusCode().is4xxClientError()) {
                return new TvMazeClientErrorException(
                        "TVMaze rechazó la solicitud con HTTP " + responseException.getStatusCode().value(),
                        clientException);



            }
            return new TvMazeUnavailableException(
                    "TVMaze respondió con un error HTTP " + responseException.getStatusCode().value(), clientException);
        }
        if (clientException instanceof ResourceAccessException) {
            if (hasCause(clientException, SocketTimeoutException.class)
                    || hasCause(clientException, HttpTimeoutException.class)) {
                return new TvMazeTimeoutException("Timeout al consultar TVMaze", clientException);
            }
            if (hasCause(clientException, ConnectException.class)) {
                return new TvMazeUnavailableException("No se pudo establecer conexión con TVMaze", clientException);
            }
            return new TvMazeUnavailableException("Error de acceso al consultar TVMaze", clientException);
        }
        return new TvMazeUnavailableException("Error de acceso al consultar TVMaze", clientException);
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
