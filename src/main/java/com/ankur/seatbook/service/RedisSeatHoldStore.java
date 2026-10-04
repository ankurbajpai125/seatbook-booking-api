package com.ankur.seatbook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Seat holds as Redis keys (SET NX + TTL). Expiry is automatic: no cleanup job needed for Redis. */
@Component
@Profile("!test")
public class RedisSeatHoldStore implements SeatHoldStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSeatHoldStore.class);

    private final StringRedisTemplate redis;

    public RedisSeatHoldStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(Long showId, String label) {
        return "hold:" + showId + ":" + label;
    }

    @Override
    public boolean tryHold(Long showId, Collection<String> seatLabels, Long userId, Duration ttl) {
        List<String> acquired = new ArrayList<>();
        try {
            for (String label : seatLabels) {
                Boolean ok = redis.opsForValue().setIfAbsent(key(showId, label), String.valueOf(userId), ttl);
                if (!Boolean.TRUE.equals(ok)) {
                    release(showId, acquired);
                    return false;
                }
                acquired.add(label);
            }
            return true;
        } catch (RuntimeException e) {
            // Redis down: fail open. The conditional UPDATE in the database still prevents double booking.
            log.warn("Redis unavailable for seat hold, falling back to database only: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void release(Long showId, Collection<String> seatLabels) {
        if (seatLabels.isEmpty()) return;
        try {
            redis.delete(seatLabels.stream().map(l -> key(showId, l)).toList());
        } catch (RuntimeException e) {
            log.warn("Could not release Redis seat holds (they will expire by TTL): {}", e.getMessage());
        }
    }
}
