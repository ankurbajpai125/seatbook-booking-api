package com.ankur.seatbook.web;

import com.ankur.seatbook.domain.BookingStatus;
import com.ankur.seatbook.domain.PaymentStatus;
import com.ankur.seatbook.domain.Role;
import com.ankur.seatbook.domain.SeatStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response shapes for the REST API. */
public final class Dtos {

    private Dtos() {}

    // ---- auth ----
    public record RegisterRequest(@NotBlank String name,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, String name, Role role) {}

    // ---- admin ----
    public record MovieRequest(@NotBlank String title,
                               String language,
                               @Min(1) int durationMinutes,
                               @Size(max = 1000) String description) {}

    public record TheatreRequest(@NotBlank String name,
                                 String city,
                                 @Min(1) @Max(26) int rows,
                                 @Min(1) @Max(50) int seatsPerRow) {}

    public record ShowRequest(@NotNull Long movieId,
                              @NotNull Long theatreId,
                              @NotNull @Future LocalDateTime startTime,
                              @NotNull @DecimalMin("0.01") BigDecimal price) {}

    // ---- catalog ----
    public record ShowView(Long id, Long movieId, Long theatreId, LocalDateTime startTime, BigDecimal price) {}

    public record SeatView(String label, SeatStatus status) {}

    // ---- booking ----
    public record HoldRequest(@NotEmpty List<@NotBlank String> seatLabels) {}

    /** Body is optional; simulateFailure lets you demo the failed-payment path. */
    public record PayRequest(Boolean simulateFailure) {}

    public record BookingView(Long id,
                              Long showId,
                              BookingStatus status,
                              List<String> seatLabels,
                              BigDecimal totalAmount,
                              LocalDateTime holdExpiresAt,
                              LocalDateTime createdAt) {}

    public record PaymentView(Long paymentId, PaymentStatus status, BookingView booking) {}
}
