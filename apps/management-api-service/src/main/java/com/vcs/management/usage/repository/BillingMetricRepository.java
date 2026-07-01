package com.vcs.management.usage.repository;

import com.vcs.management.usage.entity.BillingMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingMetricRepository extends JpaRepository<BillingMetric, Long> {

    Optional<BillingMetric> findByTenantIdAndWindowTypeAndWindowKey(UUID tenantId, String windowType, String windowKey);

    java.util.List<BillingMetric> findByTenantIdAndWindowTypeAndWindowKeyStartingWith(UUID tenantId, String windowType, String windowKeyPrefix);

}
