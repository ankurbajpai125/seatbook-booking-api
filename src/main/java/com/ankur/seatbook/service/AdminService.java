package com.ankur.seatbook.service;

import com.ankur.seatbook.domain.Movie;
import com.ankur.seatbook.domain.Show;
import com.ankur.seatbook.domain.ShowSeat;
import com.ankur.seatbook.domain.Theatre;
import com.ankur.seatbook.repo.MovieRepository;
import com.ankur.seatbook.repo.ShowRepository;
import com.ankur.seatbook.repo.ShowSeatRepository;
import com.ankur.seatbook.repo.TheatreRepository;
import com.ankur.seatbook.web.Dtos.MovieRequest;
import com.ankur.seatbook.web.Dtos.ShowRequest;
import com.ankur.seatbook.web.Dtos.ShowView;
import com.ankur.seatbook.web.Dtos.TheatreRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    private final MovieRepository movieRepo;
    private final TheatreRepository theatreRepo;
    private final ShowRepository showRepo;
    private final ShowSeatRepository seatRepo;

    public AdminService(MovieRepository movieRepo, TheatreRepository theatreRepo,
                        ShowRepository showRepo, ShowSeatRepository seatRepo) {
        this.movieRepo = movieRepo;
        this.theatreRepo = theatreRepo;
        this.showRepo = showRepo;
        this.seatRepo = seatRepo;
    }

    @Transactional
    public Movie createMovie(MovieRequest r) {
        return movieRepo.save(new Movie(r.title(), r.language(), r.durationMinutes(), r.description()));
    }

    @Transactional
    public Theatre createTheatre(TheatreRequest r) {
        return theatreRepo.save(new Theatre(r.name(), r.city(), r.rows(), r.seatsPerRow()));
    }

    /** Creating a show also creates its seat grid (A1..A10, B1..B10, ...). */
    @Transactional
    public ShowView createShow(ShowRequest r) {
        if (!movieRepo.existsById(r.movieId())) throw ApiException.notFound("Movie");
        Theatre theatre = theatreRepo.findById(r.theatreId()).orElseThrow(() -> ApiException.notFound("Theatre"));

        Show show = showRepo.save(new Show(r.movieId(), r.theatreId(), r.startTime(), r.price()));

        List<ShowSeat> seats = new ArrayList<>();
        for (int row = 0; row < theatre.getSeatRows(); row++) {
            char rowLetter = (char) ('A' + row);
            for (int col = 1; col <= theatre.getSeatsPerRow(); col++) {
                seats.add(new ShowSeat(show.getId(), rowLetter + String.valueOf(col)));
            }
        }
        seatRepo.saveAll(seats);
        return CatalogService.toView(show);
    }
}
