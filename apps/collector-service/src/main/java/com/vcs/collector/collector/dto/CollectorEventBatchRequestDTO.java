package com.vcs.collector.collector.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO for batch collector API request
 * POST /api/v1/collector/events/batch
 */
public record CollectorEventBatchRequestDTO(
        @NotNull(message = "Tenant ID is required")
        String tenantId,

        @NotEmpty(message = "Events list cannot be empty")
        @Valid
        List<CollectorEventDTO> events
) {
}
