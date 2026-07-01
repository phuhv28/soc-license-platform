package com.vcs.management.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcs.management.audit.dto.AuditLogResponse;
import com.vcs.management.audit.entity.AuditLog;
import com.vcs.management.audit.repository.AuditLogRepository;
import com.vcs.management.common.enums.AuditAction;
import com.vcs.management.common.enums.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, objectMapper);
    }

    @Test
    void writeAuditLog_Success() throws JsonProcessingException {
        UUID resourceId = UUID.randomUUID();
        AuditLog savedLog = new AuditLog("admin", AuditAction.CREATE_TENANT, ResourceType.TENANT, resourceId, null, null);
        
        when(objectMapper.readTree(anyString())).thenReturn(null);
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

        AuditLogResponse response = auditLogService.writeAuditLog(
                "admin",
                AuditAction.CREATE_TENANT,
                ResourceType.TENANT,
                resourceId,
                "{}",
                "{}"
        );

        assertNotNull(response);
        assertEquals("admin", response.actor());
        assertEquals(AuditAction.CREATE_TENANT, response.action());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void getAuditLogs_ReturnsList() {
        AuditLog log1 = new AuditLog("admin", AuditAction.CREATE_TENANT, ResourceType.TENANT, UUID.randomUUID(), null, null);
        AuditLog log2 = new AuditLog("admin", AuditAction.UPDATE_TENANT, ResourceType.TENANT, UUID.randomUUID(), null, null);

        when(auditLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(log1, log2));

        List<AuditLogResponse> responses = auditLogService.getAuditLogs();

        assertEquals(2, responses.size());
    }

    @Test
    void writeAuditLog_InvalidJson_ThrowsException() throws JsonProcessingException {
        UUID resourceId = UUID.randomUUID();
        when(objectMapper.readTree(anyString())).thenThrow(JsonProcessingException.class);

        assertThrows(IllegalArgumentException.class, () -> {
            auditLogService.writeAuditLog(
                    "admin",
                    AuditAction.CREATE_TENANT,
                    ResourceType.TENANT,
                    resourceId,
                    "invalid-json",
                    null
            );
        });
    }
}
