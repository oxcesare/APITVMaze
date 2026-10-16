package com.mx.tvmazemiddleware.exception;

import com.mx.tvmazemiddleware.controller.TVMazeController;
import com.mx.tvmazemiddleware.service.TVMazeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TVMazeController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TVMazeService tvMazeService;

    @Test
    void showNoEncontradoTieneTypePropio() throws Exception {
        when(tvMazeService.getShowById(99L)).thenThrow(new TvNotShowException("99"));

        mockMvc.perform(get("/shows/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:show-not-found"));
    }

    @Test
    void busquedaSinResultadosTieneTypePropio() throws Exception {
        when(tvMazeService.searchShows("zzzz")).thenThrow(new TvShowSearchEmptyException("zzzz"));

        mockMvc.perform(get("/search/shows").param("searchQuery", "zzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:search-no-results"));
    }

    @Test
    void rateLimitTieneTypePropio() throws Exception {
        when(tvMazeService.getShowById(1L))
                .thenThrow(new TvMazeRateLimitException("limite", new RuntimeException()));

        mockMvc.perform(get("/shows/1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:rate-limited"));
    }
}
