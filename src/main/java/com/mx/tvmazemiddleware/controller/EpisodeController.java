package com.mx.tvmazemiddleware.controller;


import com.mx.tvmazemiddleware.dto.EpisodeDetailResponse;
import com.mx.tvmazemiddleware.service.EpisodeService;
import org.springframework.web.bind.annotation.*;

@RestController
public class EpisodeController {

    private final EpisodeService episodeService;

    public EpisodeController(EpisodeService episodeService) {
        this.episodeService = episodeService;
    }

    /**
     * Obtiene los detalles de un episodio por su identificador.
     *
     * @param episodeId identificador del episodio
     * @return los detalles del episodio obtenidos de TVMaze
     */
    @GetMapping("/episodes/{id}")
    public EpisodeDetailResponse getEpisodeById(@PathVariable("id") Long episodeId) {
        return episodeService.getEpisodeById(episodeId);
    }
}
