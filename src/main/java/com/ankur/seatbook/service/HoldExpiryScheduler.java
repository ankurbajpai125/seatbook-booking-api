package com.ankur.seatbook.service;

import com.ankur.seatbook.domain.Booking;
import com.ankur.seatbook.domain.BookingStatus;
import com.ankur.seatbook.repo.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Releases seats whose payment window has passed. Each booking is expired in its own transaction. */
@Component
public class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);
    private static final int BATCH_SIZE = 200;

    private final BookingRepository bookingRepo;
    private final BookingService bookingService;

    public HoldExpiryScheduler(BookingRepository bookingRepo, BookingService bookingService) {
        this.bookingRepo = bookingRepo;
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${app.expiry-sweep-ms:30000}")
    public void sweep() {
        int totalExpired = 0;
        int expiredInBatch;
        do {
            List<Booking> batch = bookingRepo.findByStatusAndHoldExpiresAtBefore(
                    BookingStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
            expiredInBatch = 0;
            for (Booking b : batch) {
                try {
                    if (bookingService.expireIfStale(b.getId())) expiredInBatch++;
                } catch (RuntimeException e) {
                    log.warn("Could not expire booking {}: {}", b.getId(), e.getMessage());
                }
            }
            totalExpired += expiredInBatch;
            // Another full batch is only worth fetching if this one made progress; otherwise we would loop forever.
        } while (expiredInBatch == BATCH_SIZE);
        if (totalExpired > 0) log.info("Released {} expired seat holds", totalExpired);
    }
}
