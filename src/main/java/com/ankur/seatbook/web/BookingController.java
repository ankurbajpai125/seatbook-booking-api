package com.ankur.seatbook.web;

import com.ankur.seatbook.config.AuthUser;
import com.ankur.seatbook.service.ApiException;
import com.ankur.seatbook.service.BookingService;
import com.ankur.seatbook.web.Dtos.BookingView;
import com.ankur.seatbook.web.Dtos.HoldRequest;
import com.ankur.seatbook.web.Dtos.PayRequest;
import com.ankur.seatbook.web.Dtos.PaymentView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    /** Step 1: hold seats for a few minutes. */
    @PostMapping("/shows/{showId}/hold")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView hold(@AuthenticationPrincipal AuthUser user,
                            @PathVariable Long showId,
                            @Valid @RequestBody HoldRequest request) {
        return bookings.hold(user.id(), showId, request.seatLabels());
    }

    /** Step 2: pay. Send a fresh Idempotency-Key per payment attempt; reuse it only when retrying that attempt. */
    @PostMapping("/bookings/{id}/pay")
    public PaymentView pay(@AuthenticationPrincipal AuthUser user,
                           @PathVariable Long id,
                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                           @RequestBody(required = false) PayRequest request) {
        String key = idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 100) {
            throw ApiException.badRequest("Idempotency-Key must be 1 to 100 characters");
        }
        boolean fail = request != null && Boolean.TRUE.equals(request.simulateFailure());
        return bookings.pay(user.id(), id, key, fail);
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingView cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return bookings.cancel(user.id(), id);
    }

    @GetMapping("/bookings/mine")
    public List<BookingView> mine(@AuthenticationPrincipal AuthUser user) {
        return bookings.myBookings(user.id());
    }

    @GetMapping("/bookings/{id}")
    public BookingView get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return bookings.get(user.id(), id);
    }
}
