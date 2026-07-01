package com.vcs.management.tenant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcs.management.audit.service.AuditLogService;
import com.vcs.management.common.enums.TenantStatus;
import com.vcs.management.common.exception.ResourceNotFoundException;
import com.vcs.management.tenant.dto.CreateTenantRequest;
import com.vcs.management.tenant.dto.TenantResponse;
import com.vcs.management.tenant.dto.UpdateTenantRequest;
import com.vcs.management.tenant.entity.Tenant;
import com.vcs.management.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KeycloakAdminService keycloakAdminService;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, auditLogService, objectMapper, keycloakAdminService);
    }

    @Test
    void createTenant_Success() throws JsonProcessingException {
        CreateTenantRequest request = new CreateTenantRequest("Test Tenant", "test@test.com", "http://webhook", "testuser");
        Tenant savedTenant = new Tenant("Test Tenant");
        ReflectionTestUtils.setField(savedTenant, "tenantId", UUID.randomUUID());
        savedTenant.setNotificationEmail(request.notificationEmail());
        savedTenant.setWebhookUrl(request.webhookUrl());

        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenReturn(savedTenant);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        TenantResponse response = tenantService.createTenant(request);

        assertNotNull(response);
        assertEquals("Test Tenant", response.name());
        verify(keycloakAdminService).createTenantUser(any(), any(), any());
        verify(auditLogService).writeAuditLog(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getTenants_ReturnsList() {
        Tenant tenant1 = new Tenant("Tenant 1");
        ReflectionTestUtils.setField(tenant1, "tenantId", UUID.randomUUID());
        Tenant tenant2 = new Tenant("Tenant 2");
        ReflectionTestUtils.setField(tenant2, "tenantId", UUID.randomUUID());

        when(tenantRepository.findAll()).thenReturn(List.of(tenant1, tenant2));

        List<TenantResponse> responses = tenantService.getTenants();

        assertEquals(2, responses.size());
    }

    @Test
    void getTenant_Success() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant("Tenant 1");
        ReflectionTestUtils.setField(tenant, "tenantId", tenantId);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        TenantResponse response = tenantService.getTenant(tenantId);

        assertEquals("Tenant 1", response.name());
    }

    @Test
    void getTenant_NotFound() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tenantService.getTenant(tenantId));
    }

    @Test
    void updateTenant_Success() throws JsonProcessingException {
        UUID tenantId = UUID.randomUUID();
        Tenant existingTenant = new Tenant("Old Name");
        ReflectionTestUtils.setField(existingTenant, "tenantId", tenantId);

        UpdateTenantRequest request = new UpdateTenantRequest("New Name", "new@test.com", "http://new");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(existingTenant));
        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenReturn(existingTenant);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        TenantResponse response = tenantService.updateTenant(tenantId, request);

        assertEquals("New Name", response.name());
        verify(auditLogService).writeAuditLog(any(), any(), any(), any(), any(), any());
    }

    @Test
    void disableTenant_Success() throws JsonProcessingException {
        UUID tenantId = UUID.randomUUID();
        Tenant existingTenant = new Tenant("Tenant 1");
        ReflectionTestUtils.setField(existingTenant, "tenantId", tenantId);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(existingTenant));
        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenReturn(existingTenant);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        TenantResponse response = tenantService.disableTenant(tenantId);

        assertEquals(TenantStatus.DISABLED, response.status());
        verify(auditLogService).writeAuditLog(any(), any(), any(), any(), any(), any());
    }
}
