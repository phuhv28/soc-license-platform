package com.vcs.management.audit.controller;

import com.vcs.management.audit.dto.AuditLogResponse;
import com.vcs.management.audit.service.AuditLogService;
import com.vcs.management.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<List<AuditLogResponse>> getAuditLogs() {
        return ApiResponse.success(auditLogService.getAuditLogs());
    }
}
