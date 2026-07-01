package com.vcs.management.report.service;

import com.vcs.management.common.exception.BadRequestException;
import com.vcs.management.common.exception.ResourceNotFoundException;
import com.vcs.management.license.entity.License;
import com.vcs.management.license.repository.LicenseRepository;
import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import com.vcs.management.usage.service.UsageApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Service for generating usage reports as CSV.
 * Reads daily counters from Redis and formats as CSV data.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DAY_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String CSV_DATA_HEADER = "Date,Received,Accepted,Dropped,Overflow\n";

    private final UsageApiService usageApiService;
    private final TenantRepository tenantRepository;
    private final LicenseRepository licenseRepository;

    public ReportService(UsageApiService usageApiService, TenantRepository tenantRepository, LicenseRepository licenseRepository) {
        this.usageApiService = usageApiService;
        this.tenantRepository = tenantRepository;
        this.licenseRepository = licenseRepository;
    }

    /**
     * Generate CSV content for a tenant's monthly usage.
     *
     * @param tenantId the tenant UUID
     * @param month    month string in format yyyy-MM (e.g. "2026-06")
     * @return CSV content as string
     */
    public String generateMonthlyCsv(UUID tenantId, String month) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Integer epsQuota = 0;
        java.util.List<License> licenses = licenseRepository.findAllByTenantTenantIdOrderByCreatedAtDesc(tenantId);
        for (License l : licenses) {
            if (l.getStatus() == LicenseStatus.ACTIVE) {
                epsQuota = l.getEpsQuota();
                break;
            }
        }
        
        long dailyBaseCapacity = epsQuota * 86400L;

        YearMonth yearMonth = parseMonth(month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        StringBuilder csv = new StringBuilder();

        // Metadata header for context
        csv.append("# Tenant: ").append(tenant.getName()).append('\n');
        csv.append("# Tenant ID: ").append(tenantId).append('\n');
        csv.append("# Period: ").append(month).append('\n');
        csv.append("# EPS Quota: ").append(epsQuota).append('\n');
        csv.append("# Generated: ").append(java.time.Instant.now()).append('\n');
        csv.append(CSV_DATA_HEADER);

        long totalReceived = 0, totalAccepted = 0, totalDropped = 0, totalOverflow = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String dayKey = date.format(DAY_KEY_FORMATTER);
            String displayDate = date.format(DISPLAY_DATE_FORMATTER);

            long received = usageApiService.getDailyCounter(tenantId, "received", dayKey);
            long accepted = usageApiService.getDailyCounter(tenantId, "accepted", dayKey);
            long dropped = usageApiService.getDailyCounter(tenantId, "dropped", dayKey);
            
            long overflow = 0;
            if (accepted > dailyBaseCapacity) {
                overflow = accepted - dailyBaseCapacity;
            }

            totalReceived += received;
            totalAccepted += accepted;
            totalDropped += dropped;
            totalOverflow += overflow;

            csv.append(displayDate).append(',')
               .append(received).append(',')
               .append(accepted).append(',')
               .append(dropped).append(',')
               .append(overflow).append('\n');
        }

        // Summary row
        csv.append("TOTAL,")
           .append(totalReceived).append(',')
           .append(totalAccepted).append(',')
           .append(totalDropped).append(',')
           .append(totalOverflow).append('\n');

        log.info("Generated CSV report for tenant {} ({}), month {}",
                tenant.getName(), tenantId, month);

        return csv.toString();
    }

    /**
     * Generate a filename for the CSV download.
     */
    public String generateFilename(UUID tenantId, String month) {
        // Include tenant name in filename for easy identification
        String tenantName = tenantRepository.findById(tenantId)
                .map(Tenant::getName)
                .orElse("unknown");
        String safeName = tenantName.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
        return String.format("usage_%s_%s_%s.csv", safeName, tenantId.toString().substring(0, 8), month);
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid month format. Expected yyyy-MM, got: " + month);
        }
    }
}
