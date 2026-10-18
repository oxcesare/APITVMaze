package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.client.TVClient;
import com.mx.tvmazemiddleware.dto.EpisodeDetailResponse;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import com.mx.tvmazemiddleware.exception.TvNotShowException;
import com.mx.tvmazemiddleware.repository.EpisodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EpisodeServiceTest {

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private TVClient tvClient;

    @InjectMocks
    private EpisodeService episodeService;

    @Test
    void obtenerEpisodioDesdeTVMaze() {
        Long episodeId = 42L;
        EpisodeDetailResponse expected = new EpisodeDetailResponse(
                episodeId, null, "Pilot", 1, 1, null, null, null,
                null, null, null, null, null, null
        );
        when(tvClient.getEpisodeById(episodeId)).thenReturn(expected);

        EpisodeDetailResponse result = episodeService.getEpisodeById(episodeId);

        assertThat(result).isSameAs(expected);
        verify(tvClient).getEpisodeById(episodeId);
        verifyNoInteractions(episodeRepository);
    }

    @Test
    void propagarErrorCuandoTVMazeNoEstaDisponible() {
        Long episodeId = 42L;
        TvMazeUnavailableException expected =
                new TvMazeUnavailableException("TVMaze no disponible", new RuntimeException());
        when(tvClient.getEpisodeById(episodeId)).thenThrow(expected);

        assertThatThrownBy(() -> episodeService.getEpisodeById(episodeId))
                .isSameAs(expected);
    }

    @Test
    void propagarErrorCuandoNoExisteElEpisodio() {
        Long episodeId = 42L;
        TvNotShowException expected = new TvNotShowException(episodeId.toString());
        when(tvClient.getEpisodeById(episodeId)).thenThrow(expected);

        assertThatThrownBy(() -> episodeService.getEpisodeById(episodeId))
                .isSameAs(expected);
    }
}
