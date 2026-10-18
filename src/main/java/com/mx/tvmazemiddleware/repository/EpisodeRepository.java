package com.mx.tvmazemiddleware.repository;

import com.mx.tvmazemiddleware.dto.EpisodeDetailResponse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EpisodeRepository  extends MongoRepository<EpisodeDetailResponse, Long> {
}
