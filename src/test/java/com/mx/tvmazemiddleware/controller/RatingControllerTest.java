package com.mx.tvmazemiddleware.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.exception.RatingAlreadyExistsException;
import com.mx.tvmazemiddleware.exception.RatingShowNotFoundException;
import com.mx.tvmazemiddleware.exception.RatingShowReferenceNotFoundException;
import com.mx.tvmazemiddleware.exception.TvMazeRateLimitException;
import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import com.mx.tvmazemiddleware.service.RatingService;
import com.mx.tvmazemiddleware.util.ApiConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.tvmazemiddleware.dto.rating.Rating;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(RatingController.class)
class RatingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RatingService ratingService;


    @Test
    void guardarRating() throws Exception {
        RatingRequest ratingRequest = new RatingRequest(1L, "2", "Excelente Serie!", 5);
        RatingResponse response = new RatingResponse("success", "Rating saved successfully", "abc123");

        when(ratingService.saveRating(any(RatingRequest.class))).thenReturn(response);

        mockMvc.perform(post(ApiConstants.V1 + "/shows/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ratingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Rating saved successfully"))
                .andExpect(jsonPath("$.ratingId").value("abc123"));

    }

    @Test
    void errorCuandoNoExisteShow() throws Exception {
        RatingRequest request = new RatingRequest(0L, "2", "Comentario", 3);
        when(ratingService.saveRating(any(RatingRequest.class)))
                .thenThrow(new RatingShowReferenceNotFoundException(0L));

        mockMvc.perform(post(ApiConstants.V1 + "/shows/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rating-show-not-registered"))
                .andExpect(jsonPath("$.detail")
                        .value("No se encontró en nuestra base de datos un show con ID 0; no se puede guardar la calificación."))
                .andExpect(jsonPath("$.showId").value(0))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void errorCuandoTVMazeNoEstaDisponibleAlGuardarRating() throws Exception {
        RatingRequest request = new RatingRequest(1L, "2", "Comentario", 3);
        when(ratingService.saveRating(any(RatingRequest.class)))
                .thenThrow(new TvMazeUnavailableException("TVMaze respondió con un error HTTP 503", null));

        mockMvc.perform(post(ApiConstants.V1 + "/shows/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:upstream-unavailable"));
    }

    @Test
    void errorCuandoTVMazeLimitaPeticionesAlGuardarRating() throws Exception {
        RatingRequest request = new RatingRequest(1L, "2", "Comentario", 3);
        when(ratingService.saveRating(any(RatingRequest.class)))
                .thenThrow(new TvMazeRateLimitException("Se excedió el límite de peticiones", null));

        mockMvc.perform(post(ApiConstants.V1 + "/shows/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rate-limited"));
    }

    @Test
    void errorCuandoElRatingYaExiste() throws Exception {
        RatingRequest request = new RatingRequest(1L, "2", "Comentario", 3);
        when(ratingService.saveRating(any(RatingRequest.class)))
                .thenThrow(new RatingAlreadyExistsException(1L, "2", "abc123"));

        mockMvc.perform(post(ApiConstants.V1 + "/shows/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rating-already-exists"))
                .andExpect(jsonPath("$.ratingId").value("abc123"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void listarRatingsSinPaginacionDevuelvePrimeraPaginaPorDefectoSinUserId() throws Exception {
        when(ratingService.findRatingsByShowId(eq(1L), eq(PageRequest.of(0, 20))))
                .thenReturn(new SliceImpl<>(List.of(new Rating("abc123", "user-42", 1L, "Excelente", 5)),
                        PageRequest.of(0, 20), false));

        mockMvc.perform(get("/api/v1/shows/ratings").param("showId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("abc123"))
                .andExpect(jsonPath("$.content[0].comment").value("Excelente"))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(content().string(not(containsString("userId"))))
                .andExpect(content().string(not(containsString("user-42"))));
    }

    @Test
    void listarRatingsDeShowNoRegistradoDevuelveNotFound() throws Exception {
        when(ratingService.findRatingsByShowId(eq(999L), any()))
                .thenThrow(new RatingShowNotFoundException(999L));

        mockMvc.perform(get("/api/v1/shows/ratings").param("showId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rating-show-not-registered"))
                .andExpect(jsonPath("$.showId").value(999))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listarRatingsSinShowIdDevuelveBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/shows/ratings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:bad-request"));

        verifyNoInteractions(ratingService);
    }
}
