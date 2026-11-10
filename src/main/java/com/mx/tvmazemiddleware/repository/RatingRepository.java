package com.mx.tvmazemiddleware.repository;

import com.mx.tvmazemiddleware.dto.rating.Rating;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends MongoRepository<Rating, String> {
      List<Rating> findByShowId(Long showId);
      Slice<Rating> findByShowId(Long showId, Pageable pageable);
      List<Rating> findByShowIdIn(Collection<Long> showIds);
      Optional<Rating> findByShowIdAndUserId(@NotNull Long showId, @NotBlank String id);
}