package com.vcs.management.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.vcs.management.audit.entity.AuditLog;
import com.vcs.management.common.enums.AuditAction;
import com.vcs.management.common.enums.ResourceType;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID auditLogId,
        String actor,
        AuditAction action,
        ResourceType resourceType,
        UUID resourceId,
        JsonNode beforeValue,
        JsonNode afterValue,
        Instant createdAt
) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getAuditLogId(),
                auditLog.getActor(),
                auditLog.getAction(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                auditLog.getBeforeValue(),
                auditLog.getAfterValue(),
                auditLog.getCreatedAt()
        );
    }
}
