package com.vcs.collector.quota.service;

import com.vcs.collector.quota.dto.QuotaInfoDTO;
import com.vcs.collector.quota.dto.TokenBucketStateDTO;
import com.vcs.collector.quota.exception.TokenBucketException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Token bucket service for rate limiting
 * Single-instance version using Redis for state persistence
 * 
 * Refills tokens based on quota (EPS) and elapsed time
 * Each event consumes 1 token
 */
@Service
@RequiredArgsConstructor
public class TokenBucketService {

    private static final String TOKENS_KEY_TEMPLATE = "bucket:%s:tokens";
    private static final String LAST_REFILL_KEY_TEMPLATE = "bucket:%s:last_refill_epoch_ms";
    private static final long REDIS_TTL_SECONDS = 86400; // 24 hours

    private final RedisTemplate<String, String> redisTemplate;
    private final QuotaService quotaService;

    /**
     * Check if an event can be accepted based on available tokens
     * Refills bucket first, then checks/consumes token
     *
     * @param tenantId the tenant ID
     * @param eventCount number of events to process
     * @return number of events that can be accepted
     */
    public long getAcceptableEventCount(String tenantId, long eventCount) {
        try {
            TokenBucketStateDTO state = getAndRefillBucket(tenantId);

            // If no active quota, reject all
            if (state.quotaEps() <= 0) {
                return 0;
            }

            // Accept up to available tokens
            long accepted = Math.min(eventCount, state.availableTokens());

            // Consume tokens and save state
            TokenBucketStateDTO newState = new TokenBucketStateDTO(
                    tenantId,
                    state.availableTokens() - accepted,
                    state.lastRefillEpochMs(),
                    state.quotaEps()
            );
            saveBucketState(newState);

            return accepted;
        } catch (Exception e) {
            throw new TokenBucketException("Failed to check token availability for tenant: " + tenantId, e);
        }
    }

    /**
     * Get and refill token bucket based on elapsed time
     * Formula: new_tokens = old_tokens + (time_elapsed_sec * quota_eps)
     * Max tokens = quota_eps (capacity)
     *
     * @param tenantId the tenant ID
     * @return current bucket state with refilled tokens
     */
    private TokenBucketStateDTO getAndRefillBucket(String tenantId) {
        // Get quota
        QuotaInfoDTO quota = quotaService.getQuota(tenantId);
        if (!quota.active()) {
            return TokenBucketStateDTO.initial(tenantId, 0);
        }

        // Get current state from Redis
        String tokensKey = String.format(TOKENS_KEY_TEMPLATE, tenantId);
        String lastRefillKey = String.format(LAST_REFILL_KEY_TEMPLATE, tenantId);

        String tokensStr = redisTemplate.opsForValue().get(tokensKey);
        String lastRefillStr = redisTemplate.opsForValue().get(lastRefillKey);

        // If no state exists, initialize
        if (tokensStr == null || lastRefillStr == null) {
            return TokenBucketStateDTO.initial(tenantId, quota.quotaEps());
        }

        long currentTokens = Long.parseLong(tokensStr);
        long lastRefillEpoch = Long.parseLong(lastRefillStr);

        // Calculate refill using millisecond precision to avoid losing tokens
        long nowEpoch = System.currentTimeMillis();
        long elapsedMs = nowEpoch - lastRefillEpoch;

        long refillTokens = (elapsedMs * quota.quotaEps()) / 1000;
        long newTokens = Math.min(currentTokens + refillTokens, quota.quotaEps());

        // Only advance the epoch by the exact amount of time that produced the tokens
        long newRefillEpoch = lastRefillEpoch;
        if (refillTokens > 0) {
            newRefillEpoch = lastRefillEpoch + (refillTokens * 1000 / quota.quotaEps());
        }
        // If bucket is full, we can just fast-forward to now
        if (newTokens == quota.quotaEps()) {
            newRefillEpoch = nowEpoch;
        }

        return new TokenBucketStateDTO(tenantId, newTokens, newRefillEpoch, quota.quotaEps());
    }

    /**
     * Save bucket state to Redis with TTL
     *
     * @param state the token bucket state to save
     */
    private void saveBucketState(TokenBucketStateDTO state) {
        String tokensKey = String.format(TOKENS_KEY_TEMPLATE, state.tenantId());
        String lastRefillKey = String.format(LAST_REFILL_KEY_TEMPLATE, state.tenantId());

        redisTemplate.opsForValue().set(tokensKey, String.valueOf(state.availableTokens()));
        redisTemplate.opsForValue().set(lastRefillKey, String.valueOf(state.lastRefillEpochMs()));

        // Set TTL
        redisTemplate.expire(tokensKey, Duration.ofSeconds(REDIS_TTL_SECONDS));
        redisTemplate.expire(lastRefillKey, Duration.ofSeconds(REDIS_TTL_SECONDS));
    }
}
