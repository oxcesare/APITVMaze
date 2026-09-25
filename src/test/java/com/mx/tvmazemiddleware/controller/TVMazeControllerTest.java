package com.mx.tvmazemiddleware.controller;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.service.TVMazeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(TVMazeController.class)
class TVMazeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TVMazeService tvMazeService;

    @Test
    void encuentraShowStatus200() throws Exception {
        ShowResponse show = new ShowResponse();
        show.setId(1L);
        show.setName("Girls");

        when(tvMazeService.searchShows("girls")).thenReturn(List.of(show));

        mockMvc.perform(get("/search/shows").param("searchQuery", "girls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Girls"));
    }

    @Test
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

        mockMvc.perform(get("/shows/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Under the Dome"));
    }
}
