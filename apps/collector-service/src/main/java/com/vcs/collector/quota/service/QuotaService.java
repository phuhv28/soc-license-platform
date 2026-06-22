package com.vcs.collector.quota.service;

import com.vcs.collector.quota.dto.QuotaInfoDTO;
import com.vcs.collector.quota.exception.QuotaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service to read quota from Redis
 * Redis key format: quota:{tenant_id}
 * Redis value: quota EPS (e.g., "100")
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final String QUOTA_KEY_PREFIX = "quota:";
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Get quota for a tenant from Redis
     *
     * @param tenantId the tenant ID
     * @return quota info containing EPS quota and active status
     */
    public QuotaInfoDTO getQuota(String tenantId) {
        try {
            String quotaKey = QUOTA_KEY_PREFIX + tenantId;
            String quotaValue = redisTemplate.opsForValue().get(quotaKey);

            // If quota not found in Redis, tenant has no active license
            if (quotaValue == null || quotaValue.isBlank()) {
                return QuotaInfoDTO.inactive(tenantId);
            }

            // Parse quota from String to long
            long quotaEps = parseQuota(quotaValue);

            return new QuotaInfoDTO(tenantId, quotaEps, true);
        } catch (Exception e) {
            throw new QuotaException("Failed to fetch quota for tenant: " + tenantId, e);
        }
    }

    /**
     * Check if tenant has active quota
     *
     * @param tenantId the tenant ID
     * @return true if quota is active and > 0
     */
    public boolean hasActiveQuota(String tenantId) {
        QuotaInfoDTO quota = getQuota(tenantId);
        return quota.active() && quota.quotaEps() > 0;
    }

    /**
     * Parse quota string to long value
     *
     * @param quotaValue the quota string value
     * @return parsed quota as long
     * @throws QuotaException if value is not a valid number
     */
    private long parseQuota(String quotaValue) {
        try {
            return Long.parseLong(quotaValue.trim());
        } catch (NumberFormatException e) {
            throw new QuotaException("Invalid quota value: " + quotaValue + ". Must be a valid number", e);
        }
    }
}
