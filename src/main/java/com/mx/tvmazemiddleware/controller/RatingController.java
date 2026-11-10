package com.mx.tvmazemiddleware.controller;


import com.mx.tvmazemiddleware.dto.rating.*;
import com.mx.tvmazemiddleware.service.RatingService;
import com.mx.tvmazemiddleware.util.ApiConstants;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public RatingResponse createRating(
            @Valid @RequestBody RatingRequest ratingRequest,
            @AuthenticationPrincipal Jwt jwt) {

        String userIdFromToken = jwt.getSubject();

        RatingRequest secureRequest = new RatingRequest(
                ratingRequest.showId(),
                userIdFromToken,
                ratingRequest.comment(),
                ratingRequest.rating()
        );

        return ratingService.saveRating(secureRequest);
    }

    @GetMapping("/ratings")
    public RatingPageResponse listRatings(@RequestParam Long showId, Pageable pageable) {
        Slice<Rating> ratings = ratingService.findRatingsByShowId(showId, pageable);
        List<RatingItemResponse> content = ratings.getContent().stream()
                .map(r -> new RatingItemResponse(r.id(), r.showId(), r.comment(), r.rating()))
                .toList();
        return new RatingPageResponse(content, ratings.getNumber(), ratings.getSize(), ratings.hasNext());
    }

    @DeleteMapping("/ratings/{ratingId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRating(@PathVariable("ratingId") String ratingId) {
        ratingService.deleteById(ratingId);
    }
}
