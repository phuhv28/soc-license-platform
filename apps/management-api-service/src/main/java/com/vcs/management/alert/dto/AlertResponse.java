package com.vcs.management.alert.dto;

import com.vcs.management.alert.entity.Alert;
import com.vcs.management.common.enums.AlertSeverity;
import com.vcs.management.common.enums.AlertStatus;
import com.vcs.management.common.enums.AlertType;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID alertId,
        UUID tenantId,
        String tenantName,
        UUID licenseId,
        AlertType alertType,
        AlertSeverity severity,
        AlertStatus status,
        String message,
        Integer thresholdPercent,
        Integer currentPercent,
        Integer triggerCount,
        Instant triggeredAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getAlertId(),
                alert.getTenant().getTenantId(),
                alert.getTenant().getName(),
                alert.getLicense() != null ? alert.getLicense().getLicenseId() : null,
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getMessage(),
                alert.getThresholdPercent(),
                alert.getCurrentPercent(),
                alert.getTriggerCount(),
                alert.getTriggeredAt(),
                alert.getResolvedAt(),
                alert.getCreatedAt(),
                alert.getUpdatedAt()
        );
    }
}
