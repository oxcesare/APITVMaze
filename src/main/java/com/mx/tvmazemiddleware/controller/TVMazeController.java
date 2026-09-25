package com.mx.tvmazemiddleware.controller;

import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.service.TVMazeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TVMazeController {

    private final TVMazeService tvMazeService;

    /**
     * Inyeccion de dependencias por constructor
     *
     * @param tvMazeService
     */
    public TVMazeController(TVMazeService tvMazeService) {
        this.tvMazeService = tvMazeService;
    }

    @GetMapping("/search/shows")
    public List<ShowResponse> searchShows(@RequestParam String searchQuery) {
        return tvMazeService.searchShows(searchQuery);
    }
}
