package com.vcs.management.usage.service;

import com.vcs.management.usage.dto.UsageHistoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.vcs.management.usage.repository.BillingMetricRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageApiServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private BillingMetricRepository billingMetricRepository;

    private UsageApiService usageApiService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        usageApiService = new UsageApiService(redisTemplate, billingMetricRepository);
        tenantId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("getQuota")
    class GetQuotaTests {

        @Test
        @DisplayName("should return quota from Redis")
        void shouldReturnQuota() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("quota:" + tenantId)).thenReturn("500");

            long quota = usageApiService.getQuota(tenantId);

            assertEquals(500, quota);
        }

        @Test
        @DisplayName("should return 0 when key not found")
        void shouldReturn0WhenNotFound() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("quota:" + tenantId)).thenReturn(null);

            long quota = usageApiService.getQuota(tenantId);

            assertEquals(0, quota);
        }

        @Test
        @DisplayName("should return 0 on Redis error")
        void shouldReturn0OnError() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            long quota = usageApiService.getQuota(tenantId);

            assertEquals(0, quota);
        }
    }

    @Nested
    @DisplayName("getUsagePercent")
    class GetUsagePercentTests {

        @Test
        @DisplayName("should return 0 when quota is 0")
        void shouldReturn0WhenNoQuota() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("quota:" + tenantId)).thenReturn("0");

            int percent = usageApiService.getUsagePercent(tenantId);

            assertEquals(0, percent);
        }
    }

    @Nested
    @DisplayName("getCounterValue")
    class GetCounterValueTests {

        @Test
        @DisplayName("should parse counter from Redis")
        void shouldParseCounter() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("usage:tid:accepted:1d:20260623")).thenReturn("12345");

            long val = usageApiService.getCounterValue("tid", "accepted", "1d", "20260623");

            assertEquals(12345, val);
        }

        @Test
        @DisplayName("should return 0 for missing key")
        void shouldReturn0ForMissing() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn(null);

            long val = usageApiService.getCounterValue("tid", "accepted", "1d", "20260623");

            assertEquals(0, val);
        }
    }

    @Nested
    @DisplayName("getUsageHistory")
    class GetUsageHistoryTests {

        @Test
        @DisplayName("should return list of data points with correct size")
        void shouldReturnDataPoints() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn("0");

            List<UsageHistoryResponse.DataPoint> result = usageApiService.getUsageHistory(tenantId, "1m", 60);

            assertEquals(60, result.size()); 
        }

        @Test
        @DisplayName("should return at least 1 point for negative limit")
        void shouldClampLimit() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn("0");

            List<UsageHistoryResponse.DataPoint> result = usageApiService.getUsageHistory(tenantId, "1m", -5);

            assertEquals(1, result.size()); 
        }
    }

    @Nested
    @DisplayName("getDailyCounter")
    class GetDailyCounterTests {

        @Test
        @DisplayName("should delegate to getCounterValue with 1d window")
        void shouldDelegateCorrectly() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("usage:" + tenantId + ":received:1d:20260623")).thenReturn("999");

            long val = usageApiService.getDailyCounter(tenantId, "received", "20260623");

            assertEquals(999, val);
        }
    }
}
