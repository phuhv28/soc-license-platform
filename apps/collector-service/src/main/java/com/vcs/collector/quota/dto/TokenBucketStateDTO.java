package com.vcs.collector.quota.dto;

/**
 * DTO representing token bucket state
 */
public record TokenBucketStateDTO(
        String tenantId,
        long availableTokens,
        long lastRefillEpochMs,
        long quotaEps
) {
    /**
     * Create initial token bucket state
     *
     * @param tenantId the tenant ID
     * @param quotaEps the quota in EPS (tokens per second)
     * @return initial state with full tokens
     */
    public static TokenBucketStateDTO initial(String tenantId, long quotaEps) {
        return new TokenBucketStateDTO(tenantId, quotaEps, System.currentTimeMillis(), quotaEps);
    }
}
