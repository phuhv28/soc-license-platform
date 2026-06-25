package com.vcs.management.license.scheduler;

import com.vcs.management.alert.service.AlertService;
import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.license.entity.License;
import com.vcs.management.license.repository.LicenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduler that checks for licenses expiring within 7 days
 * and triggers LICENSE_EXPIRING_SOON alerts.
 *
 * Runs daily at 08:00 AM.
 */
@Component
public class LicenseExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(LicenseExpirationScheduler.class);
    private static final int EXPIRATION_WARNING_DAYS = 7;

    private final LicenseRepository licenseRepository;
    private final AlertService alertService;

    public LicenseExpirationScheduler(LicenseRepository licenseRepository, AlertService alertService) {
        this.licenseRepository = licenseRepository;
        this.alertService = alertService;
    }

    /**
     * Runs every 5 minutes for testing purposes.
     * (Originally: 0 0 8 * * * - every day at 08:00)
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void checkExpiringLicenses() {
        log.info("Running license expiration check...");

        LocalDate today = LocalDate.now();
        LocalDate warningDate = today.plusDays(EXPIRATION_WARNING_DAYS);

        List<License> expiringLicenses = licenseRepository
                .findAllByStatusAndEndDateBetweenOrderByEndDateAsc(
                        LicenseStatus.ACTIVE, today, warningDate);

        for (License license : expiringLicenses) {
            try {
                long daysRemaining = ChronoUnit.DAYS.between(today, license.getEndDate());
                alertService.triggerLicenseExpiringAlert(
                        license.getTenant().getTenantId(),
                        license.getLicenseId(),
                        daysRemaining
                );
                
                // Add delay to prevent Mailtrap rate limits (Too many emails per second)
                Thread.sleep(3000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Scheduler interrupted during delay");
            } catch (Exception e) {
                log.error("Error creating expiration alert for license {}: {}",
                        license.getLicenseId(), e.getMessage(), e);
            }
        }

        log.info("License expiration check completed. Found {} expiring licenses", expiringLicenses.size());
    }
}
