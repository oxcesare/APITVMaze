package com.mx.tvmazemiddleware.controller;


import com.mx.tvmazemiddleware.dto.EpisodeDetailResponse;
import com.mx.tvmazemiddleware.exception.GlobalExceptionHandler;
import com.mx.tvmazemiddleware.service.EpisodeService;
import com.mx.tvmazemiddleware.util.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.V1 + "/shows")
public class EpisodeController {

    private static final Logger log = LoggerFactory.getLogger(EpisodeController.class);

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
