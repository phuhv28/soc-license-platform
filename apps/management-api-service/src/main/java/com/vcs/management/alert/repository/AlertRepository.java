package com.vcs.management.alert.repository;

import com.vcs.management.alert.entity.Alert;
import com.vcs.management.common.enums.AlertStatus;
import com.vcs.management.common.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findAllByOrderByTriggeredAtDesc();

    List<Alert> findAllByTenantTenantIdOrderByTriggeredAtDesc(UUID tenantId);

    List<Alert> findAllByStatusOrderByTriggeredAtDesc(AlertStatus status);

    List<Alert> findAllByTenantTenantIdAndStatusOrderByTriggeredAtDesc(UUID tenantId, AlertStatus status);

    List<Alert> findAllByAlertTypeOrderByTriggeredAtDesc(AlertType alertType);

    Optional<Alert> findByTenantTenantIdAndAlertTypeAndStatus(UUID tenantId, AlertType alertType, AlertStatus status);

    boolean existsByTenantTenantIdAndAlertTypeAndStatus(UUID tenantId, AlertType alertType, AlertStatus status);

    List<Alert> findAllByTenantTenantIdAndAlertTypeInAndStatus(UUID tenantId, List<AlertType> alertTypes, AlertStatus status);
}
