package com.mx.tvmazemiddleware.controller;


import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.service.RatingService;
import com.mx.tvmazemiddleware.util.ApiConstants;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.V1 + "/shows")
public class RatingController {


    private static final Logger log = LoggerFactory.getLogger(RatingController.class);

    private final RatingService ratingService;

    /**
     * Inyeccion de dependencias por constructor
     *
     * @param ratingService
     */
    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingResponse createRating(@Valid @RequestBody RatingRequest ratingRequest) {
        return ratingService.saveRating(ratingRequest);
    }
}
