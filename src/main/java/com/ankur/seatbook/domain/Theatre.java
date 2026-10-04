package com.ankur.seatbook.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "theatres")
public class Theatre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String city;

    @Column(nullable = false)
    private int seatRows;

    @Column(nullable = false)
    private int seatsPerRow;

    protected Theatre() {}

    public Theatre(String name, String city, int seatRows, int seatsPerRow) {
        this.name = name;
        this.city = city;
        this.seatRows = seatRows;
        this.seatsPerRow = seatsPerRow;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public int getSeatRows() { return seatRows; }
    public int getSeatsPerRow() { return seatsPerRow; }
}
