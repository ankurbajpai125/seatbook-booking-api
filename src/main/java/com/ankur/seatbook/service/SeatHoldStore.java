package com.ankur.seatbook.service;

import java.time.Duration;
import java.util.Collection;

/**
 * Fast, TTL-based first line of defence for seat holds. The database remains the source of truth,
 * so implementations may fail open if their backing store is unavailable.
 */
public interface SeatHoldStore {

    /** Atomically claims all seats or none. Returns false if any seat is already claimed. */
    boolean tryHold(Long showId, Collection<String> seatLabels, Long userId, Duration ttl);

    void release(Long showId, Collection<String> seatLabels);
}
