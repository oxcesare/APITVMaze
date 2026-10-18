package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.client.TVClient;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.dto.rating.Rating;
import com.mx.tvmazemiddleware.repository.RatingRepository;
import com.mx.tvmazemiddleware.repository.ShowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class TVMazeServiceTest {

    @Mock
    private TVClient tvClient;

    @Mock
    private ShowRepository showRepository;

    @Mock
    private RatingRepository ratingRepository;

    @InjectMocks
    private TVMazeService tvMazeService;

    @Test
    void agregarComentariosAlEncontrarShow() {
        ShowResponse show = new ShowResponse();
        show.setId(1L);
        show.setName("Girls");

        Rating rating = new Rating("abc123", 1L, "Muy buena", 5);

        when(tvClient.searchShows("girls")).thenReturn(List.of(show));
        when(ratingRepository.findByShowId(1L)).thenReturn(List.of(rating));

        List<ShowResponse> result = tvMazeService.searchShows("girls");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getComments()).hasSize(1);
        assertThat(result.get(0).getComments().get(0).comment()).isEqualTo("Muy buena");
    }

    @Test
    void mostrarShowDesdeCache() {
        ShowDetailResponse showEnCache = construirShow(1L, "Under the Dome");

        when(showRepository.findById(1L)).thenReturn(Optional.of(showEnCache));
        when(ratingRepository.findByShowId(1L)).thenReturn(List.of());

        ShowDetailResponse result = tvMazeService.getShowById(1L);

        assertThat(result.name()).isEqualTo("Under the Dome");
        verify(tvClient, never()).getShowById(anyLong());
        verify(showRepository, never()).save(any());
    }


    @Test
    void mostrarShowConsultandoAPI() {

        ShowDetailResponse desdeApi = construirShow(1L, "Under the Dome");

        when(showRepository.findById(1L)).thenReturn(Optional.empty());
        when(tvClient.getShowById(1L)).thenReturn(desdeApi);
        when(showRepository.save(desdeApi)).thenReturn(desdeApi);
        when(ratingRepository.findByShowId(1L)).thenReturn(List.of());

        ShowDetailResponse result = tvMazeService.getShowById(1L);

        assertThat(result.name()).isEqualTo("Under the Dome");
        verify(tvClient, times(1)).getShowById(1L);
        verify(showRepository, times(1)).save(desdeApi);
    }

    private ShowDetailResponse construirShow(Long id, String name) {
        return new ShowDetailResponse(id, "url", name, "Scripted", "English",
                List.of("Drama"), "Ended", 60, 60,
                "2013-06-24", "2015-09-10", "site", null,
                null, 100, null, null,
                null, null, null, "summary",
                123456L, null, null);
    }


}
