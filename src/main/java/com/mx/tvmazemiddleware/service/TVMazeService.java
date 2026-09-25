package com.mx.tvmazemiddleware.service;


import com.mx.tvmazemiddleware.client.TVClient;
import com.mx.tvmazemiddleware.dto.rating.Rating;

import java.util.List;

import com.mx.tvmazemiddleware.dto.RatingInfo;
import com.mx.tvmazemiddleware.dto.ShowDetailResponse;
import com.mx.tvmazemiddleware.dto.ShowResponse;
import com.mx.tvmazemiddleware.repository.RatingRepository;
import com.mx.tvmazemiddleware.repository.ShowRepository;
import org.springframework.stereotype.Service;

@Service
public class TVMazeService {

    private final TVClient tvClient;
    private final ShowRepository showRepository;
    private final RatingRepository ratingRepository;


    public TVMazeService(TVClient tvClient, ShowRepository showRepository, RatingRepository ratingRepository) {
        this.tvClient = tvClient;
        this.showRepository = showRepository;
        this.ratingRepository = ratingRepository;
    }

    public List<ShowResponse> searchShows(String searchQuery) {
        List<ShowResponse> shows = tvClient.searchShows(searchQuery);
        shows.forEach(this::addCommentsToShow);
        return shows;
    }

    private void addCommentsToShow(ShowResponse show) {
        List<Rating> ratings = ratingRepository.findByShowId(show.getId());

        List<RatingInfo> comments = ratings.stream()
                .map(rating -> new RatingInfo(rating.comment(), rating.rating()))
                .toList();

        show.setComments(comments);

    }

    public ShowDetailResponse getShowById(Long showId) {
        ShowDetailResponse show = showRepository.findById(showId)
                .orElseGet(() -> {
                    ShowDetailResponse response = tvClient.getShowById(showId);
                    return showRepository.save(response);
                });

        List<RatingInfo> comments = ratingRepository.findByShowId(showId).stream()
                .map(rating -> new RatingInfo(rating.comment(), rating.rating()))
                .toList();

        return new ShowDetailResponse(
                show.id(),
                show.url(),
                show.name(),
                show.type(),
                show.language(),
                show.genres(),
                show.status(),
                show.runtime(),
                show.averageRuntime(),
                show.premiered(),
                show.ended(),
                show.officialSite(),
                show.schedule(),
                show.rating(),
                show.weight(),
                show.network(),
                show.webChannel(),
                show.dvdCountry(),
                show.externals(),
                show.image(),
                show.summary(),
                show.updated(),
                show.links(),
                comments
        );
    }
}


