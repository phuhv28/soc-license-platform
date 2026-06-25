package com.vcs.management.usage.controller;

import com.vcs.management.common.enums.TenantStatus;
import com.vcs.management.common.exception.ResourceNotFoundException;
import com.vcs.management.common.response.ApiResponse;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import com.vcs.management.usage.dto.UsageHistoryResponse;
import com.vcs.management.usage.dto.UsageResponse;
import com.vcs.management.usage.dto.UsageSummaryResponse;
import com.vcs.management.usage.service.UsageApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final UsageApiService usageApiService;
    private final TenantRepository tenantRepository;

    public UsageController(UsageApiService usageApiService, TenantRepository tenantRepository) {
        this.usageApiService = usageApiService;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Get current usage snapshot for a tenant.
     * Includes current EPS, quota, usage percent, and daily totals.
     */
    @GetMapping("/{tenantId}/current")
    public ApiResponse<UsageResponse> getCurrentUsage(@PathVariable UUID tenantId) {
        Tenant tenant = findTenant(tenantId);

        UsageResponse response = new UsageResponse(
                tenant.getTenantId(),
                tenant.getName(),
                usageApiService.getCurrentEps(tenantId),
                usageApiService.getQuota(tenantId),
                usageApiService.getUsagePercent(tenantId),
                usageApiService.getAcceptedToday(tenantId),
                usageApiService.getDroppedToday(tenantId),
                usageApiService.getReceivedToday(tenantId)
        );

        return ApiResponse.success(response);
    }

    /**
     * Get usage history (time series) for charts.
     * Returns one data point per minute for the past N hours.
     *
     * @param tenantId the tenant UUID
     * @param hours    number of hours (default 24, max 48)
     */
    @GetMapping("/{tenantId}/history")
    public ApiResponse<UsageHistoryResponse> getUsageHistory(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "24") int hours
    ) {
        Tenant tenant = findTenant(tenantId);

        List<UsageHistoryResponse.DataPoint> dataPoints =
                usageApiService.getUsageHistory(tenantId, hours);

        UsageHistoryResponse response = new UsageHistoryResponse(
                tenant.getTenantId(),
                tenant.getName(),
                "1m",
                dataPoints
        );

        return ApiResponse.success(response);
    }

    /**
     * Get usage summary for all active tenants (admin dashboard).
     * Returns tenant list with current EPS and usage percent.
     */
    @GetMapping("/summary")
    public ApiResponse<UsageSummaryResponse> getUsageSummary() {
        List<Tenant> activeTenants = tenantRepository.findAll()
                .stream()
                .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
                .toList();

        List<UsageSummaryResponse.TenantUsage> tenantUsages = activeTenants.stream()
                .map(tenant -> new UsageSummaryResponse.TenantUsage(
                        tenant.getTenantId(),
                        tenant.getName(),
                        usageApiService.getCurrentEps(tenant.getTenantId()),
                        usageApiService.getQuota(tenant.getTenantId()),
                        usageApiService.getUsagePercent(tenant.getTenantId())
                ))
                .toList();

        UsageSummaryResponse response = new UsageSummaryResponse(
                activeTenants.size(),
                tenantUsages
        );

        return ApiResponse.success(response);
    }

    /**
     * Get Top-N dimensions (agent, logsource) by EPS.
     */
    @GetMapping("/{tenantId}/dimensions/top")
    public ApiResponse<List<com.vcs.management.usage.dto.UsageDimensionResponse>> getTopDimensions(
            @PathVariable UUID tenantId,
            @RequestParam String dimension,
            @RequestParam(defaultValue = "5m") String window,
            @RequestParam(defaultValue = "10") int limit
    ) {
        // Validate tenant exists
        findTenant(tenantId);

        List<com.vcs.management.usage.dto.UsageDimensionResponse> topDimensions = 
                usageApiService.getTopNDimensions(tenantId, dimension, window, limit);
        
        return ApiResponse.success(topDimensions);
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }
}
