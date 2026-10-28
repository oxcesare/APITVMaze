package com.mx.tvmazemiddleware.controller;

import com.mx.tvmazemiddleware.dto.EpisodeDetailResponse;
import com.mx.tvmazemiddleware.security.SecurityConfig;
import com.mx.tvmazemiddleware.service.EpisodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EpisodeController.class)
@Import(SecurityConfig.class)
class EpisodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EpisodeService episodeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void episodioPorIdDevuelve200() throws Exception {
        EpisodeDetailResponse episode = new EpisodeDetailResponse(
                42L, "url", "Pilot", 1, 1, "regular", "2013-06-24",
                "22:00", "2013-06-25T02:00:00+00:00", 60, null, null, "<p>Resumen</p>", null);

        when(episodeService.getEpisodeById(42L)).thenReturn(episode);

        mockMvc.perform(get("/api/v1/episodes/42").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("Pilot"))
                .andExpect(jsonPath("$.season").value(1));

        verify(episodeService).getEpisodeById(42L);
    }

    @Test
    void rutaAnteriorShowsEpisodesYaNoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/shows/episodes/42").with(jwt()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void episodioSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/episodes/42"))
                .andExpect(status().isUnauthorized());
    }
}
