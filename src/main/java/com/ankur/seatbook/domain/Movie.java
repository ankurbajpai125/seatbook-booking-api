package com.ankur.seatbook.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String language;

    private int durationMinutes;

    @Column(length = 1000)
    private String description;

    protected Movie() {}

    public Movie(String title, String language, int durationMinutes, String description) {
        this.title = title;
        this.language = language;
        this.durationMinutes = durationMinutes;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getLanguage() { return language; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getDescription() { return description; }
}
