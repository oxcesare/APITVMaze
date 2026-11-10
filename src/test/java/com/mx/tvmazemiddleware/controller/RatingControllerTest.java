package com.mx.tvmazemiddleware.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.tvmazemiddleware.dto.rating.Rating;
import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.exception.*;
import com.mx.tvmazemiddleware.security.SecurityConfig;
import com.mx.tvmazemiddleware.service.RatingService;
import com.mx.tvmazemiddleware.util.ApiConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RatingController.class)
@Import(SecurityConfig.class)
class RatingControllerTest {

    @MockBean
    private JwtDecoder jwtDecoder;

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
                        .with(jwt().jwt(j -> j.subject("2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ratingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Rating saved successfully"))
                .andExpect(jsonPath("$.ratingId").value("abc123"));

    }

    @Test
    void guardarRatingConJwtAutenticado() throws Exception {
        RatingRequest ratingRequest = new RatingRequest(1L, "2", "Excelente Serie!", 5);
        RatingResponse response = new RatingResponse("success", "Rating saved successfully", "abc123");

        when(ratingService.saveRating(any(RatingRequest.class))).thenReturn(response);

        mockMvc.perform(post(ApiConstants.V1 + "/shows/ratings")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ratingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ratingId").value("abc123"));
    }

    @Test
    void errorCuandoNoExisteShow() throws Exception {
        RatingRequest request = new RatingRequest(0L, "2", "Comentario", 3);
        when(ratingService.saveRating(any(RatingRequest.class)))
                .thenThrow(new RatingShowReferenceNotFoundException(0L));

        mockMvc.perform(post(ApiConstants.V1 + "/shows/ratings")
                        .with(jwt().jwt(j -> j.subject("2")))
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
                        .with(jwt().jwt(j -> j.subject("2")))
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
                        .with(jwt().jwt(j -> j.subject("2")))
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
                        .with(jwt().jwt(j -> j.subject("2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rating-already-exists"))
                .andExpect(jsonPath("$.ratingId").value("abc123"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void errorCuandoElRatingYaExisteSinIdOmiteElCampoRatingId() throws Exception {
        RatingRequest request = new RatingRequest(1L, "2", "Comentario", 3);
        when(ratingService.saveRating(any(RatingRequest.class)))
                .thenThrow(new RatingAlreadyExistsException(1L, "2", null));

        mockMvc.perform(post(ApiConstants.V1 + "/shows/ratings")
                        .with(jwt().jwt(j -> j.subject("2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rating-already-exists"))
                .andExpect(jsonPath("$.showId").value(1))
                .andExpect(jsonPath("$.userId").value("2"))
                .andExpect(jsonPath("$", not(hasKey("ratingId"))))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void listarRatingsSinPaginacionDevuelvePrimeraPaginaPorDefectoSinUserId() throws Exception {
        when(ratingService.findRatingsByShowId(eq(1L), eq(PageRequest.of(0, 20))))
                .thenReturn(new SliceImpl<>(List.of(new Rating("abc123", "user-42", 1L, "Excelente", 5)),
                        PageRequest.of(0, 20), false));

        mockMvc.perform(get("/api/v1/shows/ratings").param("showId", "1")
                        .with(jwt())) // <-- Autenticación añadida
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

        mockMvc.perform(get("/api/v1/shows/ratings").param("showId", "999")
                        .with(jwt())) // <-- Autenticación añadida
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rating-show-not-registered"))
                .andExpect(jsonPath("$.showId").value(999))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listarRatingsSinShowIdDevuelveBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/shows/ratings")
                        .with(jwt())) // <-- Autenticación añadida
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:bad-request"));

        verifyNoInteractions(ratingService);
    }

    @Test
    void listarRatingsConSortNoPermitidoDevuelveBadRequest() throws Exception {
        when(ratingService.findRatingsByShowId(eq(1L), any()))
                .thenThrow(new InvalidSortFieldException("userId"));

        mockMvc.perform(get("/api/v1/shows/ratings")
                        .param("showId", "1")
                        .param("sort", "userId")
                        .with(jwt())) // <-- Autenticación añadida
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:validation-error"))
                .andExpect(jsonPath("$.title").value("Error de validación"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.field").value("userId"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(not(containsString("allowed"))))
                .andExpect(content().string(not(containsString("permitidos"))))
                .andExpect(content().string(not(containsString("\"rating\""))));
    }

    @Test
    void listarRatingsConPaginaTamanioYOrdenPersonalizados() throws Exception {

        Pageable esperado = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "rating"));

        when(ratingService.findRatingsByShowId(eq(1L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(new Rating("abc123", "user-42", 1L, "Excelente", 5)),
                        esperado, true));

        mockMvc.perform(get("/api/v1/shows/ratings")
                        .param("showId", "1")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "rating,desc")
                        .with(jwt())) // <-- Autenticación añadida
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content.length()").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ratingService).findRatingsByShowId(eq(1L), captor.capture());
        Pageable recibido = captor.getValue();
        Assertions.assertEquals(2, recibido.getPageNumber());
        Assertions.assertEquals(5, recibido.getPageSize());
        Assertions.assertEquals(Sort.by(Sort.Direction.DESC, "rating"), recibido.getSort());
    }

    @Test
    void listarRatingsConSizeMayorAlMaximoDevuelveSizeEfectivo() throws Exception {
        when(ratingService.findRatingsByShowId(eq(1L), any(Pageable.class)))
                .thenAnswer(inv -> new SliceImpl<>(
                        List.of(new Rating("abc123", "user-42", 1L, "Excelente", 5)),
                        inv.<Pageable>getArgument(1), false));

        mockMvc.perform(get("/api/v1/shows/ratings")
                        .param("showId", "1")
                        .param("size", "100000")
                        .with(jwt())) // <-- Autenticación añadida
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ratingService).findRatingsByShowId(eq(1L), captor.capture());
        Assertions.assertEquals(100, captor.getValue().getPageSize());
    }


    @Test
    void listarRatingsConSizeCeroUsaElTamanioPorDefecto() throws Exception {
        mockListado();

        mockMvc.perform(get("/api/v1/shows/ratings")
                        .param("showId", "1")
                        .param("size", "0")
                        .with(jwt())) // <-- Autenticación añadida
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));

        Assertions.assertEquals(20, pageableRecibido().getPageSize());
    }

    @Test
    void listarRatingsConPaginaNegativaUsaLaPrimeraPagina() throws Exception {
        mockListado();

        mockMvc.perform(get("/api/v1/shows/ratings")
                        .param("showId", "1")
                        .param("page", "-1")
                        .with(jwt())) // <-- Autenticación añadida
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));

        Assertions.assertEquals(0, pageableRecibido().getPageNumber());
    }

    private void mockListado() {
        when(ratingService.findRatingsByShowId(eq(1L), any(Pageable.class)))
                .thenAnswer(inv -> new SliceImpl<>(
                        List.of(new Rating("abc123", "user-42", 1L, "Excelente", 5)),
                        inv.<Pageable>getArgument(1), false));
    }

    private Pageable pageableRecibido() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ratingService).findRatingsByShowId(eq(1L), captor.capture());
        return captor.getValue();
    }

    @Test
    @WithMockUser(roles = "USER")
    void borrarRatingSinRolAdminDevuelve403() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(com.mx.tvmazemiddleware.util.ApiConstants.V1 + "/shows/ratings/123"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void borrarRatingConRolAdminDevuelve204() throws Exception {
        String ratingId = "6ad2a0b1dffd796377c6c49c";

        mockMvc.perform(delete(ApiConstants.V1 + "/shows/ratings/" + ratingId))
                .andExpect(status().isNoContent());

        verify(ratingService).deleteById(eq(ratingId));
    }

    @Test
    void borrarRatingConBearerTokenConRolAdminPasaPorElConversorYDevuelve204() throws Exception {
        String ratingId = "6ad2a0b1dffd796377c6c49c";
        org.springframework.security.oauth2.jwt.Jwt token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("tok")
                .header("alg", "none")
                .subject("1")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(900))
                .build();
        when(jwtDecoder.decode("tok")).thenReturn(token);

        mockMvc.perform(delete(ApiConstants.V1 + "/shows/ratings/" + ratingId)
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isNoContent());

        verify(ratingService).deleteById(eq(ratingId));
    }

    @Test
    void crearRatingExtraeUserIdDelToken() throws Exception {
        String jsonBody = "{\"showId\": 1, \"comment\": \"Excelente\", \"rating\": 5, \"userId\": \"otro\"}";

        mockMvc.perform(post(ApiConstants.V1 + "/shows/ratings")
                        .with(jwt().jwt(j -> j.subject("u7")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated());

        ArgumentCaptor<RatingRequest> captor = forClass(RatingRequest.class);
        verify(ratingService).saveRating(captor.capture());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().userId()).isEqualTo("u7");
    }

    @Test
    @WithAnonymousUser
    void crearRatingSinTokenDevuelve401() throws Exception {
        String jsonBody = "{\"showId\": 1, \"comment\": \"Excelente\", \"rating\": 5, \"userId\": \"u1\"}";

        mockMvc.perform(post(com.mx.tvmazemiddleware.util.ApiConstants.V1 + "/shows/ratings")
                        // SE REMOVIO .with(jwt().jwt(...)) porque este test DEBE ser anónimo sin token
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ratingService);
    }
}