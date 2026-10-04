package com.ankur.seatbook.repo;

import com.ankur.seatbook.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {
    List<Show> findByMovieIdAndStartTimeAfterOrderByStartTime(Long movieId, LocalDateTime after);
}
