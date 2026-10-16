package com.mx.tvmazemiddleware.controller;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.service.TVMazeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TVMazeController {

    private static final Logger log = LoggerFactory.getLogger(TVMazeController.class);

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
        log.info("Thread: {}", Thread.currentThread().isVirtual());
        return tvMazeService.searchShows(searchQuery);
    }

    @GetMapping("/shows/{id}")
    public ShowDetailResponse getShowById(@PathVariable("id") Long showId) {
        log.info("Thread: {}", Thread.currentThread().isVirtual());
        return tvMazeService.getShowById(showId);
    }
}
