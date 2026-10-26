package com.mx.tvmazemiddleware.exception;

import com.mx.tvmazemiddleware.controller.TVMazeController;
import com.mx.tvmazemiddleware.service.TVMazeService;
import com.mx.tvmazemiddleware.util.ApiConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TVMazeController.class)
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TVMazeService tvMazeService;

    @Test
    void showNoEncontradoTieneTypePropio() throws Exception {
        when(tvMazeService.getShowById(99L)).thenThrow(new TvNotShowException("99"));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:show-not-found"));
    }

    @Test
    void busquedaSinResultadosTieneTypePropio() throws Exception {
        when(tvMazeService.searchShows("zzzz")).thenThrow(new TvShowSearchEmptyException("zzzz"));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/search").param("searchQuery", "zzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:search-no-results"));
    }

    @Test
    void rateLimitTieneTypePropio() throws Exception {
        when(tvMazeService.getShowById(1L))
                .thenThrow(new TvMazeRateLimitException("limite", new RuntimeException()));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rate-limited"));
    }
}
