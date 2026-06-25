package com.vcs.collector.usagecounter.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageCounterServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private UsageCounterService usageCounterService;

    @Test
    @DisplayName("should increment all 12 counters (3 types × 4 windows)")
    void shouldIncrementAllCounters() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString(), anyLong())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        usageCounterService.incrementCounters("tenant-a", 10, 8, 2);

        // 3 types × 4 windows = 12 increment calls
        verify(valueOps, times(12)).increment(anyString(), anyLong());
    }

    @Test
    @DisplayName("should skip increment when all values are zero")
    void shouldSkipWhenAllZero() {
        usageCounterService.incrementCounters("tenant-a", 0, 0, 0);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("should only increment non-zero counters")
    void shouldOnlyIncrementNonZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString(), anyLong())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        usageCounterService.incrementCounters("tenant-a", 10, 0, 0);

        // Only received > 0: 1 type × 4 windows = 4 increment calls
        verify(valueOps, times(4)).increment(anyString(), eq(10L));
    }

    @Test
    @DisplayName("should generate correct minute counter key")
    void shouldGenerateMinuteKey() {
        String key = usageCounterService.getMinuteCounterKey("tenant-a", "accepted", "202606230930");
        assertEquals("usage:tenant-a:accepted:1m:202606230930", key);
    }

    @Test
    @DisplayName("should generate correct day counter key")
    void shouldGenerateDayKey() {
        String key = usageCounterService.getDayCounterKey("tenant-a", "dropped", "20260623");
        assertEquals("usage:tenant-a:dropped:1d:20260623", key);
    }

    @Test
    @DisplayName("getCounterValue should return 0 on Redis error")
    void shouldReturn0OnRedisError() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        long result = usageCounterService.getCounterValue("some:key");

        assertEquals(0, result);
    }

    @Test
    @DisplayName("getCounterValue should return parsed value")
    void shouldReturnParsedValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("some:key")).thenReturn("42");

        long result = usageCounterService.getCounterValue("some:key");

        assertEquals(42, result);
    }
}
