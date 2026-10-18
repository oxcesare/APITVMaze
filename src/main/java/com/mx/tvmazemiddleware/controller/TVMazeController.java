package com.mx.tvmazemiddleware.controller;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.service.TVMazeService;
import com.mx.tvmazemiddleware.util.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiConstants.V1 + "/shows")
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

    @GetMapping("/search")
    public List<ShowResponse> searchShows(@RequestParam String searchQuery) {
        return tvMazeService.searchShows(searchQuery);
    }

    @GetMapping("/{id}")
    public ShowDetailResponse getShowById(@PathVariable("id") Long showId) {
        return tvMazeService.getShowById(showId);
    }
}
