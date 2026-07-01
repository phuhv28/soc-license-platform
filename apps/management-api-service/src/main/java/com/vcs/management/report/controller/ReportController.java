package com.vcs.management.report.controller;

import com.vcs.management.report.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Export monthly usage report as CSV file.
     * Includes UTF-8 BOM for proper Excel compatibility.
     *
     * @param tenantId the tenant UUID
     * @param month    month in format yyyy-MM (e.g. "2026-06")
     * @return CSV file download
     */
    @GetMapping("/usage/csv")
    public ResponseEntity<byte[]> exportUsageCsv(
            @RequestParam UUID tenantId,
            @RequestParam String month
    ) {
        String csvContent = reportService.generateMonthlyCsv(tenantId, month);
        String filename = reportService.generateFilename(tenantId, month);

        // Prepend UTF-8 BOM so Excel opens with correct encoding
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, body, 0, bom.length);
        System.arraycopy(csvBytes, 0, body, bom.length, csvBytes.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
