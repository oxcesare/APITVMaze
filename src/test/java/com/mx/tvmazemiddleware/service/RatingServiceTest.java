package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.rating.Rating;
import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
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
        ratingRequest = new RatingRequest(1L, "Excelente Serie!", 5);
    }


    @Test
    void guardarCuandoShowExiste() {

        ShowDetailResponse mockShow = mock(ShowDetailResponse.class);

        when(showRepository.findById(1L)).thenReturn(Optional.of(mockShow));

        RatingResponse response = ratingService.saveRating(ratingRequest);

        assertThat(response.status()).isEqualTo("success");

        verify(ratingRepository, times(1)).save(any(Rating.class));

    }

    @Test
    void guardarCuandoShowNoExiste() {
        when(showRepository.findById(1L)).thenReturn(Optional.empty());

        RatingResponse response = ratingService.saveRating(ratingRequest);

        assertThat(response.status()).isEqualTo("error");
        verify(ratingRepository, never()).save(any(Rating.class));
    }

}
