package com.vcs.management.license.controller;

import com.vcs.management.common.response.ApiResponse;
import com.vcs.management.license.dto.CreateLicenseRequest;
import com.vcs.management.license.dto.LicenseResponse;
import com.vcs.management.license.dto.UpdateLicenseRequest;
import com.vcs.management.license.service.LicenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/licenses")
public class LicenseController {

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LicenseResponse>> createLicense(
            @Valid @RequestBody CreateLicenseRequest request
    ) {
        LicenseResponse response = licenseService.createLicense(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("License created successfully", response));
    }

    @GetMapping
    public ApiResponse<List<LicenseResponse>> getLicenses() {
        return ApiResponse.success(licenseService.getLicenses());
    }

    @GetMapping("/{licenseId}")
    public ApiResponse<LicenseResponse> getLicense(@PathVariable UUID licenseId) {
        return ApiResponse.success(licenseService.getLicense(licenseId));
    }

    @GetMapping("/tenant/{tenantId}")
    public ApiResponse<List<LicenseResponse>> getLicensesByTenant(@PathVariable UUID tenantId) {
        return ApiResponse.success(licenseService.getLicensesByTenant(tenantId));
    }

    @PutMapping("/{licenseId}")
    public ApiResponse<LicenseResponse> updateLicense(
            @PathVariable UUID licenseId,
            @Valid @RequestBody UpdateLicenseRequest request
    ) {
        return ApiResponse.success("License updated successfully", licenseService.updateLicense(licenseId, request));
    }

    @DeleteMapping("/{licenseId}")
    public ApiResponse<LicenseResponse> disableLicense(@PathVariable UUID licenseId) {
        return ApiResponse.success("License disabled successfully", licenseService.disableLicense(licenseId));
    }

    @GetMapping("/expiring-soon")
    public ApiResponse<List<LicenseResponse>> getExpiringSoonLicenses(
            @RequestParam(defaultValue = "7") Integer days
    ) {
        return ApiResponse.success(licenseService.getExpiringSoonLicenses(days));
    }
}
