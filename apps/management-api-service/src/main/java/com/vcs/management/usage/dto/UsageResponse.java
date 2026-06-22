package com.vcs.management.usage.dto;

import java.util.UUID;

/**
 * Current usage snapshot for a single tenant.
 */
public record UsageResponse(
        UUID tenantId,
        String tenantName,
        long currentEps,
        long quota,
        int usagePercent,
        long acceptedToday,
        long droppedToday,
        long receivedToday
) {
}
