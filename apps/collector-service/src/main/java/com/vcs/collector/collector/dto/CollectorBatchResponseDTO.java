package com.vcs.collector.collector.dto;

import com.vcs.collector.common.enums.ProcessingDecision;
import java.time.Instant;

/**
 * DTO for batch processing response data
 */
public record CollectorBatchResponseDTO(
        String tenantId,
        long received,
        long accepted,
        long dropped,
        Instant processedAt,
        ProcessingDecision decision
) {
}
