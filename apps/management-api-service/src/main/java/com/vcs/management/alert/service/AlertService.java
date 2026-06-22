package com.vcs.management.alert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcs.management.alert.dto.AlertResponse;
import com.vcs.management.alert.entity.Alert;
import com.vcs.management.alert.repository.AlertRepository;
import com.vcs.management.audit.service.AuditLogService;
import com.vcs.management.common.enums.AlertSeverity;
import com.vcs.management.common.enums.AlertStatus;
import com.vcs.management.common.enums.AlertType;
import com.vcs.management.common.enums.AuditAction;
import com.vcs.management.common.enums.ResourceType;
import com.vcs.management.common.exception.ResourceNotFoundException;
import com.vcs.management.license.entity.License;
import com.vcs.management.license.repository.LicenseRepository;
import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final String DEFAULT_ACTOR = "system";

    private final AlertRepository alertRepository;
    private final TenantRepository tenantRepository;
    private final LicenseRepository licenseRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AlertService(
            AlertRepository alertRepository,
            TenantRepository tenantRepository,
            LicenseRepository licenseRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.alertRepository = alertRepository;
        this.tenantRepository = tenantRepository;
        this.licenseRepository = licenseRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    // ── Query ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(UUID tenantId, AlertStatus status, AlertType alertType) {
        if (tenantId != null && status != null) {
            return alertRepository.findAllByTenantTenantIdAndStatusOrderByTriggeredAtDesc(tenantId, status)
                    .stream().map(AlertResponse::from).toList();
        }
        if (tenantId != null) {
            return alertRepository.findAllByTenantTenantIdOrderByTriggeredAtDesc(tenantId)
                    .stream().map(AlertResponse::from).toList();
        }
        if (status != null) {
            return alertRepository.findAllByStatusOrderByTriggeredAtDesc(status)
                    .stream().map(AlertResponse::from).toList();
        }
        if (alertType != null) {
            return alertRepository.findAllByAlertTypeOrderByTriggeredAtDesc(alertType)
                    .stream().map(AlertResponse::from).toList();
        }
        return alertRepository.findAllByOrderByTriggeredAtDesc()
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlert(UUID alertId) {
        return AlertResponse.from(findAlert(alertId));
    }

    // ── Resolve / Ignore ────────────────────────────────────────────────

    @Transactional
    public AlertResponse resolveAlert(UUID alertId) {
        Alert alert = findAlert(alertId);
        String beforeValue = toJson(AlertResponse.from(alert));

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now());
        Alert saved = alertRepository.saveAndFlush(alert);
        AlertResponse response = AlertResponse.from(saved);

        auditLogService.writeAuditLog(
                DEFAULT_ACTOR,
                AuditAction.RESOLVE_ALERT,
                ResourceType.ALERT,
                saved.getAlertId(),
                beforeValue,
                toJson(response)
        );

        return response;
    }

    @Transactional
    public AlertResponse ignoreAlert(UUID alertId) {
        Alert alert = findAlert(alertId);
        String beforeValue = toJson(AlertResponse.from(alert));

        alert.setStatus(AlertStatus.IGNORED);
        Alert saved = alertRepository.saveAndFlush(alert);
        AlertResponse response = AlertResponse.from(saved);

        auditLogService.writeAuditLog(
                DEFAULT_ACTOR,
                AuditAction.IGNORE_ALERT,
                ResourceType.ALERT,
                saved.getAlertId(),
                beforeValue,
                toJson(response)
        );

        return response;
    }

    // ── Usage Alert Trigger ─────────────────────────────────────────────

    /**
     * Trigger usage alert when tenant EPS reaches threshold.
     * Deduplicates: only creates alert if no OPEN alert of same type exists.
     *
     * @param tenantId       the tenant UUID
     * @param currentPercent current usage as percentage of quota
     */
    @Transactional
    public void triggerUsageAlert(UUID tenantId, int currentPercent) {
        if (currentPercent >= 100) {
            createUsageAlertIfNotExists(tenantId, AlertType.USAGE_100_PERCENT,
                    AlertSeverity.CRITICAL, 100, currentPercent);
        }
        if (currentPercent >= 70) {
            createUsageAlertIfNotExists(tenantId, AlertType.USAGE_70_PERCENT,
                    AlertSeverity.WARNING, 70, currentPercent);
        }
    }

    /**
     * Auto-resolve usage alerts when usage drops below threshold.
     */
    @Transactional
    public void autoResolveUsageAlerts(UUID tenantId, int currentPercent) {
        if (currentPercent < 70) {
            autoResolveByType(tenantId, AlertType.USAGE_70_PERCENT);
            autoResolveByType(tenantId, AlertType.USAGE_100_PERCENT);
        } else if (currentPercent < 100) {
            autoResolveByType(tenantId, AlertType.USAGE_100_PERCENT);
        }
    }

    // ── License Expiration Alert Trigger ─────────────────────────────────

    /**
     * Trigger license expiring alert if not already OPEN.
     *
     * @param tenantId      the tenant UUID
     * @param licenseId     the license UUID
     * @param daysRemaining days until license expires
     */
    @Transactional
    public void triggerLicenseExpiringAlert(UUID tenantId, UUID licenseId, long daysRemaining) {
        if (alertRepository.existsByTenantTenantIdAndAlertTypeAndStatus(
                tenantId, AlertType.LICENSE_EXPIRING_SOON, AlertStatus.OPEN)) {
            log.debug("License expiring alert already open for tenant {}", tenantId);
            return;
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        License license = licenseRepository.findById(licenseId).orElse(null);

        String message = String.format("License for tenant '%s' expires in %d day(s)",
                tenant.getName(), daysRemaining);

        Alert alert = new Alert(tenant, license, AlertType.LICENSE_EXPIRING_SOON,
                AlertSeverity.WARNING, message, null, null);
        alertRepository.save(alert);

        log.info("Created LICENSE_EXPIRING_SOON alert for tenant {}, {} days remaining",
                tenantId, daysRemaining);
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    private void createUsageAlertIfNotExists(UUID tenantId, AlertType alertType,
                                              AlertSeverity severity, int threshold,
                                              int currentPercent) {
        // Check if open alert of this type already exists
        Optional<Alert> existing = alertRepository.findByTenantTenantIdAndAlertTypeAndStatus(
                tenantId, alertType, AlertStatus.OPEN);

        if (existing.isPresent()) {
            // Update currentPercent on existing alert
            Alert alert = existing.get();
            alert.setCurrentPercent(currentPercent);
            alertRepository.save(alert);
            log.debug("Updated existing {} alert for tenant {}, currentPercent={}",
                    alertType, tenantId, currentPercent);
            return;
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        // Find active license for this tenant
        License license = licenseRepository
                .findAllByTenantTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .filter(l -> l.getStatus() == LicenseStatus.ACTIVE)
                .findFirst()
                .orElse(null);

        String message = String.format("Tenant '%s' EPS usage reached %d%% of quota (threshold: %d%%)",
                tenant.getName(), currentPercent, threshold);

        Alert alert = new Alert(tenant, license, alertType, severity,
                message, threshold, currentPercent);
        alertRepository.save(alert);

        log.info("Created {} alert for tenant {}, currentPercent={}", alertType, tenantId, currentPercent);
    }

    private void autoResolveByType(UUID tenantId, AlertType alertType) {
        alertRepository.findByTenantTenantIdAndAlertTypeAndStatus(tenantId, alertType, AlertStatus.OPEN)
                .ifPresent(alert -> {
                    alert.setStatus(AlertStatus.RESOLVED);
                    alert.setResolvedAt(Instant.now());
                    alertRepository.save(alert);
                    log.info("Auto-resolved {} alert for tenant {}", alertType, tenantId);
                });
    }

    private Alert findAlert(UUID alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found"));
    }

    private String toJson(AlertResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize alert audit snapshot", ex);
        }
    }
}
