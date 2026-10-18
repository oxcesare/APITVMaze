package com.mx.tvmazemiddleware.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.exception.RatingAlreadyExistsException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rating-show-reference-not-found"))
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
}
