package com.vcs.collector.collector.service;

import com.vcs.collector.collector.dto.CollectorBatchResponseDTO;
import com.vcs.collector.collector.dto.CollectorEventBatchRequestDTO;
import com.vcs.collector.collector.dto.CollectorEventDTO;
import com.vcs.collector.common.enums.ProcessingDecision;
import com.vcs.collector.quota.dto.QuotaInfoDTO;
import com.vcs.collector.quota.service.QuotaService;
import com.vcs.collector.quota.service.TokenBucketService;
import com.vcs.collector.usagecounter.service.UsageCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of CollectorService
 * Enforces Data Plane rules:
 * - Rule 1: No quota → drop all
 * - Rule 2: Token bucket decides accepted/dropped
 * - Rule 3: Only accepted events go downstream
 * - Rule 4: Always update usage counters
 */
@Service
@RequiredArgsConstructor
public class CollectorServiceImpl implements CollectorService {

    private final QuotaService quotaService;
    private final TokenBucketService tokenBucketService;
    private final UsageCounterService usageCounterService;

    /**
     * Process a batch of events with full Data Plane enforcement
     * 
     * @param request the batch request containing events
     * @return the batch processing result with statistics and decision
     */
    @Override
    public CollectorBatchResponseDTO processBatch(CollectorEventBatchRequestDTO request) {
        String tenantId = request.tenantId();
        List<CollectorEventDTO> events = request.events();
        long received = events.size();

        // Rule 1: Check if tenant has active quota
        QuotaInfoDTO quota = quotaService.getQuota(tenantId);
        if (!quota.active()) {
            // No active license - drop all events
            CollectorBatchResponseDTO response = new CollectorBatchResponseDTO(
                    tenantId,
                    received,
                    0,        // accepted
                    received, // dropped - all events dropped
                    Instant.now(),
                    ProcessingDecision.NO_ACTIVE_LICENSE
            );
            
            // Rule 4: Update counters even when all dropped
            usageCounterService.incrementCounters(tenantId, received, 0, received);
            
            return response;
        }

        // Rule 2: Use token bucket to determine how many events can be accepted
        long acceptableCount = tokenBucketService.getAcceptableEventCount(tenantId, received);

        long accepted = 0;
        long dropped = 0;
        List<CollectorEventDTO> acceptedEvents = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            CollectorEventDTO event = events.get(i);
            
            // Accept if within token limit and passes validation
            if (i < acceptableCount && validateEvent(event)) {
                acceptedEvents.add(event);
                accepted++;
            } else {
                dropped++;
            }
        }

        // Rule 3: Send accepted events to downstream
        if (!acceptedEvents.isEmpty()) {
            sendToDownstream(acceptedEvents);
        }

        // Rule 4: Always update counters
        usageCounterService.incrementCounters(tenantId, received, accepted, dropped);

        // Determine decision
        ProcessingDecision decision = determineDecision(received, accepted, dropped);

        return new CollectorBatchResponseDTO(
                tenantId,
                received,
                accepted,
                dropped,
                Instant.now(),
                decision
        );
    }

    /**
     * Validate individual event
     *
     * @param event the event to validate
     * @return true if event is valid, false otherwise
     */
    private boolean validateEvent(CollectorEventDTO event) {
        try {
            if (event.eventId() == null || event.eventId().isBlank()) {
                return false;
            }
            if (event.tenantId() == null || event.tenantId().isBlank()) {
                return false;
            }
            if (event.eventType() == null || event.eventType().isBlank()) {
                return false;
            }
            if (event.timestamp() == null) {
                return false;
            }
            if (event.payload() == null || event.payload().isEmpty()) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Send accepted events to downstream processors
     * Placeholder for future integration with downstream module
     *
     * @param events the accepted events to forward
     */
    private void sendToDownstream(List<CollectorEventDTO> events) {
        // TODO: Forward events to downstream processors
        // - Store in database
        // - Send to message queue
        // - Call alert service if thresholds reached
    }

    /**
     * Determine processing decision based on batch results
     *
     * @param received total events received
     * @param accepted events accepted
     * @param dropped events dropped
     * @return processing decision
     */
    private ProcessingDecision determineDecision(long received, long accepted, long dropped) {
        if (accepted == received) {
            return ProcessingDecision.ALL_ACCEPTED;
        } else if (accepted == 0) {
            return ProcessingDecision.ALL_INVALID;
        } else {
            return ProcessingDecision.PARTIAL_VALIDATION;
        }
    }
}
