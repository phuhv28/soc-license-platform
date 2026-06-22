package com.vcs.management.report.service;

import com.vcs.management.common.exception.BadRequestException;
import com.vcs.management.common.exception.ResourceNotFoundException;
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
    private static final String CSV_HEADER = "Date,Received,Accepted,Dropped\n";

    private final UsageApiService usageApiService;
    private final TenantRepository tenantRepository;

    public ReportService(UsageApiService usageApiService, TenantRepository tenantRepository) {
        this.usageApiService = usageApiService;
        this.tenantRepository = tenantRepository;
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

        YearMonth yearMonth = parseMonth(month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Don't generate future data beyond today
        LocalDate today = LocalDate.now();
        if (endDate.isAfter(today)) {
            endDate = today;
        }

        StringBuilder csv = new StringBuilder();
        csv.append(CSV_HEADER);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String dayKey = date.format(DAY_KEY_FORMATTER);
            String displayDate = date.format(DISPLAY_DATE_FORMATTER);

            long received = usageApiService.getDailyCounter(tenantId, "received", dayKey);
            long accepted = usageApiService.getDailyCounter(tenantId, "accepted", dayKey);
            long dropped = usageApiService.getDailyCounter(tenantId, "dropped", dayKey);

            csv.append(displayDate).append(',')
               .append(received).append(',')
               .append(accepted).append(',')
               .append(dropped).append('\n');
        }

        log.info("Generated CSV report for tenant {} ({}), month {}",
                tenant.getName(), tenantId, month);

        return csv.toString();
    }

    /**
     * Generate a filename for the CSV download.
     */
    public String generateFilename(UUID tenantId, String month) {
        return String.format("usage_%s_%s.csv", tenantId, month);
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid month format. Expected yyyy-MM, got: " + month);
        }
    }
}
