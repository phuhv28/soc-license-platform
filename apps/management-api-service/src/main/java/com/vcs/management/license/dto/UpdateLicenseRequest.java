package com.vcs.management.license.dto;

import com.vcs.management.common.enums.LicenseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record UpdateLicenseRequest(
        @NotNull(message = "EPS quota is required")
        @Positive(message = "EPS quota must be greater than 0")
        Integer epsQuota,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @NotNull(message = "License status is required")
        LicenseStatus status
) {
}
