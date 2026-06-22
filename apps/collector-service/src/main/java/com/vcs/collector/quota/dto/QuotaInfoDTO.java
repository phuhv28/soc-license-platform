package com.vcs.collector.quota.dto;

/**
 * DTO containing quota information for a tenant
 */
public record QuotaInfoDTO(
        String tenantId,
        long quotaEps,
        boolean active
) {
    /**
     * Create an inactive quota (no active license)
     *
     * @param tenantId the tenant ID
     * @return inactive quota info
     */
    public static QuotaInfoDTO inactive(String tenantId) {
        return new QuotaInfoDTO(tenantId, 0L, false);
    }
}
