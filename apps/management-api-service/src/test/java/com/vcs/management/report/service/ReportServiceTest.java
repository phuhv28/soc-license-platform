package com.vcs.management.report.service;

import com.vcs.management.common.exception.BadRequestException;
import com.vcs.management.common.exception.ResourceNotFoundException;
import com.vcs.management.license.repository.LicenseRepository;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import com.vcs.management.usage.repository.BillingMetricRepository;
import com.vcs.management.usage.entity.BillingMetric;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private BillingMetricRepository billingMetricRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private LicenseRepository licenseRepository;

    private ReportService reportService;
    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(billingMetricRepository, tenantRepository, licenseRepository);
        tenantId = UUID.randomUUID();
        tenant = new Tenant("Test Tenant");
        try {
            var f = Tenant.class.getDeclaredField("tenantId");
            f.setAccessible(true);
            f.set(tenant, tenantId);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    @DisplayName("should generate CSV with header and data rows")
    void shouldGenerateCsv() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(billingMetricRepository.findByTenantIdAndWindowTypeAndWindowKeyStartingWith(eq(tenantId), eq("1d"), anyString()))
            .thenReturn(Collections.emptyList());

        String csv = reportService.generateMonthlyCsv(tenantId, "2026-01");

        assertTrue(csv.contains("Date,Received,Accepted,Dropped,Overflow\n"));
        // 6 header lines + 31 data rows + 1 summary row = 38 lines
        long lineCount = csv.lines().count();
        assertEquals(38, lineCount);
    }

    @Test
    @DisplayName("should throw if tenant not found")
    void shouldThrowIfTenantNotFound() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reportService.generateMonthlyCsv(tenantId, "2026-06"));
    }

    @Test
    @DisplayName("should throw on invalid month format")
    void shouldThrowOnInvalidMonth() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertThrows(BadRequestException.class,
                () -> reportService.generateMonthlyCsv(tenantId, "invalid"));
    }

    @Test
    @DisplayName("should generate correct filename")
    void shouldGenerateFilename() {
        String filename = reportService.generateFilename(tenantId, "2026-06");

        assertTrue(filename.startsWith("usage_"));
        assertTrue(filename.endsWith(".csv"));
        assertTrue(filename.contains("2026-06"));
    }
}
