package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.rating.Rating;
import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.exception.RatingAlreadyExistsException;
import com.mx.tvmazemiddleware.exception.RatingShowReferenceNotFoundException;
import com.mx.tvmazemiddleware.repository.RatingRepository;
import com.mx.tvmazemiddleware.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private ShowRepository showRepository;

    @InjectMocks
    private RatingService ratingService;

    private RatingRequest ratingRequest;

    @BeforeEach
    void setUp() {
        ratingRequest = new RatingRequest(1L, "2", "Excelente Serie!", 5);
    }


    @Test
    void guardarCuandoShowExiste() {

        ShowDetailResponse mockShow = mock(ShowDetailResponse.class);

        when(showRepository.findById(1L)).thenReturn(Optional.of(mockShow));
        when(ratingRepository.save(any(Rating.class)))
                .thenReturn(new Rating("abc123", "2", 1L, "Excelente Serie!", 5));

        RatingResponse response = ratingService.saveRating(ratingRequest);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.ratingId()).isEqualTo("abc123");

        verify(ratingRepository, times(1)).save(any(Rating.class));

    }

    @Test
    void guardarCuandoShowNoExiste() {
        RatingRequest request = new RatingRequest(0L, "2", "Comentario", 3);
        when(showRepository.findById(0L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.saveRating(request))
                .isInstanceOf(RatingShowReferenceNotFoundException.class)
                .hasMessageContaining("ID 0");
        verify(ratingRepository, never()).save(any(Rating.class));
    }

    @Test
    void guardarCuandoRatingYaExiste() {
        when(showRepository.findById(1L)).thenReturn(Optional.of(mock(ShowDetailResponse.class)));
        when(ratingRepository.findByShowIdAndUserId(1L, "2"))
                .thenReturn(Optional.of(new Rating("abc123", "2", 1L, "Excelente Serie!", 5)));

        assertThatThrownBy(() -> ratingService.saveRating(ratingRequest))
                .isInstanceOf(RatingAlreadyExistsException.class)
                .hasMessageContaining("show con ID 1");
        verify(ratingRepository, never()).save(any(Rating.class));
    }
}
