package com.vcs.collector.collector.service;

import com.vcs.collector.collector.dto.CollectorBatchResponseDTO;
import com.vcs.collector.collector.dto.CollectorEventBatchRequestDTO;
import com.vcs.collector.collector.dto.CollectorEventDTO;
import com.vcs.collector.common.enums.ProcessingDecision;
import com.vcs.collector.quota.dto.QuotaInfoDTO;
import com.vcs.collector.quota.service.QuotaService;
import com.vcs.collector.quota.service.TokenBucketService;
import com.vcs.collector.usagecounter.service.UsageCounterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectorServiceImplTest {

    @Mock private QuotaService quotaService;
    @Mock private TokenBucketService tokenBucketService;
    @Mock private UsageCounterService usageCounterService;

    @InjectMocks private CollectorServiceImpl collectorService;

    private CollectorEventDTO validEvent(String tenantId) {
        return new CollectorEventDTO(
                "evt-1", tenantId, "firewall.connection",
                Instant.now(), Map.of("key", "value"), Map.of()
        );
    }

    private CollectorEventBatchRequestDTO batch(String tenantId, int count) {
        List<CollectorEventDTO> events = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(new CollectorEventDTO(
                    "evt-" + i, tenantId, "firewall.connection",
                    Instant.now(), Map.of("key", "value"), Map.of()
            ));
        }
        return new CollectorEventBatchRequestDTO(tenantId, events);
    }

    @Nested
    @DisplayName("processBatch - No active license")
    class NoActiveLicenseTests {

        @Test
        @DisplayName("should drop all events when no active license")
        void shouldDropAll() {
            when(quotaService.getQuota("tenant-a")).thenReturn(new QuotaInfoDTO("tenant-a", 0L, false));

            var request = batch("tenant-a", 10);
            CollectorBatchResponseDTO result = collectorService.processBatch(request);

            assertEquals(10, result.received());
            assertEquals(0, result.accepted());
            assertEquals(10, result.dropped());
            assertEquals(ProcessingDecision.NO_ACTIVE_LICENSE, result.decision());
            verify(usageCounterService).incrementCounters("tenant-a", 10, 0, 10);
        }
    }

    @Nested
    @DisplayName("processBatch - All accepted")
    class AllAcceptedTests {

        @Test
        @DisplayName("should accept all events when quota available")
        void shouldAcceptAll() {
            when(quotaService.getQuota("tenant-a")).thenReturn(new QuotaInfoDTO("tenant-a", 100L, true));
            when(tokenBucketService.getAcceptableEventCount("tenant-a", 5)).thenReturn(5L);

            var request = batch("tenant-a", 5);
            CollectorBatchResponseDTO result = collectorService.processBatch(request);

            assertEquals(5, result.received());
            assertEquals(5, result.accepted());
            assertEquals(0, result.dropped());
            assertEquals(ProcessingDecision.ALL_ACCEPTED, result.decision());
            verify(usageCounterService).incrementCounters("tenant-a", 5, 5, 0);
        }
    }

    @Nested
    @DisplayName("processBatch - Over quota")
    class OverQuotaTests {

        @Test
        @DisplayName("should partially accept based on token bucket")
        void shouldPartiallyAccept() {
            when(quotaService.getQuota("tenant-a")).thenReturn(new QuotaInfoDTO("tenant-a", 100L, true));
            when(tokenBucketService.getAcceptableEventCount("tenant-a", 10)).thenReturn(3L);

            var request = batch("tenant-a", 10);
            CollectorBatchResponseDTO result = collectorService.processBatch(request);

            assertEquals(10, result.received());
            assertEquals(3, result.accepted());
            assertEquals(7, result.dropped());
            assertEquals(ProcessingDecision.OVER_QUOTA, result.decision());
        }

        @Test
        @DisplayName("should drop all when zero tokens available")
        void shouldDropAllWhenNoTokens() {
            when(quotaService.getQuota("tenant-a")).thenReturn(new QuotaInfoDTO("tenant-a", 100L, true));
            when(tokenBucketService.getAcceptableEventCount("tenant-a", 5)).thenReturn(0L);

            var request = batch("tenant-a", 5);
            CollectorBatchResponseDTO result = collectorService.processBatch(request);

            assertEquals(5, result.received());
            assertEquals(0, result.accepted());
            assertEquals(5, result.dropped());
            assertEquals(ProcessingDecision.OVER_QUOTA, result.decision());
        }
    }

    @Nested
    @DisplayName("processBatch - Invalid events")
    class InvalidEventTests {

        @Test
        @DisplayName("should drop events with missing required fields")
        void shouldDropInvalidEvents() {
            when(quotaService.getQuota("tenant-a")).thenReturn(new QuotaInfoDTO("tenant-a", 100L, true));
            when(tokenBucketService.getAcceptableEventCount("tenant-a", 3)).thenReturn(3L);

            CollectorEventDTO valid = validEvent("tenant-a");
            // Invalid: null eventId
            CollectorEventDTO invalid1 = new CollectorEventDTO(
                    null, "tenant-a", "test", Instant.now(), Map.of("k", "v"), null);
            // Invalid: empty payload
            CollectorEventDTO invalid2 = new CollectorEventDTO(
                    "evt-2", "tenant-a", "test", Instant.now(), Map.of(), null);

            var request = new CollectorEventBatchRequestDTO("tenant-a", List.of(valid, invalid1, invalid2));
            CollectorBatchResponseDTO result = collectorService.processBatch(request);

            assertEquals(3, result.received());
            assertEquals(1, result.accepted());
            assertEquals(2, result.dropped());
            assertEquals(ProcessingDecision.PARTIAL_VALIDATION, result.decision());
        }

        @Test
        @DisplayName("should return ALL_INVALID when all events fail validation")
        void shouldReturnAllInvalid() {
            when(quotaService.getQuota("tenant-a")).thenReturn(new QuotaInfoDTO("tenant-a", 100L, true));
            when(tokenBucketService.getAcceptableEventCount("tenant-a", 2)).thenReturn(2L);

            CollectorEventDTO inv1 = new CollectorEventDTO(
                    null, "tenant-a", "test", Instant.now(), Map.of("k", "v"), null);
            CollectorEventDTO inv2 = new CollectorEventDTO(
                    "evt", null, "test", Instant.now(), Map.of("k", "v"), null);

            var request = new CollectorEventBatchRequestDTO("tenant-a", List.of(inv1, inv2));
            CollectorBatchResponseDTO result = collectorService.processBatch(request);

            assertEquals(2, result.received());
            assertEquals(0, result.accepted());
            assertEquals(2, result.dropped());
            assertEquals(ProcessingDecision.ALL_INVALID, result.decision());
        }
    }

    @Nested
    @DisplayName("processBatch - Usage counters")
    class UsageCounterTests {

        @Test
        @DisplayName("should always update counters regardless of outcome")
        void shouldAlwaysUpdateCounters() {
            when(quotaService.getQuota("tenant-a")).thenReturn(new QuotaInfoDTO("tenant-a", 100L, true));
            when(tokenBucketService.getAcceptableEventCount("tenant-a", 5)).thenReturn(5L);

            var request = batch("tenant-a", 5);
            collectorService.processBatch(request);

            verify(usageCounterService, times(1)).incrementCounters(eq("tenant-a"), anyLong(), anyLong(), anyLong());
        }
    }
}
