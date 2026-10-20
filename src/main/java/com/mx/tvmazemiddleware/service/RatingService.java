package com.mx.tvmazemiddleware.service;

import com.mx.tvmazemiddleware.dto.rating.Rating;
import com.mx.tvmazemiddleware.dto.rating.RatingRequest;
import com.mx.tvmazemiddleware.dto.rating.RatingResponse;
import com.mx.tvmazemiddleware.exception.InvalidSortFieldException;
import com.mx.tvmazemiddleware.exception.RatingAlreadyExistsException;
import com.mx.tvmazemiddleware.exception.RatingShowNotFoundException;
import com.mx.tvmazemiddleware.exception.RatingShowReferenceNotFoundException;
import com.mx.tvmazemiddleware.repository.RatingRepository;
import com.mx.tvmazemiddleware.repository.ShowRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
public class RatingService {

    // Comparación exacta (sensible a mayúsculas): las propiedades del repositorio también lo son,
    // así que "Rating" o "ID" se rechazan.
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "rating");

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

    public Slice<Rating> findRatingsByShowId(Long showId, Pageable pageable) {

        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new InvalidSortFieldException(order.getProperty());
            }
        });

        if (showRepository.findById(showId).isEmpty()) {
            throw new RatingShowNotFoundException(showId);
        }

        List<Sort.Order> orders = new ArrayList<>();
        pageable.getSort().stream()
                .filter(order -> !order.getProperty().equals("id"))
                .forEach(orders::add);
        orders.add(Sort.Order.desc("id"));

        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
        return ratingRepository.findByShowId(showId, sorted);
    }
}
