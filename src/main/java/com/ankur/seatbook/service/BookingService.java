package com.ankur.seatbook.service;

import com.ankur.seatbook.domain.*;
import com.ankur.seatbook.repo.*;
import com.ankur.seatbook.web.Dtos.BookingView;
import com.ankur.seatbook.web.Dtos.PaymentView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final ShowRepository showRepo;
    private final ShowSeatRepository seatRepo;
    private final BookingRepository bookingRepo;
    private final PaymentRepository paymentRepo;
    private final SeatHoldStore holdStore;
    private final Duration holdDuration;
    private final int maxSeatsPerBooking;

    public BookingService(ShowRepository showRepo,
                          ShowSeatRepository seatRepo,
                          BookingRepository bookingRepo,
                          PaymentRepository paymentRepo,
                          SeatHoldStore holdStore,
                          @Value("${app.hold.minutes:5}") long holdMinutes,
                          @Value("${app.hold.max-seats-per-booking:6}") int maxSeatsPerBooking) {
        this.showRepo = showRepo;
        this.seatRepo = seatRepo;
        this.bookingRepo = bookingRepo;
        this.paymentRepo = paymentRepo;
        this.holdStore = holdStore;
        this.holdDuration = Duration.ofMinutes(holdMinutes);
        this.maxSeatsPerBooking = maxSeatsPerBooking;
    }

    /**
     * Step 1: lock the chosen seats for a few minutes. Redis gives a fast rejection for losers;
     * the conditional UPDATE in the database is what guarantees nobody else can hold the same seat.
     */
    @Transactional
    public BookingView hold(Long userId, Long showId, List<String> requestedSeats) {
        List<String> labels = requestedSeats.stream()
                .map(s -> s.trim().toUpperCase())
                .distinct()
                .sorted()                       // consistent lock order across concurrent requests
                .toList();
        if (labels.isEmpty() || labels.size() > maxSeatsPerBooking) {
            throw ApiException.badRequest("Select between 1 and " + maxSeatsPerBooking + " seats");
        }

        Show show = showRepo.findById(showId).orElseThrow(() -> ApiException.notFound("Show"));
        LocalDateTime now = LocalDateTime.now();
        if (!show.getStartTime().isAfter(now)) {
            throw ApiException.conflict("This show has already started");
        }
        if (seatRepo.countByShowIdAndSeatLabelIn(showId, labels) != labels.size()) {
            throw ApiException.badRequest("One or more seats do not exist for this show");
        }

        if (!holdStore.tryHold(showId, labels, userId, holdDuration)) {
            throw ApiException.conflict("Some of these seats are currently held by another user");
        }
        try {
            LocalDateTime expiresAt = now.plus(holdDuration);
            BigDecimal total = show.getPrice().multiply(BigDecimal.valueOf(labels.size()));
            Booking booking = bookingRepo.saveAndFlush(new Booking(userId, showId, labels.size(), total, expiresAt));

            int held = seatRepo.holdSeats(showId, labels, userId, booking.getId(), expiresAt, now,
                    SeatStatus.HELD, SeatStatus.AVAILABLE);
            if (held != labels.size()) {
                // At least one seat was taken. Throwing rolls back the booking row and any partial update.
                throw ApiException.conflict("Some of these seats are no longer available");
            }
            return toView(booking, labels);
        } catch (RuntimeException e) {
            holdStore.release(showId, labels);
            throw e;
        }
    }

    /**
     * Step 2: pay for a held booking. Safe to retry: the same Idempotency-Key always returns the
     * original result, and the row lock on the booking serializes concurrent duplicates.
     */
    @Transactional
    public PaymentView pay(Long userId, Long bookingId, String idempotencyKey, boolean simulateFailure) {
        Booking booking = lockOwnedBooking(userId, bookingId);

        Optional<Payment> previous = paymentRepo.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            Payment p = previous.get();
            if (!p.getBookingId().equals(bookingId)) {
                throw ApiException.conflict("Idempotency key was already used for a different booking");
            }
            return new PaymentView(p.getId(), p.getStatus(), toView(booking, labelsOf(bookingId)));
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw ApiException.conflict("Booking is already paid");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw ApiException.conflict("Booking is " + booking.getStatus().name().toLowerCase());
        }
        LocalDateTime now = LocalDateTime.now();
        if (booking.getHoldExpiresAt().isBefore(now)) {
            throw new ApiException(HttpStatus.GONE, "Seat hold expired, please select seats again");
        }

        if (simulateFailure) {
            Payment failed = paymentRepo.save(new Payment(bookingId, idempotencyKey, PaymentStatus.FAILED, booking.getTotalAmount()));
            return new PaymentView(failed.getId(), PaymentStatus.FAILED, toView(booking, labelsOf(bookingId)));
        }

        // Status change is flushed before the bulk UPDATE runs; a mismatch below rolls everything back.
        booking.setStatus(BookingStatus.CONFIRMED);
        int confirmed = seatRepo.confirmSeats(bookingId, userId, now, SeatStatus.HELD, SeatStatus.BOOKED);
        if (confirmed != booking.getSeatCount()) {
            throw ApiException.conflict("Your seats are no longer held, please select seats again");
        }
        Payment paid = paymentRepo.save(new Payment(bookingId, idempotencyKey, PaymentStatus.SUCCESS, booking.getTotalAmount()));
        return new PaymentView(paid.getId(), PaymentStatus.SUCCESS, toView(booking, labelsOf(bookingId)));
    }

    @Transactional
    public BookingView cancel(Long userId, Long bookingId) {
        Booking booking = lockOwnedBooking(userId, bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
            throw ApiException.conflict("Booking is already " + booking.getStatus().name().toLowerCase());
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            Show show = showRepo.findById(booking.getShowId()).orElseThrow(() -> ApiException.notFound("Show"));
            if (!show.getStartTime().isAfter(LocalDateTime.now())) {
                throw ApiException.conflict("Cannot cancel after the show has started");
            }
        }
        List<String> labels = labelsOf(bookingId);
        booking.setStatus(BookingStatus.CANCELLED);
        seatRepo.releaseSeats(bookingId, List.of(SeatStatus.HELD, SeatStatus.BOOKED), SeatStatus.AVAILABLE);
        holdStore.release(booking.getShowId(), labels);
        return toView(booking, labels);
    }

    /** Called by the scheduler. Returns true if this call expired the booking. */
    @Transactional
    public boolean expireIfStale(Long bookingId) {
        Booking booking = bookingRepo.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING
                || !booking.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        // Only seats still tied to this booking are returned (a newer hold may have taken some over).
        List<String> labels = labelsOf(bookingId);
        booking.setStatus(BookingStatus.EXPIRED);
        seatRepo.releaseSeats(bookingId, List.of(SeatStatus.HELD), SeatStatus.AVAILABLE);
        holdStore.release(booking.getShowId(), labels);
        return true;
    }

    @Transactional(readOnly = true)
    public BookingView get(Long userId, Long bookingId) {
        Booking booking = bookingRepo.findById(bookingId)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Booking"));
        return toView(booking, labelsOf(bookingId));
    }

    @Transactional(readOnly = true)
    public List<BookingView> myBookings(Long userId) {
        List<Booking> bookings = bookingRepo.findByUserIdOrderByCreatedAtDesc(userId);
        if (bookings.isEmpty()) return List.of();
        Map<Long, List<String>> labelsByBooking = seatRepo
                .findByBookingIdIn(bookings.stream().map(Booking::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ShowSeat::getBookingId,
                        Collectors.mapping(ShowSeat::getSeatLabel, Collectors.toList())));
        return bookings.stream()
                .map(b -> toView(b, labelsByBooking.getOrDefault(b.getId(), List.of()).stream().sorted().toList()))
                .toList();
    }

    // ---- helpers ----

    private Booking lockOwnedBooking(Long userId, Long bookingId) {
        // Same 404 for "missing" and "someone else's" so booking ids can't be probed.
        return bookingRepo.findByIdForUpdate(bookingId)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Booking"));
    }

    private List<String> labelsOf(Long bookingId) {
        return seatRepo.findByBookingId(bookingId).stream().map(ShowSeat::getSeatLabel).sorted().toList();
    }

    private BookingView toView(Booking b, List<String> labels) {
        return new BookingView(b.getId(), b.getShowId(), b.getStatus(), labels,
                b.getTotalAmount(), b.getHoldExpiresAt(), b.getCreatedAt());
    }
}
