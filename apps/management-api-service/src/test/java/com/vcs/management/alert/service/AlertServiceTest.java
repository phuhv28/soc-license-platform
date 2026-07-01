package com.vcs.management.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vcs.management.alert.dto.AlertResponse;
import com.vcs.management.alert.entity.Alert;
import com.vcs.management.alert.repository.AlertRepository;
import com.vcs.management.audit.service.AuditLogService;
import com.vcs.management.common.enums.*;
import com.vcs.management.alert.enums.*;
import com.vcs.management.common.exception.ResourceNotFoundException;
import com.vcs.management.license.repository.LicenseRepository;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private LicenseRepository licenseRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;

    private AlertService alertService;
    private Tenant tenant;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        alertService = new AlertService(alertRepository, tenantRepository, licenseRepository, auditLogService, mapper, notificationService);

        tenant = new Tenant("Test Tenant");
        try {
            var f = Tenant.class.getDeclaredField("tenantId");
            f.setAccessible(true);
            tenantId = UUID.randomUUID();
            f.set(tenant, tenantId);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Nested
    @DisplayName("triggerUsageAlert")
    class TriggerUsageAlertTests {

        @Test
        @DisplayName("should create USAGE_70_PERCENT alert when usage >= 70")
        void shouldCreate70PercentAlert() {
            when(alertRepository.findByTenantTenantIdAndAlertTypeAndStatus(
                    tenantId, AlertType.USAGE_70_PERCENT, AlertStatus.OPEN))
                    .thenReturn(Optional.empty());
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(licenseRepository.findAllByTenantTenantIdOrderByCreatedAtDesc(tenantId))
                    .thenReturn(List.of());

            alertService.triggerUsageAlert(tenantId, 75);

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository, atLeastOnce()).save(captor.capture());

            boolean has70 = captor.getAllValues().stream()
                    .anyMatch(a -> a.getAlertType() == AlertType.USAGE_70_PERCENT);
            assertTrue(has70, "Should have created USAGE_70_PERCENT alert");
        }

        @Test
        @DisplayName("should only create 100% alert when usage >= 100")
        void shouldCreateOnly100PercentAlert() {
            when(alertRepository.findByTenantTenantIdAndAlertTypeAndStatus(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(licenseRepository.findAllByTenantTenantIdOrderByCreatedAtDesc(tenantId))
                    .thenReturn(List.of());

            alertService.triggerUsageAlert(tenantId, 105);

            // With the else-if logic, it should only create 1 alert (the 100% one)
            verify(alertRepository, times(1)).save(any(Alert.class));
        }

        @Test
        @DisplayName("should not create duplicate alert if OPEN alert exists, but should increment triggerCount")
        void shouldNotDuplicateButUpdate() {
            Alert existingAlert = new Alert(tenant, null, AlertType.USAGE_70_PERCENT,
                    AlertSeverity.WARNING, "test", 70, 75);
            when(alertRepository.findByTenantTenantIdAndAlertTypeAndStatus(
                    tenantId, AlertType.USAGE_70_PERCENT, AlertStatus.OPEN))
                    .thenReturn(Optional.of(existingAlert));

            alertService.triggerUsageAlert(tenantId, 80);

            // Should update existing, not create new
            verify(alertRepository, times(1)).save(existingAlert);
            verify(tenantRepository, never()).findById(any());
            
            assertEquals(80, existingAlert.getCurrentPercent());
            assertEquals(2, existingAlert.getTriggerCount());
            assertTrue(existingAlert.getMessage().contains("Triggered 2 times"));
        }
    }


    @Nested
    @DisplayName("resolveAlert")
    class ResolveAlertTests {

        @Test
        @DisplayName("should resolve alert and write audit log")
        void shouldResolve() {
            Alert alert = new Alert(tenant, null, AlertType.USAGE_70_PERCENT,
                    AlertSeverity.WARNING, "test", 70, 75);
            UUID alertId = UUID.randomUUID();
            try {
                var f = Alert.class.getDeclaredField("alertId");
                f.setAccessible(true);
                f.set(alert, alertId);
            } catch (Exception e) { throw new RuntimeException(e); }

            when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
            when(alertRepository.saveAndFlush(any())).thenReturn(alert);

            AlertResponse response = alertService.resolveAlert(alertId);

            assertEquals(AlertStatus.RESOLVED, alert.getStatus());
            assertNotNull(alert.getResolvedAt());
            verify(auditLogService).writeAuditLog(eq("system"), eq(AuditAction.RESOLVE_ALERT),
                    any(), eq(alertId), any(), any());
        }

        @Test
        @DisplayName("should throw if alert not found")
        void shouldThrowIfNotFound() {
            UUID id = UUID.randomUUID();
            when(alertRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> alertService.resolveAlert(id));
        }
    }

    @Nested
    @DisplayName("triggerLicenseExpiringAlert")
    class LicenseExpiringTests {

        @Test
        @DisplayName("should create alert when no open alert exists")
        void shouldCreateAlert() {
            UUID licenseId = UUID.randomUUID();
            when(alertRepository.existsByTenantTenantIdAndAlertTypeAndStatus(
                    tenantId, AlertType.LICENSE_EXPIRING_SOON, AlertStatus.OPEN))
                    .thenReturn(false);
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(licenseRepository.findById(licenseId)).thenReturn(Optional.empty());

            alertService.triggerLicenseExpiringAlert(tenantId, licenseId, 5);

            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(captor.capture());
            assertEquals(AlertType.LICENSE_EXPIRING_SOON, captor.getValue().getAlertType());
            assertTrue(captor.getValue().getMessage().contains("5 day(s)"));
        }

        @Test
        @DisplayName("should skip if open alert already exists")
        void shouldSkipIfExists() {
            when(alertRepository.existsByTenantTenantIdAndAlertTypeAndStatus(
                    tenantId, AlertType.LICENSE_EXPIRING_SOON, AlertStatus.OPEN))
                    .thenReturn(true);

            alertService.triggerLicenseExpiringAlert(tenantId, UUID.randomUUID(), 5);

            verify(alertRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAlerts")
    class GetAlertsTests {

        @Test
        @DisplayName("should return all alerts when no filter")
        void shouldReturnAll() {
            when(alertRepository.findAllByOrderByTriggeredAtDesc()).thenReturn(List.of());

            List<AlertResponse> result = alertService.getAlerts(null, null, null);

            assertNotNull(result);
            verify(alertRepository).findAllByOrderByTriggeredAtDesc();
        }

        @Test
        @DisplayName("should filter by tenantId and status")
        void shouldFilterByTenantAndStatus() {
            when(alertRepository.findAllByTenantTenantIdAndStatusOrderByTriggeredAtDesc(tenantId, AlertStatus.OPEN))
                    .thenReturn(List.of());

            alertService.getAlerts(tenantId, AlertStatus.OPEN, null);

            verify(alertRepository).findAllByTenantTenantIdAndStatusOrderByTriggeredAtDesc(tenantId, AlertStatus.OPEN);
        }
    }
}
