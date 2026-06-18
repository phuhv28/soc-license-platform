package com.vcs.management.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RedisQuotaSyncService {

    private static final String QUOTA_KEY_FORMAT = "quota:%s";

    private final StringRedisTemplate redisTemplate;

    public RedisQuotaSyncService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void syncQuota(UUID tenantId, Integer epsQuota) {
        redisTemplate.opsForValue().set(quotaKey(tenantId), String.valueOf(epsQuota));
    }

    public void removeQuota(UUID tenantId) {
        redisTemplate.delete(quotaKey(tenantId));
    }

    private String quotaKey(UUID tenantId) {
        return QUOTA_KEY_FORMAT.formatted(tenantId);
    }
}
