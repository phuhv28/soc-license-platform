package com.vcs.collector.usagecounter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    public static class DimensionCount {
        public long received = 0;
        public long accepted = 0;
        public long dropped = 0;
        
        public void addReceived(long n) { received += n; }
        public void addAccepted(long n) { accepted += n; }
        public void addDropped(long n) { dropped += n; }
    }

    private final RedisTemplate<String, String> redisTemplate;

    // TTL constants
    private static final long COUNTER_1M_TTL_SECONDS = 48 * 60 * 60; // 48 hours
    private static final long COUNTER_5M_TTL_SECONDS = 7 * 24 * 60 * 60; // 7 days
    private static final long COUNTER_15M_TTL_SECONDS = 14 * 24 * 60 * 60; // 14 days
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
            String minute5Window = getFloorMinuteWindow(nowUnixSeconds, 5);
            String minute15Window = getFloorMinuteWindow(nowUnixSeconds, 15);
            String dayWindow = getDayWindow(nowUnixSeconds);

            // Update 1-minute counters
            incrementWindowCounters(tenantId, "1m", minuteWindow, received, accepted, dropped, COUNTER_1M_TTL_SECONDS);

            // Update 5-minute counters
            incrementWindowCounters(tenantId, "5m", minute5Window, received, accepted, dropped, COUNTER_5M_TTL_SECONDS);

            // Update 15-minute counters
            incrementWindowCounters(tenantId, "15m", minute15Window, received, accepted, dropped, COUNTER_15M_TTL_SECONDS);

            // Update 1-day counters
            incrementWindowCounters(tenantId, "1d", dayWindow, received, accepted, dropped, COUNTER_1D_TTL_SECONDS);

        } catch (Exception e) {
            // Non-blocking: silently skip on Redis error
            // Dashboard will show partial data rather than failing event acceptance
        }
    }

    /**
     * Increment dimensions (agent, log source) for Top-N metrics using sorted sets
     */
    public void incrementDimensions(String tenantId, java.util.Map<String, DimensionCount> agentCounts, java.util.Map<String, DimensionCount> logSourceCounts) {
        if (agentCounts.isEmpty() && logSourceCounts.isEmpty()) {
            return;
        }

        try {
            long nowUnixSeconds = System.currentTimeMillis() / 1000;
            String window1m = getMinuteWindow(nowUnixSeconds);
            String window5m = getFloorMinuteWindow(nowUnixSeconds, 5);
            String window15m = getFloorMinuteWindow(nowUnixSeconds, 15);

            redisTemplate.executePipelined((org.springframework.data.redis.connection.RedisConnection connection) -> {
                org.springframework.data.redis.serializer.RedisSerializer<String> serializer = 
                        (org.springframework.data.redis.serializer.RedisSerializer<String>) redisTemplate.getKeySerializer();

                java.util.function.BiConsumer<String, java.util.Map<String, DimensionCount>> addZsets = (dimension, counts) -> {
                    counts.forEach((item, dc) -> {
                        byte[] itemBytes = serializer.serialize(item);
                        
                        // 1m keys
                        byte[] rKey1m = serializer.serialize(String.format("top:%s:%s:received:1m:%s", tenantId, dimension, window1m));
                        byte[] aKey1m = serializer.serialize(String.format("top:%s:%s:accepted:1m:%s", tenantId, dimension, window1m));
                        byte[] dKey1m = serializer.serialize(String.format("top:%s:%s:dropped:1m:%s", tenantId, dimension, window1m));

                        // 5m keys
                        byte[] rKey5m = serializer.serialize(String.format("top:%s:%s:received:5m:%s", tenantId, dimension, window5m));
                        byte[] aKey5m = serializer.serialize(String.format("top:%s:%s:accepted:5m:%s", tenantId, dimension, window5m));
                        byte[] dKey5m = serializer.serialize(String.format("top:%s:%s:dropped:5m:%s", tenantId, dimension, window5m));

                        // 15m keys
                        byte[] rKey15m = serializer.serialize(String.format("top:%s:%s:received:15m:%s", tenantId, dimension, window15m));
                        byte[] aKey15m = serializer.serialize(String.format("top:%s:%s:accepted:15m:%s", tenantId, dimension, window15m));
                        byte[] dKey15m = serializer.serialize(String.format("top:%s:%s:dropped:15m:%s", tenantId, dimension, window15m));

                        // Update Received
                        if (dc.received > 0) {
                            connection.zSetCommands().zIncrBy(rKey1m, dc.received, itemBytes);
                            connection.keyCommands().expire(rKey1m, COUNTER_1M_TTL_SECONDS);
                            connection.zSetCommands().zIncrBy(rKey5m, dc.received, itemBytes);
                            connection.keyCommands().expire(rKey5m, COUNTER_5M_TTL_SECONDS);
                            connection.zSetCommands().zIncrBy(rKey15m, dc.received, itemBytes);
                            connection.keyCommands().expire(rKey15m, COUNTER_15M_TTL_SECONDS);
                        }

                        // Update Accepted
                        if (dc.accepted > 0) {
                            connection.zSetCommands().zIncrBy(aKey1m, dc.accepted, itemBytes);
                            connection.keyCommands().expire(aKey1m, COUNTER_1M_TTL_SECONDS);
                            connection.zSetCommands().zIncrBy(aKey5m, dc.accepted, itemBytes);
                            connection.keyCommands().expire(aKey5m, COUNTER_5M_TTL_SECONDS);
                            connection.zSetCommands().zIncrBy(aKey15m, dc.accepted, itemBytes);
                            connection.keyCommands().expire(aKey15m, COUNTER_15M_TTL_SECONDS);
                        }

                        // Update Dropped
                        if (dc.dropped > 0) {
                            connection.zSetCommands().zIncrBy(dKey1m, dc.dropped, itemBytes);
                            connection.keyCommands().expire(dKey1m, COUNTER_1M_TTL_SECONDS);
                            connection.zSetCommands().zIncrBy(dKey5m, dc.dropped, itemBytes);
                            connection.keyCommands().expire(dKey5m, COUNTER_5M_TTL_SECONDS);
                            connection.zSetCommands().zIncrBy(dKey15m, dc.dropped, itemBytes);
                            connection.keyCommands().expire(dKey15m, COUNTER_15M_TTL_SECONDS);
                        }
                    });
                };

                addZsets.accept("agent", agentCounts);
                addZsets.accept("logsource", logSourceCounts);

                return null;
            });
        } catch (Exception e) {
            // Non-blocking
        }
    }

    /**
     * Generic window counter incrementer
     */
    private void incrementWindowCounters(String tenantId, String window, String windowKey,
                                         long received, long accepted, long dropped, long ttlSeconds) {
        String receivedKey = String.format("usage:%s:received:%s:%s", tenantId, window, windowKey);
        String acceptedKey = String.format("usage:%s:accepted:%s:%s", tenantId, window, windowKey);
        String droppedKey = String.format("usage:%s:dropped:%s:%s", tenantId, window, windowKey);

        if (received > 0) {
            redisTemplate.opsForValue().increment(receivedKey, received);
            redisTemplate.expire(receivedKey, Duration.ofSeconds(ttlSeconds));
        }

        if (accepted > 0) {
            redisTemplate.opsForValue().increment(acceptedKey, accepted);
            redisTemplate.expire(acceptedKey, Duration.ofSeconds(ttlSeconds));
        }

        if (dropped > 0) {
            redisTemplate.opsForValue().increment(droppedKey, dropped);
            redisTemplate.expire(droppedKey, Duration.ofSeconds(ttlSeconds));
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
     * Get floored minute window identifier for 5m, 15m etc.
     * Format: yyyyMMddHHmm
     */
    private String getFloorMinuteWindow(long unixSeconds, int minuteInterval) {
        java.time.ZonedDateTime zdt = java.time.Instant.ofEpochSecond(unixSeconds)
                .atZone(ZoneId.systemDefault());
        int minute = zdt.getMinute();
        int flooredMinute = (minute / minuteInterval) * minuteInterval;
        return zdt.withMinute(flooredMinute).withSecond(0).withNano(0)
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
