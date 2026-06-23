package com.vcs.management.license.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vcs.management.audit.service.AuditLogService;
import com.vcs.management.common.enums.AuditAction;
import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.common.enums.TenantStatus;
import com.vcs.management.common.exception.BadRequestException;
import com.vcs.management.common.exception.ConflictException;
import com.vcs.management.common.exception.ResourceNotFoundException;
import com.vcs.management.common.redis.RedisQuotaSyncService;
import com.vcs.management.license.dto.CreateLicenseRequest;
import com.vcs.management.license.dto.LicenseResponse;
import com.vcs.management.license.dto.UpdateLicenseRequest;
import com.vcs.management.license.entity.License;
import com.vcs.management.license.repository.LicenseRepository;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock private LicenseRepository licenseRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private RedisQuotaSyncService redisQuotaSyncService;

    @InjectMocks private LicenseService licenseService;

    private Tenant activeTenant;
    private License activeLicense;

    @BeforeEach
    void setUp() {
        // Replace the objectMapper with a real one (not mocked)
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        licenseService = new LicenseService(
                licenseRepository, tenantRepository, auditLogService,
                redisQuotaSyncService, mapper);

        activeTenant = new Tenant("Test Tenant");
        // Use reflection to set the tenantId
        try {
            var field = Tenant.class.getDeclaredField("tenantId");
            field.setAccessible(true);
            field.set(activeTenant, UUID.randomUUID());
        } catch (Exception e) { throw new RuntimeException(e); }

        activeLicense = new License(activeTenant, 100, LocalDate.now(), LocalDate.now().plusMonths(6));
        try {
            var field = License.class.getDeclaredField("licenseId");
            field.setAccessible(true);
            field.set(activeLicense, UUID.randomUUID());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Nested
    @DisplayName("createLicense")
    class CreateLicenseTests {

        @Test
        @DisplayName("should create license successfully")
        void shouldCreateLicense() {
            var request = new CreateLicenseRequest(
                    activeTenant.getTenantId(), 200,
                    LocalDate.now(), LocalDate.now().plusMonths(6));

            when(tenantRepository.findById(activeTenant.getTenantId())).thenReturn(Optional.of(activeTenant));
            when(licenseRepository.existsByTenantTenantIdAndStatus(any(), eq(LicenseStatus.ACTIVE))).thenReturn(false);
            when(licenseRepository.saveAndFlush(any())).thenAnswer(inv -> {
                License lic = inv.getArgument(0);
                try {
                    var f = License.class.getDeclaredField("licenseId");
                    f.setAccessible(true);
                    f.set(lic, UUID.randomUUID());
                } catch (Exception e) { throw new RuntimeException(e); }
                return lic;
            });

            LicenseResponse response = licenseService.createLicense(request);

            assertNotNull(response);
            assertEquals(200, response.epsQuota());
            assertEquals(activeTenant.getTenantId(), response.tenantId());
            verify(redisQuotaSyncService).syncQuota(activeTenant.getTenantId(), 200);
            verify(auditLogService).writeAuditLog(eq("admin"), eq(AuditAction.CREATE_LICENSE),
                    any(), any(), isNull(), any());
        }

        @Test
        @DisplayName("should fail if tenant not found")
        void shouldFailIfTenantNotFound() {
            UUID fakeId = UUID.randomUUID();
            var request = new CreateLicenseRequest(fakeId, 100, LocalDate.now(), LocalDate.now().plusDays(30));
            when(tenantRepository.findById(fakeId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> licenseService.createLicense(request));
        }

        @Test
        @DisplayName("should fail if tenant already has active license")
        void shouldFailIfActiveLicenseExists() {
            var request = new CreateLicenseRequest(
                    activeTenant.getTenantId(), 100, LocalDate.now(), LocalDate.now().plusDays(30));
            when(tenantRepository.findById(activeTenant.getTenantId())).thenReturn(Optional.of(activeTenant));
            when(licenseRepository.existsByTenantTenantIdAndStatus(activeTenant.getTenantId(), LicenseStatus.ACTIVE))
                    .thenReturn(true);

            assertThrows(ConflictException.class, () -> licenseService.createLicense(request));
        }

        @Test
        @DisplayName("should fail if start date after end date")
        void shouldFailIfInvalidDateRange() {
            var request = new CreateLicenseRequest(
                    activeTenant.getTenantId(), 100,
                    LocalDate.now().plusDays(30), LocalDate.now());

            assertThrows(BadRequestException.class, () -> licenseService.createLicense(request));
        }

        @Test
        @DisplayName("should fail if tenant is disabled")
        void shouldFailIfTenantDisabled() {
            activeTenant.setStatus(TenantStatus.DISABLED);
            var request = new CreateLicenseRequest(
                    activeTenant.getTenantId(), 100, LocalDate.now(), LocalDate.now().plusDays(30));
            when(tenantRepository.findById(activeTenant.getTenantId())).thenReturn(Optional.of(activeTenant));

            assertThrows(BadRequestException.class, () -> licenseService.createLicense(request));
        }
    }

    @Nested
    @DisplayName("getLicenses")
    class GetLicensesTests {

        @Test
        @DisplayName("should return all licenses")
        void shouldReturnAll() {
            when(licenseRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(activeLicense));

            List<LicenseResponse> result = licenseService.getLicenses();

            assertEquals(1, result.size());
            assertEquals(activeLicense.getEpsQuota(), result.get(0).epsQuota());
        }
    }

    @Nested
    @DisplayName("disableLicense")
    class DisableLicenseTests {

        @Test
        @DisplayName("should disable license and remove Redis quota")
        void shouldDisable() {
            when(licenseRepository.findById(activeLicense.getLicenseId())).thenReturn(Optional.of(activeLicense));
            when(licenseRepository.saveAndFlush(any())).thenReturn(activeLicense);

            LicenseResponse response = licenseService.disableLicense(activeLicense.getLicenseId());

            assertEquals(LicenseStatus.DISABLED, activeLicense.getStatus());
            verify(redisQuotaSyncService).removeQuota(activeTenant.getTenantId());
            verify(auditLogService).writeAuditLog(eq("admin"), eq(AuditAction.DISABLE_LICENSE),
                    any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getExpiringSoonLicenses")
    class ExpiringSoonTests {

        @Test
        @DisplayName("should return expiring licenses")
        void shouldReturnExpiring() {
            when(licenseRepository.findAllByStatusAndEndDateBetweenOrderByEndDateAsc(
                    eq(LicenseStatus.ACTIVE), any(), any()))
                    .thenReturn(List.of(activeLicense));

            List<LicenseResponse> result = licenseService.getExpiringSoonLicenses(7);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("should fail if days is negative")
        void shouldFailIfNegativeDays() {
            assertThrows(BadRequestException.class, () -> licenseService.getExpiringSoonLicenses(-1));
        }
    }
}
