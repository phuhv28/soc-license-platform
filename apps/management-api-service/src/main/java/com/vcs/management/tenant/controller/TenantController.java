package com.vcs.management.tenant.controller;

import com.vcs.management.common.response.ApiResponse;
import com.vcs.management.tenant.dto.CreateTenantRequest;
import com.vcs.management.tenant.dto.TenantResponse;
import com.vcs.management.tenant.dto.UpdateTenantRequest;
import com.vcs.management.tenant.service.TenantService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantResponse response = tenantService.createTenant(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant created successfully", response));
    }

    @GetMapping
    public ApiResponse<List<TenantResponse>> getTenants() {
        return ApiResponse.success(tenantService.getTenants());
    }

    @GetMapping("/{tenantId}")
    public ApiResponse<TenantResponse> getTenant(@PathVariable UUID tenantId) {
        return ApiResponse.success(tenantService.getTenant(tenantId));
    }

    @PutMapping("/{tenantId}")
    public ApiResponse<TenantResponse> updateTenant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        return ApiResponse.success("Tenant updated successfully", tenantService.updateTenant(tenantId, request));
    }

    @DeleteMapping("/{tenantId}")
    public ApiResponse<TenantResponse> disableTenant(@PathVariable UUID tenantId) {
        return ApiResponse.success("Tenant disabled successfully", tenantService.disableTenant(tenantId));
    }
}
