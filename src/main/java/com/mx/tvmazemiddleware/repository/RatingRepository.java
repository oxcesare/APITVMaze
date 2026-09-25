package com.mx.tvmazemiddleware.repository;

import com.mx.tvmazemiddleware.dto.rating.Rating;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RatingRepository extends MongoRepository<Rating, String> {
      List<Rating> findByShowId(Long showId);
}