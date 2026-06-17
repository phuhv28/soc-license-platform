package com.vcs.management.health;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final StringRedisTemplate redisTemplate;

    public HealthController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        String redisStatus = "UNKNOWN";

        try {
            redisStatus = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
        } catch (Exception ex) {
            redisStatus = "DOWN";
        }

        return Map.of(
                "service", "management-api-service",
                "status", "ok",
                "redis", redisStatus,
                "timestamp", Instant.now().toString()
        );
    }
}