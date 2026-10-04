package com.ankur.seatbook.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_user", columnList = "userId"),
        @Index(name = "idx_booking_status_expiry", columnList = "status,holdExpiresAt")
})
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long showId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(nullable = false)
    private int seatCount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime holdExpiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Booking() {}

    public Booking(Long userId, Long showId, int seatCount, BigDecimal totalAmount, LocalDateTime holdExpiresAt) {
        this.userId = userId;
        this.showId = showId;
        this.seatCount = seatCount;
        this.totalAmount = totalAmount;
        this.holdExpiresAt = holdExpiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getShowId() { return showId; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public int getSeatCount() { return seatCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
