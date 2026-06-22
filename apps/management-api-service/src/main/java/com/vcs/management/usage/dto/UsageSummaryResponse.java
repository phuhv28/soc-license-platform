package com.vcs.management.usage.dto;

import java.util.List;
import java.util.UUID;

/**
 * Summary of all tenants' usage for admin dashboard.
 */
public record UsageSummaryResponse(
        int totalTenants,
        List<TenantUsage> tenants
) {

    /**
     * Usage info for a single tenant in the summary list.
     */
    public record TenantUsage(
            UUID tenantId,
            String tenantName,
            long currentEps,
            long quota,
            int usagePercent
    ) {
    }
}
