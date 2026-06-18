package com.vcs.management.license.repository;

import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.license.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LicenseRepository extends JpaRepository<License, UUID> {

    boolean existsByTenantTenantIdAndStatus(UUID tenantId, LicenseStatus status);

    boolean existsByTenantTenantIdAndStatusAndLicenseIdNot(
            UUID tenantId,
            LicenseStatus status,
            UUID licenseId
    );

    List<License> findAllByOrderByCreatedAtDesc();

    List<License> findAllByTenantTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<License> findAllByStatusAndEndDateBetweenOrderByEndDateAsc(
            LicenseStatus status,
            LocalDate startDate,
            LocalDate endDate
    );
}
