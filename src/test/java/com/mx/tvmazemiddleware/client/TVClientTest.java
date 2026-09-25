package com.mx.tvmazemiddleware.client;


import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
