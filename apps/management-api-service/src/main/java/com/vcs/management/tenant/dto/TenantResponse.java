package com.vcs.management.tenant.dto;

import com.vcs.management.common.enums.TenantStatus;
import com.vcs.management.tenant.entity.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID tenantId,
        String name,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getTenantId(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
