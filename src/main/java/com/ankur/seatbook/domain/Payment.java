package com.ankur.seatbook.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_idem", columnNames = "idempotencyKey"))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Payment() {}

    public Payment(Long bookingId, String idempotencyKey, PaymentStatus status, BigDecimal amount) {
        this.bookingId = bookingId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
}
