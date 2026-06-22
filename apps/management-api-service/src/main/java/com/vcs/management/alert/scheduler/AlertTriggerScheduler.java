package com.vcs.management.alert.scheduler;

import com.vcs.management.alert.service.AlertService;
import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.common.enums.TenantStatus;
import com.vcs.management.license.repository.LicenseRepository;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import com.vcs.management.usage.service.UsageApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler that periodically checks EPS usage for all active tenants
 * and triggers alerts when usage exceeds 70% or 100% of quota.
 * Also auto-resolves alerts when usage drops below thresholds.
 *
 * Runs every 60 seconds.
 */
@Component
public class AlertTriggerScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertTriggerScheduler.class);

    private final TenantRepository tenantRepository;
    private final LicenseRepository licenseRepository;
    private final UsageApiService usageApiService;
    private final AlertService alertService;

    public AlertTriggerScheduler(
            TenantRepository tenantRepository,
            LicenseRepository licenseRepository,
            UsageApiService usageApiService,
            AlertService alertService
    ) {
        this.tenantRepository = tenantRepository;
        this.licenseRepository = licenseRepository;
        this.usageApiService = usageApiService;
        this.alertService = alertService;
    }

    @Scheduled(fixedRate = 60_000)
    public void checkUsageAlerts() {
        log.debug("Running usage alert check...");

        List<Tenant> activeTenants = tenantRepository.findAll()
                .stream()
                .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
                .toList();

        for (Tenant tenant : activeTenants) {
            try {
                checkTenantUsage(tenant);
            } catch (Exception e) {
                log.error("Error checking usage for tenant {}: {}",
                        tenant.getTenantId(), e.getMessage(), e);
            }
        }

        log.debug("Usage alert check completed for {} tenants", activeTenants.size());
    }

    private void checkTenantUsage(Tenant tenant) {
        // Check if tenant has an active license
        boolean hasActiveLicense = licenseRepository.existsByTenantTenantIdAndStatus(
                tenant.getTenantId(), LicenseStatus.ACTIVE);

        if (!hasActiveLicense) {
            return;
        }

        int usagePercent = usageApiService.getUsagePercent(tenant.getTenantId());

        if (usagePercent >= 70) {
            alertService.triggerUsageAlert(tenant.getTenantId(), usagePercent);
        } else {
            // Auto-resolve if usage dropped below threshold
            alertService.autoResolveUsageAlerts(tenant.getTenantId(), usagePercent);
        }
    }
}
