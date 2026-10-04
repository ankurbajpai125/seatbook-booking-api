package com.ankur.seatbook.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shows", indexes = @Index(name = "idx_show_movie", columnList = "movieId"))
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long movieId;

    @Column(nullable = false)
    private Long theatreId;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    protected Show() {}

    public Show(Long movieId, Long theatreId, LocalDateTime startTime, BigDecimal price) {
        this.movieId = movieId;
        this.theatreId = theatreId;
        this.startTime = startTime;
        this.price = price;
    }

    public Long getId() { return id; }
    public Long getMovieId() { return movieId; }
    public Long getTheatreId() { return theatreId; }
    public LocalDateTime getStartTime() { return startTime; }
    public BigDecimal getPrice() { return price; }
}
