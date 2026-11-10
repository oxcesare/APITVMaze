package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.rating.Rating;
import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.exception.InvalidSortFieldException;
import com.mx.tvmazemiddleware.exception.RatingAlreadyExistsException;
import com.mx.tvmazemiddleware.exception.RatingShowNotFoundException;
import com.mx.tvmazemiddleware.exception.RatingShowReferenceNotFoundException;
import com.mx.tvmazemiddleware.repository.RatingRepository;
import com.mx.tvmazemiddleware.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

import java.util.List;
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

    @Test
    void guardarCuandoDuplicateKeyEnCarreraLanzaRatingAlreadyExists() {
        var existente = new Rating("abc123", "2", 1L, "Excelente Serie!", 5);
        when(showRepository.findById(1L)).thenReturn(Optional.of(mock(ShowDetailResponse.class)));
        when(ratingRepository.findByShowIdAndUserId(1L, "2"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existente));
        when(ratingRepository.save(any(Rating.class))).thenThrow(new DuplicateKeyException("duplicado"));

        assertThatThrownBy(() -> ratingService.saveRating(ratingRequest))
                .isInstanceOfSatisfying(RatingAlreadyExistsException.class,
                        ex -> assertThat(ex.ratingId()).isEqualTo("abc123"));

        verify(ratingRepository, times(2)).findByShowIdAndUserId(1L, "2");
    }



    @Test
    void guardarCuandoDuplicateKeyPersisteTrasReintentoLanzaRatingAlreadyExistsSinId() {
        when(showRepository.findById(1L)).thenReturn(Optional.of(mock(ShowDetailResponse.class)));
        when(ratingRepository.findByShowIdAndUserId(1L, "2")).thenReturn(Optional.empty());
        when(ratingRepository.save(any(Rating.class)))
                .thenThrow(new DuplicateKeyException("duplicado"))
                .thenThrow(new DuplicateKeyException("duplicado"));

        assertThatThrownBy(() -> ratingService.saveRating(ratingRequest))
                .isInstanceOfSatisfying(RatingAlreadyExistsException.class, ex -> {
                    assertThat(ex.ratingId()).isNull();
                    assertThat(ex.showId()).isEqualTo(1L);
                    assertThat(ex.userId()).isEqualTo("2");
                });

        verify(ratingRepository, times(2)).save(any(Rating.class));
    }

    @Test
    void buscarPorShowIDcuandoShowNoExiste() {

        when(showRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.findRatingsByShowId(999L, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(RatingShowNotFoundException.class,
                        ex -> assertThat(ex.showId()).isEqualTo(999L));

        verifyNoInteractions(ratingRepository);
    }

    @Test
    void buscarPorShowRegistradoPageableSinOrden() {

        Slice<Rating> esperado = new SliceImpl<>(List.of(new Rating("abc123", "2", 1L, "Excelente Serie!", 5)));
        when(showRepository.findById(1L)).thenReturn(Optional.of(mock(ShowDetailResponse.class)));
        when(ratingRepository.findByShowId(eq(1L), any(Pageable.class))).thenReturn(esperado);

        var result = ratingService.findRatingsByShowId(1L, PageRequest.of(3, 20));

        assertThat(result).isSameAs(esperado);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ratingRepository, times(1)).findByShowId(eq(1L), captor.capture());
        Pageable enviado = captor.getValue();

        assertThat(enviado.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        assertThat(enviado.getPageNumber()).isEqualTo(3);
        assertThat(enviado.getPageSize()).isEqualTo(20);

    }

    @Test
    void buscarPorShowRegistradoConSortPermitido() {

        Slice<Rating> esperado = new SliceImpl<>(List.of(new Rating("abc123", "2", 1L, "Excelente Serie!", 5)));
        when(showRepository.findById(1L)).thenReturn(Optional.of(mock(ShowDetailResponse.class)));
        when(ratingRepository.findByShowId(eq(1L), any(Pageable.class))).thenReturn(esperado);

        var pageable = PageRequest.of(2, 15, Sort.by(Sort.Direction.DESC, "rating"));
        var result = ratingService.findRatingsByShowId(1L, pageable);

        assertThat(result).isSameAs(esperado);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ratingRepository, times(1)).findByShowId(eq(1L), captor.capture());
        Pageable enviado = captor.getValue();

        assertThat(enviado.getSort()).containsExactly(
                Sort.Order.desc("rating"),
                Sort.Order.desc("id"));
        assertThat(enviado.getPageNumber()).isEqualTo(2);
        assertThat(enviado.getPageSize()).isEqualTo(15);

    }

    @Test
    void buscarPorShowRegistradoConSortNoPermitido() {

        var pageable = PageRequest.of(0, 20, Sort.by("userId"));

        assertThatThrownBy(() -> ratingService.findRatingsByShowId(1L, pageable))
                .isInstanceOfSatisfying(InvalidSortFieldException.class,
                        ex -> assertThat(ex.field()).isEqualTo("userId"));

        verifyNoInteractions(showRepository, ratingRepository);
    }

    @Test
    void buscarPorShowRegistradoConSortIdAscendenteSeDescarta() {

        Slice<Rating> esperado = new SliceImpl<>(List.of(new Rating("abc123", "2", 1L, "Excelente Serie!", 5)));
        when(showRepository.findById(1L)).thenReturn(Optional.of(mock(ShowDetailResponse.class)));
        when(ratingRepository.findByShowId(eq(1L), any(Pageable.class))).thenReturn(esperado);

        var pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.ASC, "id"));
        var result = ratingService.findRatingsByShowId(1L, pageable);

        assertThat(result).isSameAs(esperado);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ratingRepository, times(1)).findByShowId(eq(1L), captor.capture());
        Pageable enviado = captor.getValue();

        assertThat(enviado.getSort()).containsExactly(Sort.Order.desc("id"));
        assertThat(enviado.getPageNumber()).isEqualTo(1);
        assertThat(enviado.getPageSize()).isEqualTo(10);

    }

    @Test
    void guardarCuandoDuplicateKeyYElRegistroDesaparecioReintentaYGuarda() {
        when(showRepository.findById(1L)).thenReturn(Optional.of(mock(ShowDetailResponse.class)));
        when(ratingRepository.findByShowIdAndUserId(1L, "2")).thenReturn(Optional.empty());
        when(ratingRepository.save(any(Rating.class)))
                .thenThrow(new DuplicateKeyException("duplicado"))
                .thenReturn(new Rating("abc123", "2", 1L, "Excelente Serie!", 5));

        RatingResponse response = ratingService.saveRating(ratingRequest);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.ratingId()).isEqualTo("abc123");

        verify(ratingRepository, times(2)).save(any(Rating.class));
        verify(ratingRepository, times(2)).findByShowIdAndUserId(1L, "2");
    }

}
