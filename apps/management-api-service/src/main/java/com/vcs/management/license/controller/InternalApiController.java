package com.vcs.management.license.controller;

import com.vcs.management.common.enums.LicenseStatus;
import com.vcs.management.license.entity.License;
import com.vcs.management.license.repository.LicenseRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal")
public class InternalApiController {

    private final LicenseRepository licenseRepository;
    private final StringRedisTemplate redisTemplate;

    public InternalApiController(LicenseRepository licenseRepository, StringRedisTemplate redisTemplate) {
        this.licenseRepository = licenseRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Endpoint for the Go Collector to call when it has a cache miss for a tenant's quota.
     * This acts as a Read-Through cache pattern handler.
     */
    @GetMapping("/quotas/{tenantId}")
    public ResponseEntity<Long> getAndSyncQuota(@PathVariable UUID tenantId) {
        List<License> licenses = licenseRepository.findAllByTenantTenantIdOrderByCreatedAtDesc(tenantId);
        
        long quota = 0;
        for (License l : licenses) {
            if (l.getStatus() == LicenseStatus.ACTIVE) {
                quota = l.getEpsQuota();
                break;
            }
        }

        // Sync back to Redis so subsequent checks are fast
        if (quota > 0) {
            redisTemplate.opsForValue().set("quota:" + tenantId, String.valueOf(quota));
        } else {
            // Even if 0, we can cache it temporarily or just delete the key
            redisTemplate.delete("quota:" + tenantId);
        }

        return ResponseEntity.ok(quota);
    }
}
