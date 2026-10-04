package com.ankur.seatbook;

import com.ankur.seatbook.domain.*;
import com.ankur.seatbook.repo.BookingRepository;
import com.ankur.seatbook.repo.PaymentRepository;
import com.ankur.seatbook.repo.ShowSeatRepository;
import com.ankur.seatbook.service.AdminService;
import com.ankur.seatbook.service.ApiException;
import com.ankur.seatbook.service.BookingService;
import com.ankur.seatbook.service.HoldExpiryScheduler;
import com.ankur.seatbook.web.Dtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the booking rules under real concurrency. Subclasses run the same scenarios with and
 * without the seat-hold store, showing the database layer alone is enough to prevent double booking.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractConcurrentBookingTest {

    @Autowired BookingService bookingService;
    @Autowired AdminService adminService;
    @Autowired ShowSeatRepository seatRepo;
    @Autowired BookingRepository bookingRepo;
    @Autowired PaymentRepository paymentRepo;
    @Autowired HoldExpiryScheduler scheduler;
    @Autowired JdbcTemplate jdbc;

    Long showId;

    @BeforeEach
    void createShow() {
        Movie movie = adminService.createMovie(new MovieRequest("Test Movie", "en", 120, "demo"));
        Theatre theatre = adminService.createTheatre(new TheatreRequest("Test Hall", "Noida", 3, 5));
        showId = adminService.createShow(new ShowRequest(
                movie.getId(), theatre.getId(), LocalDateTime.now().plusDays(1), new BigDecimal("250.00"))).id();
    }

    @Test
    void hundredUsersFightForOneSeat_exactlyOneWins() throws Exception {
        List<Throwable> results = race(100, i -> bookingService.hold(i + 1L, showId, List.of("A1")));

        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertOnlyConflicts(results);
        assertThat(seat("A1").getStatus()).isEqualTo(SeatStatus.HELD);
        // Losers' booking rows were rolled back, so exactly one booking exists for this show.
        assertThat(bookingsForShow()).hasSize(1);
    }

    @Test
    void overlappingSeatSets_neverSplitASeatBetweenTwoBookings() throws Exception {
        // Even users want B1+B2, odd users want B2+B3. B2 is shared, so only one request can win.
        List<Throwable> results = race(60, i ->
                bookingService.hold(i + 1L, showId, i % 2 == 0 ? List.of("B1", "B2") : List.of("B2", "B3")));

        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertOnlyConflicts(results);
        List<ShowSeat> held = seatRepo.findByShowIdOrderBySeatLabel(showId).stream()
                .filter(s -> s.getStatus() == SeatStatus.HELD).toList();
        assertThat(held).hasSize(2);
        assertThat(held.stream().map(ShowSeat::getBookingId).distinct()).hasSize(1);
    }

    @Test
    void retriedPaymentWithSameKey_chargesOnce() throws Exception {
        BookingView booking = bookingService.hold(7L, showId, List.of("C1", "C2"));
        Queue<PaymentView> payments = new ConcurrentLinkedQueue<>();

        List<Throwable> results = race(20, i -> payments.add(bookingService.pay(7L, booking.id(), "key-1", false)));

        assertThat(results).allMatch(Objects::isNull);
        assertThat(payments).hasSize(20).allMatch(p -> p.status() == PaymentStatus.SUCCESS);
        assertThat(payments.stream().map(PaymentView::paymentId).distinct()).hasSize(1);
        assertThat(paymentRepo.findAll().stream().filter(p -> p.getBookingId().equals(booking.id()))).hasSize(1);
        assertThat(seat("C1").getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat("C2").getStatus()).isEqualTo(SeatStatus.BOOKED);

        // A second, different key on an already paid booking must not charge again.
        assertThatThrownBy(() -> bookingService.pay(7L, booking.id(), "key-2", false))
                .isInstanceOf(ApiException.class).hasMessageContaining("already paid");
    }

    @Test
    void failedPaymentKeepsHold_andCanBeRetriedWithNewKey() {
        BookingView booking = bookingService.hold(8L, showId, List.of("A5"));

        PaymentView failed = bookingService.pay(8L, booking.id(), "attempt-1", true);
        assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.booking().status()).isEqualTo(BookingStatus.PENDING);

        // Replaying the same key returns the same failed result instead of trying again.
        assertThat(bookingService.pay(8L, booking.id(), "attempt-1", false).paymentId()).isEqualTo(failed.paymentId());

        PaymentView ok = bookingService.pay(8L, booking.id(), "attempt-2", false);
        assertThat(ok.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(ok.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void expiredHold_cannotBePaid_andSeatsAreReleasedForOthers() {
        BookingView booking = bookingService.hold(9L, showId, List.of("A2"));
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        jdbc.update("update bookings set hold_expires_at = ? where id = ?", past, booking.id());
        jdbc.update("update show_seats set hold_expires_at = ? where booking_id = ?", past, booking.id());

        assertThatThrownBy(() -> bookingService.pay(9L, booking.id(), "late", false))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.GONE));

        scheduler.sweep();

        assertThat(seat("A2").getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(bookingService.get(9L, booking.id()).status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(bookingService.hold(10L, showId, List.of("A2")).status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void cancelledBooking_freesSeats_andOtherUsersCannotTouchIt() {
        BookingView booking = bookingService.hold(11L, showId, List.of("A3"));
        bookingService.pay(11L, booking.id(), "pay-1", false);

        assertThatThrownBy(() -> bookingService.cancel(12L, booking.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> bookingService.hold(12L, showId, List.of("A3")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        bookingService.cancel(11L, booking.id());
        assertThat(seat("A3").getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(bookingService.hold(12L, showId, List.of("A3")).status()).isEqualTo(BookingStatus.PENDING);
    }

    // ---- helpers ----

    private ShowSeat seat(String label) {
        return seatRepo.findByShowIdOrderBySeatLabel(showId).stream()
                .filter(s -> s.getSeatLabel().equals(label)).findFirst().orElseThrow();
    }

    private List<Booking> bookingsForShow() {
        return bookingRepo.findAll().stream().filter(b -> b.getShowId().equals(showId)).toList();
    }

    private static void assertOnlyConflicts(List<Throwable> results) {
        assertThat(results.stream().filter(Objects::nonNull))
                .allSatisfy(t -> {
                    assertThat(t).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) t).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    /** Starts all threads at the same instant and returns each one's error (null means success). */
    static List<Throwable> race(int threads, IntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    action.accept(n);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Throwable> results = new ArrayList<>();
        for (Future<Throwable> f : futures) results.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        return results;
    }
}
