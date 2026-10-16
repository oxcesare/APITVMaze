package com.mx.tvmazemiddleware.controller;


import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.service.RatingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RatingController {


    private static final Logger log = LoggerFactory.getLogger(RatingController.class);

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/ratings")
    public RatingResponse createRating(@Valid @RequestBody RatingRequest ratingRequest) {
        log.info("Thread: {}", Thread.currentThread().isVirtual());
        return ratingService.saveRating(ratingRequest);
    }
}
