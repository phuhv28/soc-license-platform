package com.vcs.management.tenant.dto;

import com.vcs.management.common.enums.TenantStatus;
import com.vcs.management.tenant.entity.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID tenantId,
        String name,
        String notificationEmail,
        String webhookUrl,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getTenantId(),
                tenant.getName(),
                tenant.getNotificationEmail(),
                tenant.getWebhookUrl(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
