package com.ankur.seatbook.repo;

import com.ankur.seatbook.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
