package com.ankur.seatbook.service;

import com.ankur.seatbook.domain.Movie;
import com.ankur.seatbook.domain.SeatStatus;
import com.ankur.seatbook.domain.Show;
import com.ankur.seatbook.domain.ShowSeat;
import com.ankur.seatbook.repo.MovieRepository;
import com.ankur.seatbook.repo.ShowRepository;
import com.ankur.seatbook.repo.ShowSeatRepository;
import com.ankur.seatbook.web.Dtos.SeatView;
import com.ankur.seatbook.web.Dtos.ShowView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Read-only browsing: movies, upcoming shows and the seat map. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final MovieRepository movieRepo;
    private final ShowRepository showRepo;
    private final ShowSeatRepository seatRepo;

    public CatalogService(MovieRepository movieRepo, ShowRepository showRepo, ShowSeatRepository seatRepo) {
        this.movieRepo = movieRepo;
        this.showRepo = showRepo;
        this.seatRepo = seatRepo;
    }

    public Page<Movie> movies(Pageable pageable) {
        return movieRepo.findAll(pageable);
    }

    public Movie movie(Long id) {
        return movieRepo.findById(id).orElseThrow(() -> ApiException.notFound("Movie"));
    }

    public List<ShowView> upcomingShows(Long movieId) {
        movie(movieId);
        return showRepo.findByMovieIdAndStartTimeAfterOrderByStartTime(movieId, LocalDateTime.now())
                .stream().map(CatalogService::toView).toList();
    }

    public List<SeatView> seatMap(Long showId) {
        if (!showRepo.existsById(showId)) throw ApiException.notFound("Show");
        LocalDateTime now = LocalDateTime.now();
        return seatRepo.findByShowIdOrderBySeatLabel(showId).stream()
                .sorted(Comparator.comparing((ShowSeat s) -> s.getSeatLabel().charAt(0))
                        .thenComparingInt(s -> Integer.parseInt(s.getSeatLabel().substring(1))))
                .map(s -> new SeatView(s.getSeatLabel(), effectiveStatus(s, now)))
                .toList();
    }

    /** A hold whose time has passed is shown as available even before the sweeper releases it. */
    private static SeatStatus effectiveStatus(ShowSeat s, LocalDateTime now) {
        if (s.getStatus() == SeatStatus.HELD && s.getHoldExpiresAt() != null && s.getHoldExpiresAt().isBefore(now)) {
            return SeatStatus.AVAILABLE;
        }
        return s.getStatus();
    }

    static ShowView toView(Show s) {
        return new ShowView(s.getId(), s.getMovieId(), s.getTheatreId(), s.getStartTime(), s.getPrice());
    }
}
