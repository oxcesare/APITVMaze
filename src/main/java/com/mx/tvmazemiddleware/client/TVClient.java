package com.mx.tvmazemiddleware.client;

import com.mx.tvmazemiddleware.dto.EpisodeDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.exception.*;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

    private static final String OVERLOADED_MESSAGE = "El servicio externo está saturado";

    private final RestClient restClient;

    public TVClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Retry(name = "tvmaze")
    @CircuitBreaker(name = "tvmaze")
    @Bulkhead(name = "tvmaze", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "bulkheadFullEpisode")
    public EpisodeDetailResponse getEpisodeById(Long episodeId) {
        var request = restClient.get().uri("/episodes/{id}", episodeId);
        try {
            return request.retrieve().body(EpisodeDetailResponse.class);
        } catch (RestClientException | IllegalArgumentException ex) {
            throw translateException(ex, () -> new TvNotShowException(String.valueOf(episodeId)));
        }
    }

    @Retry(name = "tvmaze")
    @CircuitBreaker(name = "tvmaze")
    @Bulkhead(name = "tvmaze", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "bulkheadFull")
    public ShowDetailResponse getShowById(Long showId) {
        var request = restClient.get().uri("/shows/{id}", showId);
        try {
            return request.retrieve().body(ShowDetailResponse.class);
        } catch (RestClientException | IllegalArgumentException ex) {
            throw translateException(ex, () -> new TvNotShowException(String.valueOf(showId)));
        }
    }



    @Retry(name = "tvmaze")
    @CircuitBreaker(name = "tvmaze")
    @Bulkhead(name = "tvmaze", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "bulkheadFullSearch")
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


    private ShowDetailResponse bulkheadFull(Long showId, BulkheadFullException ex) {
        throw new TvMazeOverloadedException(OVERLOADED_MESSAGE);
    }

    private List<ShowResponse> bulkheadFullSearch(String searchQuery, BulkheadFullException ex) {
        throw new TvMazeOverloadedException(OVERLOADED_MESSAGE);
    }

    private EpisodeDetailResponse bulkheadFullEpisode(Long episodeId, BulkheadFullException ex) {
        throw new TvMazeOverloadedException(OVERLOADED_MESSAGE);
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
