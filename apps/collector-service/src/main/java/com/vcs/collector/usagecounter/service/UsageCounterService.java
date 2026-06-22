package com.vcs.collector.usagecounter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Service for tracking event usage metrics with time-windowed counters
 * Maintains 1-minute and 1-day granularity counters for dashboard/reporting
 */
@Service
@RequiredArgsConstructor
public class UsageCounterService {

    private final RedisTemplate<String, String> redisTemplate;

    // TTL constants
    private static final long COUNTER_1M_TTL_SECONDS = 48 * 60 * 60; // 48 hours
    private static final long COUNTER_1D_TTL_SECONDS = 90 * 24 * 60 * 60; // 90 days

    // Timestamp formatters
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Increment usage counters for both 1-minute and 1-day windows
     * Non-blocking operation
     *
     * @param tenantId tenant ID
     * @param received number of events received
     * @param accepted number of events accepted
     * @param dropped number of events dropped
     */
    public void incrementCounters(String tenantId, long received, long accepted, long dropped) {
        if (received <= 0 && accepted <= 0 && dropped <= 0) {
            return;
        }

        try {
            // Get current timestamps for time windows
            long nowMs = System.currentTimeMillis();
            long nowUnixSeconds = nowMs / 1000;
            
            String minuteWindow = getMinuteWindow(nowUnixSeconds);
            String dayWindow = getDayWindow(nowUnixSeconds);

            // Update 1-minute counters
            incrementMinuteCounters(tenantId, minuteWindow, received, accepted, dropped);

            // Update 1-day counters
            incrementDayCounters(tenantId, dayWindow, received, accepted, dropped);

        } catch (Exception e) {
            // Non-blocking: silently skip on Redis error
            // Dashboard will show partial data rather than failing event acceptance
        }
    }

    /**
     * Increment 1-minute window counters with 48-hour TTL
     */
    private void incrementMinuteCounters(String tenantId, String minuteWindow,
                                        long received, long accepted, long dropped) {
        String receivedKey = String.format("usage:%s:received:1m:%s", tenantId, minuteWindow);
        String acceptedKey = String.format("usage:%s:accepted:1m:%s", tenantId, minuteWindow);
        String droppedKey = String.format("usage:%s:dropped:1m:%s", tenantId, minuteWindow);

        if (received > 0) {
            redisTemplate.opsForValue().increment(receivedKey, received);
            redisTemplate.expire(receivedKey, Duration.ofSeconds(COUNTER_1M_TTL_SECONDS));
        }

        if (accepted > 0) {
            redisTemplate.opsForValue().increment(acceptedKey, accepted);
            redisTemplate.expire(acceptedKey, Duration.ofSeconds(COUNTER_1M_TTL_SECONDS));
        }

        if (dropped > 0) {
            redisTemplate.opsForValue().increment(droppedKey, dropped);
            redisTemplate.expire(droppedKey, Duration.ofSeconds(COUNTER_1M_TTL_SECONDS));
        }
    }

    /**
     * Increment 1-day window counters with 90-day TTL
     */
    private void incrementDayCounters(String tenantId, String dayWindow,
                                     long received, long accepted, long dropped) {
        String receivedKey = String.format("usage:%s:received:1d:%s", tenantId, dayWindow);
        String acceptedKey = String.format("usage:%s:accepted:1d:%s", tenantId, dayWindow);
        String droppedKey = String.format("usage:%s:dropped:1d:%s", tenantId, dayWindow);

        if (received > 0) {
            redisTemplate.opsForValue().increment(receivedKey, received);
            redisTemplate.expire(receivedKey, Duration.ofSeconds(COUNTER_1D_TTL_SECONDS));
        }

        if (accepted > 0) {
            redisTemplate.opsForValue().increment(acceptedKey, accepted);
            redisTemplate.expire(acceptedKey, Duration.ofSeconds(COUNTER_1D_TTL_SECONDS));
        }

        if (dropped > 0) {
            redisTemplate.opsForValue().increment(droppedKey, dropped);
            redisTemplate.expire(droppedKey, Duration.ofSeconds(COUNTER_1D_TTL_SECONDS));
        }
    }

    /**
     * Get 1-minute window identifier from Unix seconds
     * Format: yyyyMMddHHmm
     */
    private String getMinuteWindow(long unixSeconds) {
        return java.time.Instant.ofEpochSecond(unixSeconds)
                .atZone(ZoneId.systemDefault())
                .format(MINUTE_FORMATTER);
    }

    /**
     * Get 1-day window identifier from Unix seconds
     * Format: yyyyMMdd
     */
    private String getDayWindow(long unixSeconds) {
        return java.time.Instant.ofEpochSecond(unixSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DAY_FORMATTER);
    }

    /**
     * Get counter value for a specific key
     * Used by Phase 4 dashboard
     *
     * @param key Redis counter key
     * @return counter value or 0 if not found
     */
    public long getCounterValue(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Construct 1-minute counter key
     */
    public String getMinuteCounterKey(String tenantId, String type, String minuteWindow) {
        return String.format("usage:%s:%s:1m:%s", tenantId, type, minuteWindow);
    }

    /**
     * Construct 1-day counter key
     */
    public String getDayCounterKey(String tenantId, String type, String dayWindow) {
        return String.format("usage:%s:%s:1d:%s", tenantId, type, dayWindow);
    }
}
