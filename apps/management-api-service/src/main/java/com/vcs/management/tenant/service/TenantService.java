package com.vcs.management.tenant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcs.management.audit.service.AuditLogService;
import com.vcs.management.common.enums.AuditAction;
import com.vcs.management.common.enums.ResourceType;
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

    private static final String DEFAULT_ACTOR = "admin";

    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final KeycloakAdminService keycloakAdminService;

    public TenantService(
            TenantRepository tenantRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            KeycloakAdminService keycloakAdminService
    ) {
        this.tenantRepository = tenantRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.keycloakAdminService = keycloakAdminService;
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        Tenant tenant = new Tenant(normalizeName(request.name()));
        tenant.setNotificationEmail(request.notificationEmail());
        tenant.setWebhookUrl(request.webhookUrl());
        Tenant savedTenant = tenantRepository.saveAndFlush(tenant);
        TenantResponse response = TenantResponse.from(savedTenant);

        // Auto-create Keycloak user for this tenant
        keycloakAdminService.createTenantUser(savedTenant.getName(), savedTenant.getTenantId().toString(), request.username());

        auditLogService.writeAuditLog(
                DEFAULT_ACTOR,
                AuditAction.CREATE_TENANT,
                ResourceType.TENANT,
                savedTenant.getTenantId(),
                null,
                toJson(response)
        );

        return response;
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
        String beforeValue = toJson(TenantResponse.from(tenant));

        tenant.setName(normalizeName(request.name()));
        tenant.setNotificationEmail(request.notificationEmail());
        tenant.setWebhookUrl(request.webhookUrl());
        Tenant updatedTenant = tenantRepository.saveAndFlush(tenant);
        TenantResponse response = TenantResponse.from(updatedTenant);

        auditLogService.writeAuditLog(
                DEFAULT_ACTOR,
                AuditAction.UPDATE_TENANT,
                ResourceType.TENANT,
                updatedTenant.getTenantId(),
                beforeValue,
                toJson(response)
        );

        return response;
    }

    @Transactional
    public TenantResponse disableTenant(UUID tenantId) {
        Tenant tenant = findTenant(tenantId);
        String beforeValue = toJson(TenantResponse.from(tenant));

        tenant.setStatus(TenantStatus.DISABLED);
        Tenant disabledTenant = tenantRepository.saveAndFlush(tenant);
        TenantResponse response = TenantResponse.from(disabledTenant);

        auditLogService.writeAuditLog(
                DEFAULT_ACTOR,
                AuditAction.DISABLE_TENANT,
                ResourceType.TENANT,
                disabledTenant.getTenantId(),
                beforeValue,
                toJson(response)
        );

        return response;
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String toJson(TenantResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize tenant audit snapshot", ex);
        }
    }
}
