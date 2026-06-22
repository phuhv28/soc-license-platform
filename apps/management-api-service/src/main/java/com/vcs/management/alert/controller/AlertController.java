package com.vcs.management.alert.controller;

import com.vcs.management.alert.dto.AlertResponse;
import com.vcs.management.alert.service.AlertService;
import com.vcs.management.common.enums.AlertStatus;
import com.vcs.management.common.enums.AlertType;
import com.vcs.management.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ApiResponse<List<AlertResponse>> getAlerts(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertType alertType
    ) {
        return ApiResponse.success(alertService.getAlerts(tenantId, status, alertType));
    }

    @GetMapping("/{alertId}")
    public ApiResponse<AlertResponse> getAlert(@PathVariable UUID alertId) {
        return ApiResponse.success(alertService.getAlert(alertId));
    }

    @PutMapping("/{alertId}/resolve")
    public ResponseEntity<ApiResponse<AlertResponse>> resolveAlert(@PathVariable UUID alertId) {
        AlertResponse response = alertService.resolveAlert(alertId);
        return ResponseEntity.ok(ApiResponse.success("Alert resolved successfully", response));
    }

    @PutMapping("/{alertId}/ignore")
    public ResponseEntity<ApiResponse<AlertResponse>> ignoreAlert(@PathVariable UUID alertId) {
        AlertResponse response = alertService.ignoreAlert(alertId);
        return ResponseEntity.ok(ApiResponse.success("Alert ignored successfully", response));
    }
}
