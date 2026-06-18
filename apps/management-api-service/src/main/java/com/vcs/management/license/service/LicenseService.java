package com.vcs.management.license.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcs.management.audit.service.AuditLogService;
import com.vcs.management.common.enums.AuditAction;
import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.common.enums.ResourceType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class LicenseService {

    private static final String DEFAULT_ACTOR = "admin";

    private final LicenseRepository licenseRepository;
    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;
    private final RedisQuotaSyncService redisQuotaSyncService;
    private final ObjectMapper objectMapper;

    public LicenseService(
            LicenseRepository licenseRepository,
            TenantRepository tenantRepository,
            AuditLogService auditLogService,
            RedisQuotaSyncService redisQuotaSyncService,
            ObjectMapper objectMapper
    ) {
        this.licenseRepository = licenseRepository;
        this.tenantRepository = tenantRepository;
        this.auditLogService = auditLogService;
        this.redisQuotaSyncService = redisQuotaSyncService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LicenseResponse createLicense(CreateLicenseRequest request) {
        validateCreateRequest(request);
        Tenant tenant = findTenant(request.tenantId());
        validateTenantIsActive(tenant);
        ensureTenantHasNoActiveLicense(request.tenantId());

        License license = new License(tenant, request.epsQuota(), request.startDate(), request.endDate());
        License savedLicense = licenseRepository.saveAndFlush(license);
        LicenseResponse response = LicenseResponse.from(savedLicense);

        auditLogService.writeAuditLog(
                DEFAULT_ACTOR,
                AuditAction.CREATE_LICENSE,
                ResourceType.LICENSE,
                savedLicense.getLicenseId(),
                null,
                toJson(response)
        );

        redisQuotaSyncService.syncQuota(
                savedLicense.getTenant().getTenantId(),
                savedLicense.getEpsQuota()
        );

        return response;
    }

    @Transactional(readOnly = true)
    public List<LicenseResponse> getLicenses() {
        return licenseRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(LicenseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LicenseResponse getLicense(UUID licenseId) {
        return LicenseResponse.from(findLicense(licenseId));
    }

    @Transactional(readOnly = true)
    public List<LicenseResponse> getLicensesByTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found");
        }

        return licenseRepository.findAllByTenantTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(LicenseResponse::from)
                .toList();
    }

    @Transactional
    public LicenseResponse updateLicense(UUID licenseId, UpdateLicenseRequest request) {
        License license = findLicense(licenseId);
        String beforeValue = toJson(LicenseResponse.from(license));
        validateUpdateRequest(request);
        validateCanUseStatus(license, request.status());

        license.setEpsQuota(request.epsQuota());
        license.setStartDate(request.startDate());
        license.setEndDate(request.endDate());
        license.setStatus(request.status());
        License updatedLicense = licenseRepository.saveAndFlush(license);
        LicenseResponse response = LicenseResponse.from(updatedLicense);

        auditLogService.writeAuditLog(
                DEFAULT_ACTOR,
                AuditAction.UPDATE_LICENSE,
                ResourceType.LICENSE,
                updatedLicense.getLicenseId(),
                beforeValue,
                toJson(response)
        );

        syncOrRemoveQuotaInRedis(updatedLicense);

        return response;
    }

    @Transactional
    public LicenseResponse disableLicense(UUID licenseId) {
        License license = findLicense(licenseId);
        String beforeValue = toJson(LicenseResponse.from(license));

        license.setStatus(LicenseStatus.DISABLED);
        License disabledLicense = licenseRepository.saveAndFlush(license);
        LicenseResponse response = LicenseResponse.from(disabledLicense);

        auditLogService.writeAuditLog(
                DEFAULT_ACTOR,
                AuditAction.DISABLE_LICENSE,
                ResourceType.LICENSE,
                disabledLicense.getLicenseId(),
                beforeValue,
                toJson(response)
        );

        redisQuotaSyncService.removeQuota(disabledLicense.getTenant().getTenantId());

        return response;
    }

    @Transactional(readOnly = true)
    public List<LicenseResponse> getExpiringSoonLicenses(Integer days) {
        if (days == null || days < 0) {
            throw new BadRequestException("Days must be greater than or equal to 0");
        }

        LocalDate today = LocalDate.now();
        return licenseRepository.findAllByStatusAndEndDateBetweenOrderByEndDateAsc(
                        LicenseStatus.ACTIVE,
                        today,
                        today.plusDays(days)
                )
                .stream()
                .map(LicenseResponse::from)
                .toList();
    }

    private License findLicense(UUID licenseId) {
        return licenseRepository.findById(licenseId)
                .orElseThrow(() -> new ResourceNotFoundException("License not found"));
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private void validateCreateRequest(CreateLicenseRequest request) {
        validateDateRange(request.startDate(), request.endDate());
    }

    private void validateUpdateRequest(UpdateLicenseRequest request) {
        validateDateRange(request.startDate(), request.endDate());
    }

    private void validateTenantIsActive(Tenant tenant) {
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new BadRequestException("Tenant must be active to use a license");
        }
    }

    private void validateCanUseStatus(License license, LicenseStatus status) {
        if (status != LicenseStatus.ACTIVE) {
            return;
        }

        validateTenantIsActive(license.getTenant());
        ensureTenantHasNoOtherActiveLicense(license.getTenant().getTenantId(), license.getLicenseId());
    }

    private void ensureTenantHasNoActiveLicense(UUID tenantId) {
        if (licenseRepository.existsByTenantTenantIdAndStatus(tenantId, LicenseStatus.ACTIVE)) {
            throw new ConflictException("Tenant already has an active license");
        }
    }

    private void ensureTenantHasNoOtherActiveLicense(UUID tenantId, UUID licenseId) {
        if (licenseRepository.existsByTenantTenantIdAndStatusAndLicenseIdNot(
                tenantId,
                LicenseStatus.ACTIVE,
                licenseId
        )) {
            throw new ConflictException("Tenant already has an active license");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date must be before or equal to end date");
        }
    }

    private void syncOrRemoveQuotaInRedis(License license) {
        if (license.getStatus() == LicenseStatus.ACTIVE) {
            redisQuotaSyncService.syncQuota(
                    license.getTenant().getTenantId(),
                    license.getEpsQuota()
            );
            return;
        }

        redisQuotaSyncService.removeQuota(license.getTenant().getTenantId());
    }

    private String toJson(LicenseResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize license audit snapshot", ex);
        }
    }
}
