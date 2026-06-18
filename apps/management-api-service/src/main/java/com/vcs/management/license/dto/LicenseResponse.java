package com.vcs.management.license.dto;

import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.license.entity.License;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LicenseResponse(
        UUID licenseId,
        UUID tenantId,
        Integer epsQuota,
        LocalDate startDate,
        LocalDate endDate,
        LicenseStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static LicenseResponse from(License license) {
        return new LicenseResponse(
                license.getLicenseId(),
                license.getTenant().getTenantId(),
                license.getEpsQuota(),
                license.getStartDate(),
                license.getEndDate(),
                license.getStatus(),
                license.getCreatedAt(),
                license.getUpdatedAt()
        );
    }
}
