package com.mx.tvmazemiddleware.repository;

import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShowRepository extends MongoRepository<ShowDetailResponse, Long> {
}
