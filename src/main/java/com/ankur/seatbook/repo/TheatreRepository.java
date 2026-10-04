package com.ankur.seatbook.repo;

import com.ankur.seatbook.domain.Theatre;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheatreRepository extends JpaRepository<Theatre, Long> {
}
