package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.dto.rating.Rating;
import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.exception.RatingAlreadyExistsException;
import com.mx.tvmazemiddleware.exception.RatingShowReferenceNotFoundException;
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

        if (show.isEmpty()) {
            throw new RatingShowReferenceNotFoundException(ratingRequest.showId());
        }

        var existingRating = ratingRepository.findByShowIdAndUserId(ratingRequest.showId(), ratingRequest.userId());

        if (existingRating.isPresent()) {
            throw new RatingAlreadyExistsException(
                    ratingRequest.showId(), ratingRequest.userId(), existingRating.get().id());
        }

        Rating rating = new Rating(null, ratingRequest.userId(), ratingRequest.showId(), ratingRequest.comment(), ratingRequest.rating());
        Rating saved = ratingRepository.save(rating);
        return new RatingResponse("success", "Rating saved successfully", saved.id());
    }
}
