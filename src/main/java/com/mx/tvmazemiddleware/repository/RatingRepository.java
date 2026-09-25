package com.mx.tvmazemiddleware.repository;

import com.mx.tvmazemiddleware.dto.rating.Rating;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RatingRepository extends MongoRepository<Rating, String> {
}