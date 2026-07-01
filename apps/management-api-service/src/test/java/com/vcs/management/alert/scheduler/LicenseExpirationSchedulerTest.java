package com.vcs.management.alert.scheduler;

import com.vcs.management.alert.service.AlertService;
import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.license.entity.License;
import com.vcs.management.license.repository.LicenseRepository;
import com.vcs.management.tenant.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LicenseExpirationSchedulerTest {

    @Mock
    private LicenseRepository licenseRepository;

    @Mock
    private AlertService alertService;

    private LicenseExpirationScheduler licenseExpirationScheduler;

    @BeforeEach
    void setUp() {
        licenseExpirationScheduler = new LicenseExpirationScheduler(licenseRepository, alertService);
    }

    @Test
    @DisplayName("Should trigger alerts for expiring licenses")
    void testCheckExpiringLicenses() throws Exception {
        Tenant tenant = new Tenant("Test Tenant");
        UUID tenantId = UUID.randomUUID();
        var fTenantId = Tenant.class.getDeclaredField("tenantId");
        fTenantId.setAccessible(true);
        fTenantId.set(tenant, tenantId);

        License license = new License(tenant, 100, LocalDate.now(), LocalDate.now().plusDays(3));
        
        UUID licenseId = UUID.randomUUID();
        var fLicenseId = License.class.getDeclaredField("licenseId");
        fLicenseId.setAccessible(true);
        fLicenseId.set(license, licenseId);

        when(licenseRepository.findAllByStatusAndEndDateBetweenOrderByEndDateAsc(
                eq(LicenseStatus.ACTIVE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(license));

        licenseExpirationScheduler.checkExpiringLicenses();

        ArgumentCaptor<Long> daysCaptor = ArgumentCaptor.forClass(Long.class);
        verify(alertService).triggerLicenseExpiringAlert(eq(tenantId), eq(licenseId), daysCaptor.capture());
        
        assertEquals(3L, daysCaptor.getValue());
    }

    @Test
    @DisplayName("Should handle exceptions gracefully without stopping scheduler")
    void testCheckExpiringLicenses_ExceptionHandling() throws Exception {
        Tenant tenant = new Tenant("Test Tenant");
        UUID tenantId = UUID.randomUUID();
        var fTenantId = Tenant.class.getDeclaredField("tenantId");
        fTenantId.setAccessible(true);
        fTenantId.set(tenant, tenantId);

        License license1 = new License(tenant, 100, LocalDate.now(), LocalDate.now().plusDays(3));
        License license2 = new License(tenant, 100, LocalDate.now(), LocalDate.now().plusDays(4));

        UUID licenseId1 = UUID.randomUUID();
        var fLicenseId = License.class.getDeclaredField("licenseId");
        fLicenseId.setAccessible(true);
        fLicenseId.set(license1, licenseId1);
        fLicenseId.set(license2, UUID.randomUUID());

        when(licenseRepository.findAllByStatusAndEndDateBetweenOrderByEndDateAsc(
                any(), any(), any()))
                .thenReturn(List.of(license1, license2));

        // Throw exception for first license
        doThrow(new RuntimeException("DB Error")).when(alertService)
                .triggerLicenseExpiringAlert(eq(tenantId), eq(licenseId1), anyLong());

        licenseExpirationScheduler.checkExpiringLicenses();

        // Should still attempt to process second license
        verify(alertService, times(2)).triggerLicenseExpiringAlert(any(), any(), anyLong());
    }
}
