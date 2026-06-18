package com.vcs.management.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcs.management.audit.dto.AuditLogResponse;
import com.vcs.management.audit.entity.AuditLog;
import com.vcs.management.audit.repository.AuditLogRepository;
import com.vcs.management.common.enums.AuditAction;
import com.vcs.management.common.enums.ResourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLogResponse writeAuditLog(
            String actor,
            AuditAction action,
            ResourceType resourceType,
            UUID resourceId,
            String beforeValue,
            String afterValue
    ) {
        AuditLog auditLog = new AuditLog(
                actor,
                action,
                resourceType,
                resourceId,
                toJsonNode(beforeValue),
                toJsonNode(afterValue)
        );

        return AuditLogResponse.from(auditLogRepository.save(auditLog));
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    private JsonNode toJsonNode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Audit value must be valid JSON", ex);
        }
    }
}
