package com.vcs.management.common.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisQuotaSyncServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisQuotaSyncService redisQuotaSyncService;

    @BeforeEach
    void setUp() {
        redisQuotaSyncService = new RedisQuotaSyncService(redisTemplate);
    }

    @Test
    @DisplayName("Should set quota in Redis")
    void testSyncQuota() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UUID tenantId = UUID.randomUUID();
        Integer epsQuota = 5000;

        redisQuotaSyncService.syncQuota(tenantId, epsQuota);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).set("quota:" + tenantId.toString(), "5000");
    }

    @Test
    @DisplayName("Should remove quota from Redis")
    void testRemoveQuota() {
        UUID tenantId = UUID.randomUUID();

        redisQuotaSyncService.removeQuota(tenantId);

        verify(redisTemplate).delete("quota:" + tenantId.toString());
    }
}
