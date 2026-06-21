package com.vcs.collector.collector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * DTO representing a single event in the batch
 */
public record CollectorEventDTO(
        @NotBlank(message = "Event ID is required")
        String eventId,

        @NotBlank(message = "Tenant ID is required")
        String tenantId,

        @NotBlank(message = "Event type is required")
        String eventType,

        @NotNull(message = "Event timestamp is required")
        Instant timestamp,

        @NotNull(message = "Event payload is required")
        Map<String, Object> payload,

        Map<String, String> metadata
) {
}
