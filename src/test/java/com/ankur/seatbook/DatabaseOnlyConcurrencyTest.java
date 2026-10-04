package com.ankur.seatbook;

import com.ankur.seatbook.service.SeatHoldStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Collection;

/**
 * Same scenarios, but the hold store lets everyone through. Passing here shows the database
 * (conditional UPDATE + unique constraints) is what actually prevents double booking, and Redis
 * is only a fast first filter.
 */
@Import(DatabaseOnlyConcurrencyTest.AllowEverything.class)
class DatabaseOnlyConcurrencyTest extends AbstractConcurrentBookingTest {

    @TestConfiguration
    static class AllowEverything {
        @Bean
        @Primary
        SeatHoldStore passThroughStore() {
            return new SeatHoldStore() {
                @Override
                public boolean tryHold(Long showId, Collection<String> seatLabels, Long userId, Duration ttl) {
                    return true;
                }

                @Override
                public void release(Long showId, Collection<String> seatLabels) {
                }
            };
        }
    }
}
