package com.vcs.management.usage.service;

import com.vcs.management.usage.dto.UsageHistoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service to read EPS usage counters from Redis.
 * Counters are written by collector-service using key format:
 *   usage:{tenantId}:{type}:1m:{yyyyMMddHHmm}
 *   usage:{tenantId}:{type}:1d:{yyyyMMdd}
 * Where type is: received, accepted, dropped
 */
@Service
public class UsageApiService {

    private static final Logger log = LoggerFactory.getLogger(UsageApiService.class);
    private static final String QUOTA_KEY_FORMAT = "quota:%s";
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final StringRedisTemplate redisTemplate;

    public UsageApiService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Current EPS ─────────────────────────────────────────────────────

    /**
     * Get the current EPS for a tenant by reading the most recent 1-minute counter.
     * Tries the current minute first, then falls back to the previous minute.
     *
     * @param tenantId the tenant UUID
     * @return current accepted EPS (events in last minute / 60)
     */
    public long getCurrentEps(UUID tenantId) {
        long now = System.currentTimeMillis() / 1000;

        // Try current minute
        String currentWindow = getMinuteWindow(now);
        long received = getCounterValue(tenantId.toString(), "received", "1m", currentWindow);

        if (received == 0) {
            // Fall back to previous minute (counter may not have accumulated yet)
            String prevWindow = getMinuteWindow(now - 60);
            received = getCounterValue(tenantId.toString(), "received", "1m", prevWindow);
        }

        // Convert events-per-minute to events-per-second
        return received / 60;
    }

    /**
     * Get the current raw event count for the latest 1-minute window.
     * Returns the raw counter value (not divided by 60).
     *
     * @param tenantId the tenant UUID
     * @return raw accepted count in the most recent minute window
     */
    public long getCurrentMinuteAccepted(UUID tenantId) {
        long now = System.currentTimeMillis() / 1000;

        String currentWindow = getMinuteWindow(now);
        long accepted = getCounterValue(tenantId.toString(), "accepted", "1m", currentWindow);

        if (accepted == 0) {
            String prevWindow = getMinuteWindow(now - 60);
            accepted = getCounterValue(tenantId.toString(), "accepted", "1m", prevWindow);
        }

        return accepted;
    }

    // ── Quota ───────────────────────────────────────────────────────────

    /**
     * Get the EPS quota for a tenant from Redis.
     *
     * @param tenantId the tenant UUID
     * @return quota EPS value, or 0 if not found
     */
    public long getQuota(UUID tenantId) {
        try {
            String key = String.format(QUOTA_KEY_FORMAT, tenantId);
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value.trim()) : 0;
        } catch (Exception e) {
            log.warn("Failed to read quota for tenant {}: {}", tenantId, e.getMessage());
            return 0;
        }
    }

    public int getUsagePercent(UUID tenantId) {
        long quota = getQuota(tenantId);
        if (quota <= 0) {
            return 0;
        }

        long currentReceivedEps = getCurrentReceivedEps(tenantId);
        return (int) ((currentReceivedEps * 100) / quota);
    }

    /**
     * Get the current received EPS by reading the most recent 1-minute counter.
     */
    public long getCurrentReceivedEps(UUID tenantId) {
        long now = System.currentTimeMillis() / 1000;
        String currentWindow = getMinuteWindow(now);
        long received = getCounterValue(tenantId.toString(), "received", "1m", currentWindow);

        if (received == 0) {
            String prevWindow = getMinuteWindow(now - 60);
            received = getCounterValue(tenantId.toString(), "received", "1m", prevWindow);
        }
        return received / 60;
    }

    // ── Daily Totals ────────────────────────────────────────────────────

    /**
     * Get the accepted event count for today.
     */
    public long getAcceptedToday(UUID tenantId) {
        String dayWindow = getDayWindow(System.currentTimeMillis() / 1000);
        return getCounterValue(tenantId.toString(), "accepted", "1d", dayWindow);
    }

    /**
     * Get the dropped event count for today.
     */
    public long getDroppedToday(UUID tenantId) {
        String dayWindow = getDayWindow(System.currentTimeMillis() / 1000);
        return getCounterValue(tenantId.toString(), "dropped", "1d", dayWindow);
    }

    /**
     * Get the received event count for today.
     */
    public long getReceivedToday(UUID tenantId) {
        String dayWindow = getDayWindow(System.currentTimeMillis() / 1000);
        return getCounterValue(tenantId.toString(), "received", "1d", dayWindow);
    }

    // ── History (Time Series) ───────────────────────────────────────────

    /**
     * Get usage history as a list of data points, one per minute.
     * Reads 1-minute counters for the past N hours.
     *
     * @param tenantId the tenant UUID
     * @param hours    number of hours of history to retrieve (max 48)
     * @return list of data points with timestamp, received, accepted, dropped
     */
    public List<UsageHistoryResponse.DataPoint> getUsageHistory(UUID tenantId, String window, int limit) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        List<UsageHistoryResponse.DataPoint> dataPoints = new ArrayList<>();
        String tid = tenantId.toString();

        int stepSeconds;
        if ("5m".equals(window)) stepSeconds = 300;
        else if ("15m".equals(window)) stepSeconds = 900;
        else if ("1d".equals(window)) stepSeconds = 86400;
        else {
            window = "1m";
            stepSeconds = 60;
        }

        // Ensure we always return at least 1 point
        if (limit <= 0) limit = 1;

        for (int i = limit - 1; i >= 0; i--) {
            long targetSeconds = nowSeconds - (i * stepSeconds);
            String timeKey;
            String displayTimestamp;

            if ("1d".equals(window)) {
                timeKey = getDayWindow(targetSeconds);
                displayTimestamp = Instant.ofEpochSecond(targetSeconds)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else if ("5m".equals(window) || "15m".equals(window)) {
                timeKey = getFloorMinuteWindow(targetSeconds, stepSeconds / 60);
                displayTimestamp = getDisplayMinuteWindow(getFlooredSeconds(targetSeconds, stepSeconds / 60));
            } else {
                timeKey = getMinuteWindow(targetSeconds);
                displayTimestamp = getDisplayMinuteWindow(targetSeconds);
            }

            long received = getCounterValue(tid, "received", window, timeKey);
            long accepted = getCounterValue(tid, "accepted", window, timeKey);
            long dropped = getCounterValue(tid, "dropped", window, timeKey);

            dataPoints.add(new UsageHistoryResponse.DataPoint(
                    displayTimestamp, received, accepted, dropped));
        }

        return dataPoints;
    }

    // ── Daily Counter for Reports ───────────────────────────────────────

    /**
     * Get counter value for a specific day.
     * Used by ReportService for monthly CSV export.
     *
     * @param tenantId the tenant UUID
     * @param type     counter type: received, accepted, dropped
     * @param dayKey   day window key in format yyyyMMdd
     * @return counter value
     */
    public long getDailyCounter(UUID tenantId, String type, String dayKey) {
        return getCounterValue(tenantId.toString(), type, "1d", dayKey);
    }

    // ── Generic Counter Read ────────────────────────────────────────────

    /**
     * Get a specific counter value from Redis.
     */
    public long getCounterValue(String tenantId, String type, String window, String timeKey) {
        try {
            String key = String.format("usage:%s:%s:%s:%s", tenantId, type, window, timeKey);
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            log.warn("Failed to read counter {}:{}:{}:{}: {}", tenantId, type, window, timeKey, e.getMessage());
            return 0;
        }
    }

    // ── Top-N Dimensions ────────────────────────────────────────────────

    /**
     * Get Top N elements for a specific dimension and time window.
     */
    public List<com.vcs.management.usage.dto.UsageDimensionResponse> getTopNDimensions(UUID tenantId, String dimension, String window, int limit) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        String timeKey;
        int windowSeconds;

        if ("5m".equals(window)) {
            timeKey = getFloorMinuteWindow(nowSeconds, 5);
            windowSeconds = 300;
        } else if ("15m".equals(window)) {
            timeKey = getFloorMinuteWindow(nowSeconds, 15);
            windowSeconds = 900;
        } else {
            // default to 1m
            timeKey = getMinuteWindow(nowSeconds);
            windowSeconds = 60;
        }

        String receivedKey = String.format("top:%s:%s:received:%s:%s", tenantId, dimension, window, timeKey);
        String acceptedKey = String.format("top:%s:%s:accepted:%s:%s", tenantId, dimension, window, timeKey);
        String droppedKey = String.format("top:%s:%s:dropped:%s:%s", tenantId, dimension, window, timeKey);

        java.util.Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples = 
                redisTemplate.opsForZSet().reverseRangeWithScores(receivedKey, 0, limit - 1);

        if (tuples == null || tuples.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<com.vcs.management.usage.dto.UsageDimensionResponse> results = new ArrayList<>();
        
        // Execute pipelined query to fetch accepted and dropped scores efficiently
        List<Object> pipelineResults = redisTemplate.executePipelined((org.springframework.data.redis.connection.RedisConnection connection) -> {
            org.springframework.data.redis.serializer.RedisSerializer<String> serializer = 
                    (org.springframework.data.redis.serializer.RedisSerializer<String>) redisTemplate.getKeySerializer();
            byte[] aKey = serializer.serialize(acceptedKey);
            byte[] dKey = serializer.serialize(droppedKey);
            
            for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> tuple : tuples) {
                byte[] itemBytes = serializer.serialize(tuple.getValue());
                connection.zSetCommands().zScore(aKey, itemBytes);
                connection.zSetCommands().zScore(dKey, itemBytes);
            }
            return null;
        });

        int i = 0;
        for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> tuple : tuples) {
            String name = tuple.getValue();
            long receivedCount = tuple.getScore() != null ? tuple.getScore().longValue() : 0;
            
            Double aScore = (Double) pipelineResults.get(i * 2);
            Double dScore = (Double) pipelineResults.get(i * 2 + 1);
            long acceptedCount = aScore != null ? aScore.longValue() : 0;
            long droppedCount = dScore != null ? dScore.longValue() : 0;
            
            double rEps = Math.round(((double) receivedCount / windowSeconds) * 100.0) / 100.0;
            double aEps = Math.round(((double) acceptedCount / windowSeconds) * 100.0) / 100.0;
            double dEps = Math.round(((double) droppedCount / windowSeconds) * 100.0) / 100.0;
            
            results.add(new com.vcs.management.usage.dto.UsageDimensionResponse(name, receivedCount, acceptedCount, droppedCount, rEps, aEps, dEps));
            i++;
        }

        return results;
    }

    // ── Time Window Helpers ─────────────────────────────────────────────

    private String getMinuteWindow(long unixSeconds) {
        return Instant.ofEpochSecond(unixSeconds)
                .atZone(ZoneId.systemDefault())
                .format(MINUTE_FORMATTER);
    }

    private String getDisplayMinuteWindow(long unixSeconds) {
        return Instant.ofEpochSecond(unixSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DISPLAY_MINUTE_FORMATTER);
    }

    private String getFloorMinuteWindow(long unixSeconds, int minuteInterval) {
        java.time.ZonedDateTime zdt = Instant.ofEpochSecond(unixSeconds)
                .atZone(ZoneId.systemDefault());
        int minute = zdt.getMinute();
        int flooredMinute = (minute / minuteInterval) * minuteInterval;
        return zdt.withMinute(flooredMinute).withSecond(0).withNano(0)
                .format(MINUTE_FORMATTER);
    }

    private long getFlooredSeconds(long unixSeconds, int minuteInterval) {
        java.time.ZonedDateTime zdt = Instant.ofEpochSecond(unixSeconds)
                .atZone(ZoneId.systemDefault());
        int minute = zdt.getMinute();
        int flooredMinute = (minute / minuteInterval) * minuteInterval;
        return zdt.withMinute(flooredMinute).withSecond(0).withNano(0).toEpochSecond();
    }

    public String getDayWindow(long unixSeconds) {
        return Instant.ofEpochSecond(unixSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DAY_FORMATTER);
    }
}

