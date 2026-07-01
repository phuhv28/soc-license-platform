package com.vcs.management.alert.entity;

import com.vcs.management.alert.enums.AlertSeverity;
import com.vcs.management.alert.enums.AlertStatus;
import com.vcs.management.alert.enums.AlertType;
import com.vcs.management.license.entity.License;
import com.vcs.management.tenant.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_id")
    private License license;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 100)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "threshold_percent")
    private Integer thresholdPercent;

    @Column(name = "current_percent")
    private Integer currentPercent;

    @Column(name = "trigger_count", nullable = false)
    private Integer triggerCount = 1;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Alert() {
    }

    public Alert(Tenant tenant, License license, AlertType alertType, AlertSeverity severity,
                 String message, Integer thresholdPercent, Integer currentPercent) {
        this.tenant = tenant;
        this.license = license;
        this.alertType = alertType;
        this.severity = severity;
        this.status = AlertStatus.OPEN;
        this.message = message;
        this.thresholdPercent = thresholdPercent;
        this.currentPercent = currentPercent;
        this.triggeredAt = Instant.now();
    }

    public UUID getAlertId() {
        return alertId;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public License getLicense() {
        return license;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Integer getThresholdPercent() {
        return thresholdPercent;
    }

    public Integer getCurrentPercent() {
        return currentPercent;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public void setCurrentPercent(Integer currentPercent) {
        this.currentPercent = currentPercent;
    }

    public Integer getTriggerCount() {
        return triggerCount;
    }

    public void setTriggerCount(Integer triggerCount) {
        this.triggerCount = triggerCount;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
