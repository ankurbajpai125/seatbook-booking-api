package com.ankur.seatbook.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One row per (show, seat). The unique constraint plus the conditional UPDATE in
 * ShowSeatRepository is what makes double booking impossible, even without Redis.
 */
@Entity
@Table(name = "show_seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_show_seat", columnNames = {"showId", "seatLabel"}),
        indexes = @Index(name = "idx_seat_booking", columnList = "bookingId"))
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long showId;

    @Column(nullable = false, length = 8)
    private String seatLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status = SeatStatus.AVAILABLE;

    private Long heldBy;

    private LocalDateTime holdExpiresAt;

    private Long bookingId;

    @Version
    private Long version;

    protected ShowSeat() {}

    public ShowSeat(Long showId, String seatLabel) {
        this.showId = showId;
        this.seatLabel = seatLabel;
    }

    public Long getId() { return id; }
    public Long getShowId() { return showId; }
    public String getSeatLabel() { return seatLabel; }
    public SeatStatus getStatus() { return status; }
    public Long getHeldBy() { return heldBy; }
    public LocalDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public Long getBookingId() { return bookingId; }
}
