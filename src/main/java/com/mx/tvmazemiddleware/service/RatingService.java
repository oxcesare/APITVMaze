package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.dto.rating.Rating;
import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.repository.RatingRepository;
import com.mx.tvmazemiddleware.repository.ShowRepository;
import org.springframework.stereotype.Service;


@Service
public class RatingService {

    private final ShowRepository showRepository;
    private final RatingRepository ratingRepository;


    public RatingService(ShowRepository showRepository, RatingRepository ratingRepository) {
        this.showRepository = showRepository;
        this.ratingRepository = ratingRepository;
    }

    public RatingResponse saveRating(RatingRequest ratingRequest) {
        var show = showRepository.findById(ratingRequest.showId());

        if (!show.isPresent()) {
            return new RatingResponse("error", "Show not found");

        }

        Rating rating = new Rating(null, ratingRequest.showId(), ratingRequest.comment(), ratingRequest.rating());
        ratingRepository.save(rating);
        return new RatingResponse("success", "Rating saved successfully");


    }
}
