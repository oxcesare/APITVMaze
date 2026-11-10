package com.mx.tvmazemiddleware.controller;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.security.SecurityConfig;
import com.mx.tvmazemiddleware.service.TVMazeService;
import com.mx.tvmazemiddleware.util.ApiConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@Import(SecurityConfig.class)
@WebMvcTest(TVMazeController.class)
class TVMazeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TVMazeService tvMazeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser
    void encuentraShowStatus200() throws Exception {
        ShowResponse show = new ShowResponse();
        show.setId(1L);
        show.setName("Girls");

        when(tvMazeService.searchShows("girls")).thenReturn(List.of(show));

        mockMvc.perform(get(ApiConstants.V1 + "/shows/search").param("searchQuery", "girls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Girls"));
    }

    @Test
    @WithMockUser
    void showPorIdStatus200() throws Exception {
        ShowDetailResponse show = new ShowDetailResponse(
                1L, "url", "Under the Dome", "Scripted", "English",
                List.of("Drama"), "Ended", 60, 60,
                "2013-06-24", "2015-09-10", "site", null,
                null, 100, null, null,
                null, null, null, "summary",
                123456L, null, null
        );

        when(tvMazeService.getShowById(1L)).thenReturn(show);

        mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Under the Dome"));
    }

    @Test
    void showPorIdSinCredencialesStatus401() throws Exception {
        mockMvc.perform(get(ApiConstants.V1 + "/shows/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void showPorIdTokenInvalidoStatus401() throws Exception {

        org.springframework.security.oauth2.jwt.BadJwtException badJwtException =
                new org.springframework.security.oauth2.jwt.BadJwtException("Token inválido");

        when(jwtDecoder.decode("abc")).thenThrow(badJwtException);

        mockMvc.perform(get(ApiConstants.V1 + "/shows/1")
                        .header("Authorization", "Bearer abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:tvmaze:problem:unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

}
