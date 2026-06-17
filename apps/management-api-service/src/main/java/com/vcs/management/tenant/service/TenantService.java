package com.vcs.management.tenant.service;

import com.vcs.management.common.enums.TenantStatus;
import com.vcs.management.common.exception.ResourceNotFoundException;
import com.vcs.management.tenant.dto.CreateTenantRequest;
import com.vcs.management.tenant.dto.TenantResponse;
import com.vcs.management.tenant.dto.UpdateTenantRequest;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        Tenant tenant = new Tenant(normalizeName(request.name()));
        Tenant savedTenant = tenantRepository.save(tenant);

        return TenantResponse.from(savedTenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> getTenants() {
        return tenantRepository.findAll()
                .stream()
                .map(TenantResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID tenantId) {
        return TenantResponse.from(findTenant(tenantId));
    }

    @Transactional
    public TenantResponse updateTenant(UUID tenantId, UpdateTenantRequest request) {
        Tenant tenant = findTenant(tenantId);

        tenant.setName(normalizeName(request.name()));
        Tenant updatedTenant = tenantRepository.save(tenant);

        return TenantResponse.from(updatedTenant);
    }

    @Transactional
    public TenantResponse disableTenant(UUID tenantId) {
        Tenant tenant = findTenant(tenantId);

        tenant.setStatus(TenantStatus.DISABLED);
        Tenant disabledTenant = tenantRepository.save(tenant);

        return TenantResponse.from(disabledTenant);
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private String normalizeName(String name) {
        return name.trim();
    }
}
