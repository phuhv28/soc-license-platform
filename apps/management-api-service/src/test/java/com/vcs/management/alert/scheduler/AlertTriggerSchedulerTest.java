package com.vcs.management.alert.scheduler;

import com.vcs.management.alert.service.AlertService;
import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.common.enums.TenantStatus;
import com.vcs.management.license.repository.LicenseRepository;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import com.vcs.management.usage.service.UsageApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlertTriggerSchedulerTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private LicenseRepository licenseRepository;
    @Mock
    private UsageApiService usageApiService;
    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertTriggerScheduler scheduler;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant("test");
        ReflectionTestUtils.setField(tenant, "tenantId", UUID.randomUUID());
        tenant.setStatus(TenantStatus.ACTIVE);
    }

    @Test
    void checkUsageAlerts_ActiveLicense_Usage70_TriggersAlert() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(licenseRepository.existsByTenantTenantIdAndStatus(tenant.getTenantId(), LicenseStatus.ACTIVE))
                .thenReturn(true);
        when(usageApiService.getUsagePercent(tenant.getTenantId())).thenReturn(75);

        scheduler.checkUsageAlerts();

        verify(alertService).triggerUsageAlert(tenant.getTenantId(), 75);
    }

    @Test
    void checkUsageAlerts_NoActiveLicense_DoesNotTriggerAlert() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(licenseRepository.existsByTenantTenantIdAndStatus(tenant.getTenantId(), LicenseStatus.ACTIVE))
                .thenReturn(false);

        scheduler.checkUsageAlerts();

        verify(usageApiService, never()).getUsagePercent(any());
        verify(alertService, never()).triggerUsageAlert(any(), anyInt());
    }

    @Test
    void checkUsageAlerts_UsageBelow70_DoesNotTriggerAlert() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(licenseRepository.existsByTenantTenantIdAndStatus(tenant.getTenantId(), LicenseStatus.ACTIVE))
                .thenReturn(true);
        when(usageApiService.getUsagePercent(tenant.getTenantId())).thenReturn(60);

        scheduler.checkUsageAlerts();

        verify(alertService, never()).triggerUsageAlert(any(), anyInt());
    }
}
