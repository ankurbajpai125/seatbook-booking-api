package com.ankur.seatbook.repo;

import com.ankur.seatbook.domain.SeatStatus;
import com.ankur.seatbook.domain.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowIdOrderBySeatLabel(Long showId);

    List<ShowSeat> findByBookingId(Long bookingId);

    List<ShowSeat> findByBookingIdIn(Collection<Long> bookingIds);

    long countByShowIdAndSeatLabelIn(Long showId, Collection<String> labels);

    /**
     * The core of double-booking prevention. One atomic UPDATE flips the requested seats to HELD
     * only if they are free (or their previous hold has expired). The DB row lock serializes
     * concurrent callers, so for any seat exactly one UPDATE can match. The caller compares the
     * returned row count with the number of seats it asked for and rolls back on a mismatch.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ShowSeat s
               set s.status = :held, s.heldBy = :userId, s.holdExpiresAt = :expiresAt,
                   s.bookingId = :bookingId, s.version = s.version + 1
             where s.showId = :showId
               and s.seatLabel in :labels
               and (s.status = :available or (s.status = :held and s.holdExpiresAt < :now))
            """)
    int holdSeats(@Param("showId") Long showId,
                  @Param("labels") Collection<String> labels,
                  @Param("userId") Long userId,
                  @Param("bookingId") Long bookingId,
                  @Param("expiresAt") LocalDateTime expiresAt,
                  @Param("now") LocalDateTime now,
                  @Param("held") SeatStatus held,
                  @Param("available") SeatStatus available);

    /** Turns this booking's still-valid holds into BOOKED. Count must equal the booking's seat count. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ShowSeat s
               set s.status = :booked, s.holdExpiresAt = null, s.version = s.version + 1
             where s.bookingId = :bookingId
               and s.heldBy = :userId
               and s.status = :held
               and s.holdExpiresAt >= :now
            """)
    int confirmSeats(@Param("bookingId") Long bookingId,
                     @Param("userId") Long userId,
                     @Param("now") LocalDateTime now,
                     @Param("held") SeatStatus held,
                     @Param("booked") SeatStatus booked);

    /** Frees seats that belong to this booking (expiry or cancellation). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ShowSeat s
               set s.status = :available, s.heldBy = null, s.holdExpiresAt = null,
                   s.bookingId = null, s.version = s.version + 1
             where s.bookingId = :bookingId
               and s.status in :statuses
            """)
    int releaseSeats(@Param("bookingId") Long bookingId,
                     @Param("statuses") Collection<SeatStatus> statuses,
                     @Param("available") SeatStatus available);
}
