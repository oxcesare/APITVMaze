package com.mx.tvmazemiddleware.client;


import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.exception.TvMazeRateLimitException;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import com.mx.tvmazemiddleware.exception.TvNotShowException;

import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;


@ExtendWith(MockitoExtension.class)
class TVClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private TVClient tvClient;

    @BeforeEach
    void setUp() {
        tvClient = new TVClient(restClient);
    }


    @Test
    @SuppressWarnings("unchecked")
    void obtenerShowTvMazeResponde() {

        ShowDetailResponse expectedShow = construirShow(1L, "Breaking Bad");


        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/shows/{id}"), eq(1L))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(ShowDetailResponse.class)).thenReturn(expectedShow);

        ShowDetailResponse result = tvClient.getShowById(1L);

        assertThat(result.name()).isEqualTo("Breaking Bad");

    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void lanzaTvMazeUnavailableCuandoGetShowRespondeErrorDelServidor(int statusCode) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TVClient client = new TVClient(builder.build());

        server.expect(requestTo("http://localhost/shows/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));

        assertThatThrownBy(() -> client.getShowById(1L))
                .isInstanceOf(TvMazeUnavailableException.class)
                .hasMessageContaining(String.valueOf(statusCode));

        server.verify();
    }

    @Test
    void lanzaRateLimitCuandoGetShowResponde429() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TVClient client = new TVClient(builder.build());

        server.expect(requestTo("http://localhost/shows/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.getShowById(1L))
                .isInstanceOf(TvMazeRateLimitException.class);

        server.verify();
    }

    @Test
    void lanzaTvNotShowCuandoBusquedaNoTieneResultados() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TVClient client = new TVClient(builder.build());

        server.expect(requestTo("http://localhost/search/shows?q=zzzz"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchShows("zzzz"))
                .isInstanceOf(TvNotShowException.class);

        server.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void lanzarExceptionCuandoTVNoEsteDisponible() {
        when(restClient.get()).thenThrow(new RestClientException("Timeout"));

        assertThatThrownBy(() -> tvClient.getShowById(1L))
                .isInstanceOf(TvMazeUnavailableException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listaShowTvMazeDisponible() {
        ShowResponse show = new ShowResponse();
        show.setId(1L);
        show.setName("Girls");

        TVClient.SearchResultItem[] mockResults = new TVClient.SearchResultItem[]{
                new TVClient.SearchResultItem(show)
        };

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq("girls"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(TVClient.SearchResultItem[].class)).thenReturn(mockResults);

        List<ShowResponse> result = tvClient.searchShows("girls");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Girls");
    }


    private ShowDetailResponse construirShow(Long id, String name) {
        return new ShowDetailResponse(
                id, "url", name, "Scripted", "English",
                List.of("Drama"), "Ended", 60, 60,
                "2013-06-24", "2015-09-10", "site", null,
                null, 100, null, null,
                null, null, null, "summary",
                123456L, null, null
        );
    }


}
