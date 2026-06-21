package com.vcs.collector.collector.dto;

import java.time.Instant;

/**
 * DTO for batch processing response data
 */
public record CollectorBatchResponseDTO(
        String tenantId,
        long received,
        long accepted,
        long dropped,
        Instant processedAt
) {
}
