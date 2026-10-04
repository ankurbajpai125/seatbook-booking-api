package com.ankur.seatbook.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** In-memory stand-in used by tests so they run without Docker/Redis. */
@Component
@Profile("test")
public class InMemorySeatHoldStore implements SeatHoldStore {

    private final Map<String, Long> expiryByKey = new HashMap<>();

    private static String key(Long showId, String label) {
        return showId + ":" + label;
    }

    @Override
    public synchronized boolean tryHold(Long showId, Collection<String> seatLabels, Long userId, Duration ttl) {
        long now = System.currentTimeMillis();
        for (String label : seatLabels) {
            Long expiry = expiryByKey.get(key(showId, label));
            if (expiry != null && expiry > now) return false;
        }
        long newExpiry = now + ttl.toMillis();
        seatLabels.forEach(l -> expiryByKey.put(key(showId, l), newExpiry));
        return true;
    }

    @Override
    public synchronized void release(Long showId, Collection<String> seatLabels) {
        seatLabels.forEach(l -> expiryByKey.remove(key(showId, l)));
    }
}
